package gb.frame

import android.opengl.EGL14
import android.opengl.GLES20
import java.io.File

/**
 * Device class for recommended settings (setup wizard preset, [GameProfiles]), from the CPU cores and the GPU:
 *
 * - **Entry**: only in-order "little" cores (Cortex-A53/A55, e.g. Rockchip RK3566). Every core is slow, but they are
 *   equal, so helper threads (sound, video) each get a core of their own.
 * - **Low**: 2 fast cores or fewer (e.g. Unisoc T618, Snapdragon G1 Gen 2): the second fast core is precious; or big cores
 *   of the Cortex-A73 generation or older (e.g. Snapdragon 662), slower per core than a T618.
 * - **Mid**: 3+ fast cores but a GPU that isn't strong enough for GPU emulation (e.g. Unisoc T820 / Mali-G57).
 * - **High**: 3+ fast cores and a strong GPU (e.g. Snapdragon 865 / Adreno 650 and newer, Dimensity 8300 / Mali-G615 MC6).
 *
 * The GPU name comes from a throwaway OpenGL ES context, so call [detect] once at startup (any thread); the result is
 * cached.
 */
object DeviceTier {
    enum class Tier(val key: String) { Entry("entry"), Low("low"), Mid("mid"), High("high") }

    data class Info(
        val tier: Tier,
        /** Cores within 60% of the fastest one (all cores on chips with equal cores). */
        val fastCores: Int,
        /** True when the fastest core is an in-order little core (Cortex-A53/A55 class). */
        val littleOnly: Boolean,
        /** OpenGL ES renderer string, e.g. "Adreno (TM) 650", "Mali-G57" ("" when unknown). */
        val gpu: String,
        /** Highest internal resolution scale worth suggesting for GPU emulation on this GPU (1 = weak GPU). */
        val gpuMaxScale: Int,
    ) {
        val summary get() = "${tier.name} · $fastCores fast core${if (fastCores == 1) "" else "s"}" + (if (gpu.isNotBlank()) " · $gpu" else "")

        /** A short user-facing explanation of the hardware signals behind this class. */
        val classificationReason: String get() = when (tier) {
            Tier.Entry -> "Only efficiency-class CPU cores were detected (such as Cortex-A53/A55-class cores), with no high-performance cores."
            Tier.Low -> if (fastCores <= 2) {
                "Only $fastCores high-performance CPU core${if (fastCores == 1) " was" else "s were"} detected, so CPU headroom for emulation and helper work is limited."
            } else {
                "The fastest CPU cores are from an older high-performance generation (Cortex-A73 class or earlier), with less per-core performance than newer designs."
            }
            Tier.Mid -> "The CPU has $fastCores fast cores, but ${gpu.ifBlank { "the detected GPU" }} does not meet the kit's threshold for heavier GPU emulation."
            Tier.High -> "The CPU has at least three fast cores and ${gpu.ifBlank { "the detected GPU" }} meets the kit's threshold for modern GPU emulation."
        }
    }

    @Volatile private var cached: Info? = null

    fun detect(): Info = cached ?: synchronized(this) { cached ?: compute().also { cached = it } }

    private fun compute(): Info {
        val cores = readCores()
        val gpu = runCatching { glRenderer() }.getOrNull().orEmpty()
        val maxCap = cores.maxOfOrNull { it.score } ?: 0L
        val fast = if (maxCap > 0) cores.count { it.score * 10 >= maxCap * 6 } else 0
        val fastCores = if (fast > 0) fast else Runtime.getRuntime().availableProcessors()
        val top = cores.maxByOrNull { it.score }
        val littleOnly = top?.part?.let { it in LITTLE_PARTS } ?: false
        val oldBig = top?.part?.let { it in OLD_BIG_PARTS } ?: false
        val gpuMaxScale = gpuMaxScale(gpu)
        val tier = when {
            littleOnly -> Tier.Entry
            fastCores <= 2 || oldBig -> Tier.Low
            gpuMaxScale >= 2 -> Tier.High
            else -> Tier.Mid
        }
        return Info(tier, fastCores, littleOnly, gpu, gpuMaxScale)
    }

    private data class Core(val score: Long, val part: Int?)

    /** (implementer << 12 | part) of in-order little cores. */
    private val LITTLE_PARTS = setOf(
        0x41 shl 12 or 0xd03, // Cortex-A53
        0x41 shl 12 or 0xd04, // Cortex-A35
        0x41 shl 12 or 0xd05, // Cortex-A55
        0x41 shl 12 or 0xd46, // Cortex-A510
        0x41 shl 12 or 0xd80, // Cortex-A520
        0x51 shl 12 or 0x801, // Kryo 2xx Silver
        0x51 shl 12 or 0x803, // Kryo 3xx Silver
        0x51 shl 12 or 0x805, // Kryo 4xx Silver
    )

    /** Big cores of the Cortex-A73 generation or older. */
    private val OLD_BIG_PARTS = setOf(
        0x41 shl 12 or 0xd07, // Cortex-A57
        0x41 shl 12 or 0xd08, // Cortex-A72
        0x41 shl 12 or 0xd09, // Cortex-A73
        0x51 shl 12 or 0x800, // Kryo 2xx Gold (A73 class: Snapdragon 662/665/835)
    )

    private fun readLong(path: String) = runCatching { File(path).readText().trim().toLong() }.getOrNull()

    private fun readCores(): List<Core> {
        val n = Runtime.getRuntime().availableProcessors().coerceAtLeast(
            File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }?.size ?: 0)
        val parts = cpuParts()
        return (0 until n).map { i ->
            val cap = readLong("/sys/devices/system/cpu/cpu$i/cpu_capacity") ?: 0L
            val freq = readLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq") ?: 0L
            Core(if (cap > 0) cap * 10_000_000L + freq / 1000 else freq, parts[i] ?: parts.values.firstOrNull())
        }
    }

    /** CPU number -> (implementer << 12 | part) from /proc/cpuinfo. */
    private fun cpuParts(): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        var cpu = -1; var impl = -1
        runCatching {
            File("/proc/cpuinfo").forEachLine { line ->
                val k = line.substringBefore(':').trim(); val v = line.substringAfter(':', "").trim()
                when (k) {
                    "processor" -> { cpu = v.toIntOrNull() ?: -1; impl = -1 }
                    "CPU implementer" -> impl = v.removePrefix("0x").toIntOrNull(16) ?: -1
                    "CPU part" -> v.removePrefix("0x").toIntOrNull(16)?.let { p -> if (cpu >= 0 && impl >= 0) out[cpu] = impl shl 12 or p }
                }
            }
        }
        return out
    }

    /**
     * Highest internal resolution worth suggesting for GPU emulation (compute shaders); 2+ makes a High device.
     * - Adreno: 730+ 4x, 640-729 3x (710-719: 2x), older 1x.
     * - Mali: 5th generation and newer (G610/G615, G710+, G925, G1-*) by core count: MC10+ 3x, MC6+ 2x (unknown count:
     *   2x for G710+), else 1x. Mali-G5x/G7x (G52, G57, G76, G77, G78) were GPU-bound already at 1x on mid-range chips.
     * - Xclipse 3x. Anything else (PowerVR, unknown names) 1x.
     */
    fun gpuMaxScale(gpu: String): Int {
        val g = gpu.lowercase()
        Regex("adreno[^0-9]*(\\d{3})").find(g)?.let { m ->
            val n = m.groupValues[1].toInt()
            return when { n >= 730 -> 4; n in 700..719 -> 2; n >= 640 -> 3; else -> 1 }
        }
        if ("mali" in g) {
            val mc = Regex("\\b(?:mc|mp)(\\d+)").find(g)?.groupValues?.get(1)?.toIntOrNull()
            val n = Regex("mali-g(\\d+)").find(g)?.groupValues?.get(1)?.toIntOrNull()
            val modern = n != null && (n in 610..699 || n >= 710 || n == 1) // "Mali-G1-Ultra" parses as 1
            if (!modern) return 1
            return when {
                mc == null -> if (n!! >= 710 || "ultra" in g || "immortalis" in g) 2 else 1
                mc >= 10 -> 3
                mc >= 6 -> 2
                else -> 1
            }
        }
        if ("xclipse" in g) return 3
        return 1
    }

    /** OpenGL ES renderer string from a 1x1 pbuffer context. */
    private fun glRenderer(): String? {
        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (dpy == EGL14.EGL_NO_DISPLAY) return null
        val ver = IntArray(2)
        // The display stays initialized (no eglTerminate): the app's own renderers use the same EGL display
        if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) return null
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1); val num = IntArray(1)
        val attrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
        if (!EGL14.eglChooseConfig(dpy, attrs, 0, configs, 0, 1, num, 0) || num[0] == 0) return null
        val ctx = EGL14.eglCreateContext(dpy, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        if (ctx == EGL14.EGL_NO_CONTEXT) return null
        val surf = EGL14.eglCreatePbufferSurface(dpy, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        try {
            if (!EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) return null
            return GLES20.glGetString(GLES20.GL_RENDERER)
        } finally {
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, surf)
            EGL14.eglDestroyContext(dpy, ctx)
        }
    }
}
