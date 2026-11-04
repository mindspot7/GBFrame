package gb.frame

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GameDiscArchiveTest {
    @Test fun extractsCueAndReferencedTrackFromZip() = withTempDir { dir ->
        val archive = File(dir, "game.zip")
        writeZip(archive, "disc/game.cue" to "FILE \"track.bin\" BINARY\n", "disc/track.bin" to "track-data")

        val result = extract(archive, "zip", dir)

        assertEquals("disc${File.separator}game.cue", result.primaryPath)
        assertEquals("FILE \"track.bin\" BINARY\n", File(File(dir, "out"), result.primaryPath).readText())
        assertEquals("track-data", File(File(dir, "out"), "disc${File.separator}track.bin").readText())
        assertEquals(34L, result.unpackedBytes)
    }

    @Test fun extractsSevenZipImage() = withTempDir { dir ->
        val archive = File(dir, "game.7z")
        writeSevenZip(archive, "disc/game.iso" to "image-data")

        val result = extract(archive, "7z", dir)

        assertEquals("disc${File.separator}game.iso", result.primaryPath)
        assertEquals("image-data", File(File(dir, "out"), result.primaryPath).readText())
    }

    @Test fun reportsByteProgressForArchiveExtraction() = withTempDir { dir ->
        val archive = File(dir, "progress.zip")
        val image = "x".repeat(128 * 1024)
        writeZip(archive, "game.iso" to image)
        val progress = mutableListOf<Pair<Long, Long?>>()

        GameDiscArchive.extract(archive, "zip", File(dir, "out"), setOf("iso"), 1024 * 1024) { done, total ->
            progress += done to total
        }

        assertTrue(progress.size >= 2)
        assertEquals(0L, progress.first().first)
        assertEquals(image.length.toLong(), progress.last().first)
        assertEquals(image.length.toLong(), progress.last().second)
        assertTrue(progress.zipWithNext().all { (before, after) -> after.first >= before.first })
    }

    @Test fun rejectsArchiveWithMoreThanOneDiscImage() = withTempDir { dir ->
        val archive = File(dir, "multi.zip")
        writeZip(archive, "one.iso" to "1", "two.chd" to "2")

        val error = runCatching { extract(archive, "zip", dir) }.exceptionOrNull()

        assertTrue(error is DiscArchiveException)
        assertTrue(error!!.message!!.contains("exactly one"))
    }

    @Test fun rejectsPathTraversal() = withTempDir { dir ->
        val archive = File(dir, "unsafe.zip")
        writeZip(archive, "../outside.iso" to "bad")

        val error = runCatching { extract(archive, "zip", dir) }.exceptionOrNull()

        assertTrue(error is DiscArchiveException)
        assertFalse(File(dir, "outside.iso").exists())
    }

    @Test fun enforcesExpandedByteLimit() = withTempDir { dir ->
        val archive = File(dir, "large.zip")
        writeZip(archive, "game.iso" to "12345")

        val error = runCatching { GameDiscArchive.extract(archive, "zip", File(dir, "out"), setOf("iso"), 4) }.exceptionOrNull()

        assertTrue(error is DiscArchiveException)
        assertTrue(error!!.message!!.contains("size limit"))
    }

    @Test fun rejectsCueTracksOutsideTheExtractedArchive() = withTempDir { dir ->
        val archive = File(dir, "unsafe-cue.zip")
        writeZip(archive, "disc/game.cue" to "FILE \"../../outside.bin\" BINARY\n", "outside.bin" to "not-a-track")

        val error = runCatching { extract(archive, "zip", dir) }.exceptionOrNull()

        assertTrue(error is DiscArchiveException)
        assertTrue(error!!.message!!.contains("missing or outside"))
    }

    private fun extract(archive: File, extension: String, dir: File) =
        GameDiscArchive.extract(archive, extension, File(dir, "out"), setOf("cue", "chd", "iso", "mds", "ccd"), 1024)

    private fun writeZip(file: File, vararg entries: Pair<String, String>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun writeSevenZip(file: File, vararg entries: Pair<String, String>) {
        SevenZOutputFile(file).use { archive ->
            archive.setContentCompression(SevenZMethod.COPY)
            entries.forEach { (name, contents) ->
                val bytes = contents.toByteArray()
                val entry = SevenZArchiveEntry().apply {
                    this.name = name
                    size = bytes.size.toLong()
                    setHasStream(true)
                }
                archive.putArchiveEntry(entry)
                archive.write(bytes)
                archive.closeArchiveEntry()
            }
        }
    }

    private inline fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("gbframe-disc-archive-test").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}
