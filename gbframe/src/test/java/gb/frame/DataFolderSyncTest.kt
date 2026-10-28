package gb.frame

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataFolderSyncTest {
    @get:Rule val temp = TemporaryFolder()
    private class Tree(val root: File) : DataTree {
        var failWrites = false
        override fun files() = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toList()
        override fun read(path: String) = File(root, path).inputStream()
        override fun write(path: String, source: File) {
            if (failWrites) error("SD card unavailable")
            File(root, path).apply { parentFile!!.mkdirs(); source.copyTo(this, overwrite = true) }
        }
        fun put(path: String, value: String) = File(root, path).apply { parentFile!!.mkdirs(); writeText(value) }
    }
    private fun fixture(): Triple<File, Tree, DataFolderSync> {
        val local = temp.newFolder()
        return Triple(local, Tree(temp.newFolder()), DataFolderSync(mapOf("saves" to local), temp.newFolder()))
    }
    @Test fun exportsAndRestoresNestedData() {
        val (local, tree, sync) = fixture()
        File(local, "game/slot.bin").apply { parentFile!!.mkdirs(); writeText("save") }
        sync.transfer(tree, mutableMapOf(), pull = false)
        assertEquals("save", tree.read("saves/game/slot.bin").bufferedReader().readText())
        val restored = temp.newFolder()
        DataFolderSync(mapOf("saves" to restored), temp.newFolder()).transfer(tree, mutableMapOf(), pull = true, restoring = true)
        assertEquals("save", File(restored, "game/slot.bin").readText())
    }
    @Test fun localOfflineSaveSurvivesRestartAndWinsOverOlderExternalCopy() {
        val (local, tree, sync) = fixture(); val baseline = mutableMapOf<String, String>()
        val save = File(local, "slot").apply { writeText("old") }
        sync.transfer(tree, baseline, pull = false)
        save.writeText("offline save")
        sync.transfer(tree, baseline, pull = true)
        assertEquals("offline save", save.readText())
        assertEquals("offline save", tree.read("saves/slot").bufferedReader().readText())
    }
    @Test fun failedProviderWriteKeepsLocalDataAndBaselineForRetry() {
        val (local, tree, sync) = fixture(); val baseline = mutableMapOf<String, String>()
        File(local, "slot").writeText("important")
        tree.failWrites = true
        assertTrue(runCatching { sync.transfer(tree, baseline, pull = false) }.isFailure)
        assertEquals("important", File(local, "slot").readText()); assertTrue(baseline.isEmpty())
        tree.failWrites = false; sync.transfer(tree, baseline, pull = false)
        assertEquals(1, baseline.size)
    }
    @Test fun restoresExternalChangesOnlyBeforeCoreInitialization() {
        val (local, tree, sync) = fixture(); val baseline = mutableMapOf<String, String>()
        File(local, "slot").writeText("old"); sync.transfer(tree, baseline, pull = false)
        tree.put("saves/slot", "external")
        sync.transfer(tree, baseline, pull = false)
        assertEquals("old", File(local, "slot").readText())
        sync.transfer(tree, baseline, pull = true)
        assertEquals("external", File(local, "slot").readText())
    }
    @Test fun ignoresUnmappedDataAndRejectsTraversal() {
        val (local, tree, sync) = fixture()
        tree.put("credentials/token", "private"); sync.transfer(tree, mutableMapOf(), pull = true)
        assertTrue(local.listFiles()!!.isEmpty())
        val malicious = object : DataTree {
            override fun files() = listOf("saves/../../escape")
            override fun read(path: String) = error("must reject before read")
            override fun write(path: String, source: File) = error("must reject")
        }
        assertTrue(runCatching { sync.transfer(malicious, mutableMapOf(), pull = true) }.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun explicitFolderRestorePreservesReplacedLocalSaveInRecovery() {
        val local = temp.newFolder(); val recovery = temp.newFolder(); val tree = Tree(temp.newFolder())
        File(local, "slot").writeText("previous"); tree.put("saves/slot", "selected folder")
        DataFolderSync(mapOf("saves" to local), recovery).transfer(tree, mutableMapOf(), pull = true, restoring = true)
        assertEquals("selected folder", File(local, "slot").readText())
        assertEquals("previous", recovery.walkTopDown().first { it.isFile }.readText())
    }
    @Test fun simultaneousEditsPreserveExternalConflict() {
        val local = temp.newFolder(); val recovery = temp.newFolder(); val tree = Tree(temp.newFolder())
        val sync = DataFolderSync(mapOf("saves" to local), recovery); val baseline = mutableMapOf<String, String>()
        File(local, "slot").writeText("base"); sync.transfer(tree, baseline, pull = false)
        File(local, "slot").writeText("local"); tree.put("saves/slot", "external")
        sync.transfer(tree, baseline, pull = true)
        assertEquals("local", File(local, "slot").readText())
        assertTrue(recovery.walkTopDown().filter { it.isFile }.any { it.readText() == "external" })
    }
}
