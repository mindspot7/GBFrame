package gb.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipDiscArchiveTest {
    @Test fun extractsCueAndReferencedTrackTogether() = withTempDir { dir ->
        val archive = File(dir, "game.zip")
        writeZip(archive, "disc/game.cue" to "FILE \"track.bin\" BINARY\n", "disc/track.bin" to "track-data")

        val result = ZipDiscArchive.extract(archive, File(dir, "out"), setOf("cue", "chd"), 1024)

        assertEquals("disc${File.separator}game.cue", result.primaryPath)
        assertEquals("FILE \"track.bin\" BINARY\n", File(File(dir, "out"), result.primaryPath).readText())
        assertEquals("track-data", File(File(dir, "out"), "disc${File.separator}track.bin").readText())
        assertEquals("FILE \"track.bin\" BINARY\n".toByteArray().size + "track-data".toByteArray().size, result.unpackedBytes.toInt())
    }

    @Test fun rejectsArchiveWithMoreThanOneDiscImage() = withTempDir { dir ->
        val archive = File(dir, "multi.zip")
        writeZip(archive, "one.iso" to "1", "two.chd" to "2")

        val error = runCatching { ZipDiscArchive.extract(archive, File(dir, "out"), setOf("iso", "chd"), 1024) }.exceptionOrNull()

        assertTrue(error is ZipDiscArchiveException)
        assertTrue(error!!.message!!.contains("exactly one"))
    }

    @Test fun rejectsPathTraversal() = withTempDir { dir ->
        val archive = File(dir, "unsafe.zip")
        writeZip(archive, "../outside.iso" to "bad")

        val error = runCatching { ZipDiscArchive.extract(archive, File(dir, "out"), setOf("iso"), 1024) }.exceptionOrNull()

        assertTrue(error is ZipDiscArchiveException)
        assertFalse(File(dir, "outside.iso").exists())
    }

    @Test fun enforcesExpandedByteLimit() = withTempDir { dir ->
        val archive = File(dir, "large.zip")
        writeZip(archive, "game.iso" to "12345")

        val error = runCatching { ZipDiscArchive.extract(archive, File(dir, "out"), setOf("iso"), 4) }.exceptionOrNull()

        assertTrue(error is ZipDiscArchiveException)
        assertTrue(error!!.message!!.contains("size limit"))
    }

    @Test fun rejectsCueTracksOutsideTheExtractedArchive() = withTempDir { dir ->
        val archive = File(dir, "unsafe-cue.zip")
        writeZip(archive, "disc/game.cue" to "FILE \"../../outside.bin\" BINARY\n", "outside.bin" to "not-a-track")

        val error = runCatching { ZipDiscArchive.extract(archive, File(dir, "out"), setOf("cue"), 1024) }.exceptionOrNull()

        assertTrue(error is ZipDiscArchiveException)
        assertTrue(error!!.message!!.contains("missing or outside"))
    }

    private fun writeZip(file: File, vararg entries: Pair<String, String>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private inline fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("gbframe-zip-test").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}
