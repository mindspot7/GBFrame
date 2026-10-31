package gb.frame

import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTierTest {
    @Test
    fun lowExplainsEfficiencyClassCores() {
        val info = DeviceTier.Info(DeviceTier.Tier.Low, fastCores = 4, littleOnly = true, gpu = "Mali-G52", gpuMaxScale = 1)

        assertTrue(info.classificationReason.contains("efficiency-class CPU cores"))
        assertTrue(info.classificationReason.contains("no high-performance cores"))
    }

    @Test
    fun midExplainsLimitedFastCores() {
        val info = DeviceTier.Info(DeviceTier.Tier.Mid, fastCores = 2, littleOnly = false, gpu = "Adreno 610", gpuMaxScale = 1)

        assertTrue(info.classificationReason.contains("Only 2 high-performance CPU cores were detected"))
    }

    @Test
    fun midExplainsOlderFastCores() {
        val info = DeviceTier.Info(DeviceTier.Tier.Mid, fastCores = 4, littleOnly = false, gpu = "Adreno 610", gpuMaxScale = 1, cpuLimited = true)

        assertTrue(info.classificationReason.contains("Cortex-A73 class or earlier"))
    }

    @Test
    fun highExplainsFastCpuAndSeparateGraphicsAssessment() {
        val info = DeviceTier.Info(DeviceTier.Tier.High, fastCores = 8, littleOnly = false, gpu = "Adreno 650", gpuMaxScale = 3, cpuLimited = false)

        assertTrue(info.classificationReason.contains("at least three fast CPU cores"))
        assertTrue(info.classificationReason.contains("Adreno 650"))
    }
}
