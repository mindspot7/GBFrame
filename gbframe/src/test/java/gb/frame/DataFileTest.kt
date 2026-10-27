package gb.frame

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataFileTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun defaultStorageCreatesListsReadsAndRenamesWithoutRecursiveFileDispatch() {
        val root = DataFile(temporary.root, "data")
        assertTrue(root.mkdirs())
        val nested = root.resolve("states")
        assertTrue(nested.mkdir())
        val state = nested.resolve("state.bin")
        state.writeText("checkpoint")
        assertEquals("checkpoint", state.readText())
        assertEquals(listOf("state.bin"), nested.listFiles()!!.map { it.name })
        assertEquals(listOf("states"), root.list()!!.toList())
        val replacement = nested.resolve("next.bin")
        assertTrue(state.renameTo(replacement))
        assertFalse(state.exists()); assertTrue(replacement.isFile)
        assertTrue(replacement.delete()); assertTrue(nested.delete())
        assertTrue(root.delete())
    }
    @Test fun temporaryWritesRemainInRequestedDirectoryAndPreserveBinaryContents() {
        val file = DataFile.createTempFile("save-", ".tmp", temporary.root)
        val bytes = byteArrayOf(0, 1, -1, 10)
        file.writeBytes(bytes)
        assertArrayEquals(bytes, File(file.path).readBytes())
        assertEquals(temporary.root.path, file.parentFile!!.path)
        assertTrue(file.delete())
    }
}
