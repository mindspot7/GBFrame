package gb.frame

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.nio.charset.Charset

/** A safely extracted, single-disc ZIP package. The returned path is relative to the extraction directory. */
internal data class ExtractedZipDisc(val primaryPath: String, val unpackedBytes: Long)

internal class ZipDiscArchiveException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** ZIP game packages are extracted before a core sees them; emulation never reads through a decompressor. */
internal object ZipDiscArchive {
    private const val MAX_ENTRIES = 4096

    fun extract(
        archive: File,
        destination: File,
        playableExtensions: Set<String>,
        maxUnpackedBytes: Long,
    ): ExtractedZipDisc {
        val zip = try {
            ZipFile(archive)
        } catch (failure: Exception) {
            throw ZipDiscArchiveException("The ZIP file is invalid or cannot be opened.", failure)
        }
        zip.use { source ->
            val entries = ArrayList<ZipEntry>()
            val iterator = source.entries()
            while (iterator.hasMoreElements()) {
                if (entries.size >= MAX_ENTRIES) throw ZipDiscArchiveException("The ZIP contains too many files.")
                entries += iterator.nextElement()
            }
            if (entries.isEmpty()) throw ZipDiscArchiveException("The ZIP is empty.")
            val normalized = entries.mapNotNull { entry ->
                if (entry.isDirectory) null else entry to safeRelativePath(entry.name)
            }
            val candidates = normalized.filter { (_, path) ->
                path.substringAfterLast('.', "").lowercase() in playableExtensions
            }
            if (candidates.size != 1) {
                throw ZipDiscArchiveException(
                    if (candidates.isEmpty()) "The ZIP does not contain a supported disc image."
                    else "Each ZIP must contain exactly one disc image."
                )
            }
            var declaredTotal = 0L
            for ((entry, _) in normalized) {
                val size = entry.size
                if (size > 0) {
                    if (size > maxUnpackedBytes - declaredTotal) {
                        throw ZipDiscArchiveException("The ZIP expands beyond the supported size limit.")
                    }
                    declaredTotal += size
                }
            }
            if (declaredTotal > maxUnpackedBytes) {
                throw ZipDiscArchiveException("The ZIP expands beyond the supported size limit.")
            }

            val root = destination.canonicalFile
            if (!root.exists() && !root.mkdirs()) throw ZipDiscArchiveException("Could not create the game cache.")
            val seen = HashSet<String>(normalized.size)
            var total = 0L
            for ((entry, relative) in normalized) {
                if (!seen.add(relative.lowercase())) {
                    throw ZipDiscArchiveException("The ZIP contains duplicate file paths.")
                }
                val output = File(root, relative)
                if (!output.canonicalPath.startsWith(root.path + File.separator)) {
                    throw ZipDiscArchiveException("The ZIP contains an unsafe file path.")
                }
                output.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw ZipDiscArchiveException("Could not create the game cache.") }
                val copied = try {
                    source.getInputStream(entry).use { input ->
                        output.outputStream().buffered().use { stream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytes = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count.toLong() > maxUnpackedBytes - total - bytes) {
                                    throw ZipDiscArchiveException("The ZIP expands beyond the supported size limit.")
                                }
                                stream.write(buffer, 0, count)
                                bytes += count
                            }
                            bytes
                        }
                    }
                } catch (failure: ZipDiscArchiveException) {
                    throw failure
                } catch (failure: Exception) {
                    throw ZipDiscArchiveException("Could not extract the ZIP game files.", failure)
                }
                total += copied
            }
            val primary = candidates.single().second
            val primaryFile = File(root, primary)
            if (!primaryFile.isFile) throw ZipDiscArchiveException("The ZIP disc image could not be extracted.")
            validateDescriptorReferences(root, primaryFile)
            return ExtractedZipDisc(primary, total)
        }
    }
    private fun safeRelativePath(entryName: String): String {
        val name = entryName.replace('\\', '/')
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (name.startsWith('/') || parts.isEmpty() || parts.any { it == ".." || ':' in it }) {
            throw ZipDiscArchiveException("The ZIP contains an unsafe file path.")
        }
        return parts.joinToString(File.separator)
    }

    private fun validateDescriptorReferences(root: File, primary: File) {
        val extension = primary.extension.lowercase()
        if (extension != "cue" && extension != "gdi") return
        if (primary.length() > MAX_DESCRIPTOR_BYTES) throw ZipDiscArchiveException("The disc descriptor is too large.")
        val text = primary.readBytes().toString(Charset.forName("ISO-8859-1"))
        val pattern = if (extension == "cue") {
            Regex("(?im)^\\s*FILE\\s+(?:\"([^\"]+)\"|(\\S+))\\s+\\S+\\s*$")
        } else {
            Regex("(?m)^[ \\t]*\\d+[ \\t]+\\d+[ \\t]+\\d+[ \\t]+\\d+[ \\t]+(?:\"([^\"]+)\"|(\\S+))")
        }
        val references = pattern.findAll(text).map { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }.toList()
        if (references.isEmpty()) throw ZipDiscArchiveException("The disc descriptor has no track files.")
        val rootPath = root.canonicalFile.toPath()
        val parentPath = requireNotNull(primary.parentFile).canonicalFile.toPath()
        for (reference in references) {
            val relative = reference.replace('\\', '/')
            if (relative.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(relative)) {
                throw ZipDiscArchiveException("The disc descriptor contains an unsafe track path.")
            }
            val resolved = parentPath.resolve(relative).normalize()
            if (!resolved.startsWith(rootPath) || !File(resolved.toString()).isFile) {
                throw ZipDiscArchiveException("A track referenced by the disc descriptor is missing or outside the ZIP.")
            }
        }
    }

    private const val MAX_DESCRIPTOR_BYTES = 4L * 1024 * 1024
}
