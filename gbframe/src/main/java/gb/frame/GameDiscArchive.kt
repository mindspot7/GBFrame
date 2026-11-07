package gb.frame

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** A safely extracted game image; companion tracks remain beside [primaryPath]. */
internal data class ExtractedDiscArchive(val primaryPath: String, val unpackedBytes: Long)

internal class DiscArchiveException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Disc archives are expanded before the core starts; emulation never reads through an archive decoder. */
internal object GameDiscArchive {
    val extensions = setOf("zip", "7z", "rar")
    private const val MAX_ENTRIES = 4096
    private const val MAX_DESCRIPTOR_BYTES = 4L * 1024 * 1024
    private const val SEVEN_Z_MEMORY_LIMIT_KIB = 256 * 1024

    fun extract(
        archive: File,
        extension: String,
        destination: File,
        playableExtensions: Set<String>,
        maxUnpackedBytes: Long,
        onProgress: (completedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): ExtractedDiscArchive {
        return try {
            when (extension.lowercase()) {
                "zip" -> extractZip(archive, destination, playableExtensions, maxUnpackedBytes, onProgress)
                "7z" -> extractSevenZip(archive, destination, playableExtensions, maxUnpackedBytes, onProgress)
                "rar" -> extractRar(archive, destination, playableExtensions, maxUnpackedBytes, onProgress)
                else -> throw DiscArchiveException("This game archive format is not supported.")
            }
        } catch (failure: DiscArchiveException) {
            destination.deleteRecursively()
            throw failure
        } catch (failure: Exception) {
            destination.deleteRecursively()
            throw DiscArchiveException("Could not read or extract the game archive.", failure)
        }
    }

    private fun extractZip(
        archive: File,
        destination: File,
        playableExtensions: Set<String>,
        maxUnpackedBytes: Long,
        onProgress: (Long, Long?) -> Unit,
    ): ExtractedDiscArchive {
        val zip = try { ZipFile(archive) } catch (failure: Exception) {
            throw DiscArchiveException("The ZIP file is invalid or cannot be opened.", failure)
        }
        zip.use { source ->
            val entries = java.util.Collections.list(source.entries())
            if (entries.size > MAX_ENTRIES) throw DiscArchiveException("The archive contains too many files.")
            val total = declaredTotal(entries.filterNot { it.isDirectory }.map { it.size })
            val writer = DiscExtractionWriter(destination, playableExtensions, maxUnpackedBytes, total, onProgress)
            for (entry in entries) {
                writer.add(entry.name, entry.isDirectory, entry.size) { output ->
                    source.getInputStream(entry).use { it.copyBoundedTo(output) }
                }
            }
            return writer.finish()
        }
    }

    private fun extractSevenZip(
        archive: File,
        destination: File,
        playableExtensions: Set<String>,
        maxUnpackedBytes: Long,
        onProgress: (Long, Long?) -> Unit,
    ): ExtractedDiscArchive {
        SevenZFile.builder().setFile(archive).setMaxMemoryLimitKiB(SEVEN_Z_MEMORY_LIMIT_KIB).get().use { source ->
            val entries = source.entries.toList()
            if (entries.size > MAX_ENTRIES) throw DiscArchiveException("The archive contains too many files.")
            val total = declaredTotal(entries.filter { !it.isDirectory && it.hasStream() && !it.isAntiItem }.map { it.size })
            val writer = DiscExtractionWriter(destination, playableExtensions, maxUnpackedBytes, total, onProgress)
            while (true) {
                val entry = source.nextEntry ?: break
                if (entry.isAntiItem) throw DiscArchiveException("Archives containing delete records are not supported.")
                if (entry.contentMethods?.any { it.method == SevenZMethod.AES256SHA256 } == true) {
                    throw DiscArchiveException("Password-protected 7z files are not supported.")
                }
                writer.add(entry.name, entry.isDirectory, entry.size) { output ->
                    // nextEntry() has positioned the decoder; read its current stream directly so
                    // solid multi-track archives stay on one sequential decoder path.
                    if (entry.hasStream()) source.copyCurrentEntryBoundedTo(output)
                }
            }
            return writer.finish()
        }
    }

    private fun extractRar(
        archive: File,
        destination: File,
        playableExtensions: Set<String>,
        maxUnpackedBytes: Long,
        onProgress: (Long, Long?) -> Unit,
    ): ExtractedDiscArchive {
        Archive(archive).use { source ->
            if (source.mainHeader?.isMultiVolume == true) {
                throw DiscArchiveException("Multi-volume RAR archives are not supported; use one complete archive file.")
            }
            if (source.isPasswordProtected) throw DiscArchiveException("Password-protected RAR files are not supported.")
            val entries = source.fileHeaders
            if (entries.size > MAX_ENTRIES) throw DiscArchiveException("The archive contains too many files.")
            val total = declaredTotal(entries.filterNot { it.isDirectory }.map { it.fullUnpackSize })
            val writer = DiscExtractionWriter(destination, playableExtensions, maxUnpackedBytes, total, onProgress)
            for (entry in entries) {
                if (entry.isSplitBefore || entry.isSplitAfter) {
                    throw DiscArchiveException("Split RAR files are not supported; use one complete archive file.")
                }
                if (entry.isEncrypted) throw DiscArchiveException("Password-protected RAR files are not supported.")
                writer.add(entry.fileName, entry.isDirectory, entry.fullUnpackSize) { output -> source.extractFile(entry, output) }
            }
            return writer.finish()
        }
    }

    private fun declaredTotal(sizes: List<Long>): Long? =
        if (sizes.any { it < 0 }) null else sizes.sum()

    private class DiscExtractionWriter(
        private val destination: File,
        private val playableExtensions: Set<String>,
        private val maxUnpackedBytes: Long,
        private val expectedBytes: Long?,
        private val onProgress: (Long, Long?) -> Unit,
    ) {
        private val filesSeen = HashSet<String>()
        private val candidates = mutableListOf<String>()
        private var totalBytes = 0L
        private var entryCount = 0
        private var copiedBytes = 0L
        private var lastReportedBytes = 0L
        private var lastReportedAtNanos = 0L

        init { onProgress(0, expectedBytes) }

        fun add(name: String, directory: Boolean, declaredSize: Long, extract: (OutputStream) -> Unit) {
            if (++entryCount > MAX_ENTRIES) throw DiscArchiveException("The archive contains too many files.")
            val relative = safeRelativePath(name)
            if (directory) return
            if (!filesSeen.add(relative.lowercase())) throw DiscArchiveException("The archive contains duplicate file paths.")
            if (declaredSize > maxUnpackedBytes - totalBytes) throw DiscArchiveException("The archive expands beyond the supported size limit.")

            val root = destination.canonicalFile
            if (!root.exists() && !root.mkdirs()) throw DiscArchiveException("Could not create the game cache.")
            val output = File(root, relative)
            if (!output.canonicalPath.startsWith(root.path + File.separator)) {
                throw DiscArchiveException("The archive contains an unsafe file path.")
            }
            output.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw DiscArchiveException("Could not create the game cache.") }
            val copied = BoundedOutputStream(BufferedOutputStream(FileOutputStream(output)), maxUnpackedBytes - totalBytes) { bytes ->
                copiedBytes += bytes
                val now = System.nanoTime()
                if (copiedBytes - lastReportedBytes >= 2L * 1024 * 1024 || now - lastReportedAtNanos >= 200_000_000L) {
                    reportProgress(now)
                }
            }
            copied.use(extract)
            if (declaredSize >= 0 && copied.count != declaredSize) {
                throw DiscArchiveException("An archive entry was incomplete or damaged.")
            }
            totalBytes += copied.count
            reportProgress(System.nanoTime(), force = true)
            if (output.extension.lowercase() in playableExtensions) candidates += relative
        }

        fun finish(): ExtractedDiscArchive {
            if (entryCount == 0) throw DiscArchiveException("The archive is empty.")
            if (candidates.size != 1) {
                throw DiscArchiveException(
                    if (candidates.isEmpty()) "The archive does not contain a supported disc image."
                    else "Each archive must contain exactly one disc image."
                )
            }
            val primary = candidates.single()
            val primaryFile = File(destination, primary)
            if (!primaryFile.isFile || primaryFile.length() <= 0L) {
                throw DiscArchiveException("The disc image could not be extracted.")
            }
            validateDescriptorReferences(destination, primaryFile)
            if (expectedBytes != null) onProgress(expectedBytes, expectedBytes)
            else onProgress(copiedBytes, null)
            return ExtractedDiscArchive(primary, totalBytes)
        }

        private fun reportProgress(now: Long, force: Boolean = false) {
            if (force || copiedBytes - lastReportedBytes >= 2L * 1024 * 1024 || now - lastReportedAtNanos >= 200_000_000L) {
                onProgress(copiedBytes, expectedBytes)
                lastReportedBytes = copiedBytes
                lastReportedAtNanos = now
            }
        }
    }

    private class BoundedOutputStream(
        private val target: OutputStream,
        private val limit: Long,
        private val onBytesWritten: (Long) -> Unit,
    ) : OutputStream() {
        var count = 0L
            private set

        override fun write(value: Int) {
            ensureWithinLimit(1)
            target.write(value)
            count++
            onBytesWritten(1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            ensureWithinLimit(length.toLong())
            target.write(buffer, offset, length)
            count += length
            onBytesWritten(length.toLong())
        }

        override fun flush() = target.flush()
        override fun close() = target.close()

        private fun ensureWithinLimit(bytes: Long) {
            if (bytes > limit - count) throw DiscArchiveException("The archive expands beyond the supported size limit.")
        }
    }

    private fun java.io.InputStream.copyBoundedTo(output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    private fun SevenZFile.copyCurrentEntryBoundedTo(output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = read(buffer)
            if (count <= 0) break
            output.write(buffer, 0, count)
        }
    }

    private fun safeRelativePath(entryName: String): String {
        val name = entryName.replace('\\', '/')
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (name.startsWith('/') || parts.isEmpty() || parts.any { it == ".." || ':' in it }) {
            throw DiscArchiveException("The archive contains an unsafe file path.")
        }
        return parts.joinToString(File.separator)
    }

    private fun validateDescriptorReferences(root: File, primary: File) {
        val extension = primary.extension.lowercase()
        if (extension != "cue" && extension != "gdi") return
        if (primary.length() > MAX_DESCRIPTOR_BYTES) throw DiscArchiveException("The disc descriptor is too large.")
        val text = primary.readBytes().toString(Charset.forName("ISO-8859-1"))
        val pattern = if (extension == "cue") {
            Regex("(?im)^\\s*FILE\\s+(?:\"([^\"]+)\"|(\\S+))\\s+\\S+\\s*$")
        } else {
            Regex("(?m)^[ \\t]*\\d+[ \\t]+\\d+[ \\t]+\\d+[ \\t]+\\d+[ \\t]+(?:\"([^\"]+)\"|(\\S+))")
        }
        val references = pattern.findAll(text).map { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }.toList()
        if (references.isEmpty()) throw DiscArchiveException("The disc descriptor has no track files.")
        val rootPath = root.canonicalFile.toPath()
        val parentPath = requireNotNull(primary.parentFile).canonicalFile.toPath()
        for (reference in references) {
            val relative = reference.replace('\\', '/')
            if (relative.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(relative)) {
                throw DiscArchiveException("The disc descriptor contains an unsafe track path.")
            }
            val resolved = parentPath.resolve(relative).normalize()
            if (!resolved.startsWith(rootPath) || !File(resolved.toString()).isFile) {
                throw DiscArchiveException("A track referenced by the disc descriptor is missing or outside the archive.")
            }
        }
    }
}
