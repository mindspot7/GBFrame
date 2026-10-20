package gb.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private val LabelBlue = Color(0xFF3FA9FF)
private val ValueGreen = Color(0xFF4CE07A)
private val ValueWhite = Color(0xFFF2F2F2)

/** Commercial names of common chips (Build.SOC_MODEL gives the part number). */
private val socNames = mapOf(
    "SM8750" to "Snapdragon 8 Elite", "SM8650" to "Snapdragon 8 Gen 3", "SM8550" to "Snapdragon 8 Gen 2",
    "SM8475" to "Snapdragon 8+ Gen 1", "SM8450" to "Snapdragon 8 Gen 1", "SM8350" to "Snapdragon 888",
    "SM8250" to "Snapdragon 865", "SM8150" to "Snapdragon 855", "SM7675" to "Snapdragon 7+ Gen 3",
    "SM7550" to "Snapdragon 7 Gen 3", "SM7475" to "Snapdragon 7+ Gen 2", "SM7450" to "Snapdragon 7 Gen 1",
    "SM7325" to "Snapdragon 778G", "SM7225" to "Snapdragon 750G", "SM6375" to "Snapdragon 695",
    "SM6225" to "Snapdragon 680", "SM6115" to "Snapdragon 662", "SDM845" to "Snapdragon 845",
    "G3X" to "Snapdragon G3x Gen 1",
)

private fun socName(): String {
    val model = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else ""
    val maker = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else ""
    return socNames[model.uppercase()]
        ?: listOf(maker.takeIf { it.isNotBlank() && !it.equals("QTI", true) }, model.takeIf { it.isNotBlank() && it != "unknown" })
            .filterNotNull().joinToString(" ").ifBlank { Build.HARDWARE }
}

/** GPU load in percent from the driver's sysfs node (Adreno/Mali), or null when not readable. */
private fun gpuBusy(): Float? {
    val nodes = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", // Adreno: "22 %"
        "/sys/kernel/gpu/gpu_busy",                    // Mali (many kernels): "22 %"
        "/sys/class/misc/mali0/device/utilization",
        "/sys/devices/platform/mali.0/utilization",
    )
    for (n in nodes) {
        val t = runCatching { File(n).readText().trim() }.getOrNull() ?: continue
        Regex("\\d+(\\.\\d+)?").find(t)?.value?.toFloatOrNull()?.let { return it }
    }
    return null
}

/** Process CPU time (user + system) in clock ticks. */
private fun cpuTicks(): Long? = runCatching {
    val f = File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ')
    f[11].toLong() + f[12].toLong() // utime, stime (fields 14 and 15)
}.getOrNull()

/** What the monitoring overlay shows; each flag is one line (or part of one). */
data class PerfOptions(
    val fps: Boolean = false, val speed: Boolean = false, val cpu: Boolean = false, val gpu: Boolean = false,
    val renderer: Boolean = false, val resolution: Boolean = false, val frameTime: Boolean = false,
) {
    val any get() = fps || speed || cpu || gpu || renderer || resolution || frameTime
    val all get() = fps && speed && cpu && gpu && renderer && resolution && frameTime
}

/**
 * Monitoring overlay (blue labels, white values, green FPS/speed), like common emulator OSDs. The header line with the
 * app name and Android version only shows when every option is on.
 *
 * @param appName name for the header (e.g. "GBSat"); the version comes from the package
 * @param targetFps full speed (60 NTSC / 50 PAL), for the speed percentage
 * @param gpuName GPU name reported by the app's renderer (read once, off the main thread)
 * @param frameMs emulation time per frame
 */
@Composable
fun PerfOverlay(
    options: PerfOptions, appName: String, fps: Float, targetFps: Float, renderer: String, width: Int, height: Int,
    frameMs: Float, gpuName: () -> String, modifier: Modifier = Modifier,
) {
    if (!options.any) return
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty().substringBefore(' ')
    }
    val soc = remember { socName() }
    var gpuLabel by remember { mutableStateOf("") }
    var cpu by remember { mutableStateOf<Float?>(null) }
    var gpu by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(options.cpu, options.gpu) {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        var lastTicks = cpuTicks(); var lastT = System.nanoTime()
        while (true) {
            delay(1000)
            withContext(Dispatchers.IO) {
                if (gpuLabel.isEmpty()) gpuLabel = runCatching(gpuName).getOrDefault("").replace("(TM)", "").replace(Regex("\\s+"), " ").trim()
                val t = cpuTicks(); val now = System.nanoTime()
                if (t != null && lastTicks != null) cpu = ((t - lastTicks!!) / 100f) / ((now - lastT) / 1e9f) / cores * 100f
                lastTicks = t; lastT = now
                if (options.gpu) gpu = gpuBusy()
            }
        }
    }
    val style = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp,
        shadow = Shadow(Color.Black, Offset(1.5f, 1.5f), 3f),
    )
    @Composable
    fun line(vararg parts: Pair<String, Color>) = Text(buildAnnotatedString {
        parts.forEach { (t, c) -> withStyle(SpanStyle(color = c)) { append(t) } }
    }, style = style)

    Column(modifier, horizontalAlignment = Alignment.End) {
        if (options.all) line("$appName-$version" to LabelBlue, "|" to ValueWhite, "Android ${Build.VERSION.RELEASE}" to ValueWhite)
        if (options.fps || options.speed) {
            val parts = mutableListOf<Pair<String, Color>>()
            if (options.fps) { parts += "FPS:" to LabelBlue; parts += "%.1f".format(fps) to ValueGreen }
            if (options.fps && options.speed) parts += "|" to ValueWhite
            if (options.speed) { parts += "Speed:" to LabelBlue; parts += "%.1f%%".format(fps / targetFps * 100f) to ValueGreen }
            line(*parts.toTypedArray())
        }
        if (options.cpu) line("CPU:" to LabelBlue, soc to ValueWhite, "|" to ValueWhite, (cpu?.let { "%.1f%%".format(it) } ?: "--") to ValueWhite)
        if (options.gpu) line("GPU:" to LabelBlue, gpuLabel.ifBlank { "GPU" } to ValueWhite,
            *(gpu?.let { arrayOf("|" to ValueWhite, "%.1f%%".format(it) to ValueWhite) } ?: emptyArray()))
        if (options.renderer) line(renderer to LabelBlue)
        if (options.resolution) line("Res:" to LabelBlue, "${width}x$height" to ValueWhite)
        if (options.frameTime) line("Frame:" to LabelBlue, "%.1fms".format(frameMs) to ValueWhite)
    }
}
