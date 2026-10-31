package gb.frame

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable

/** Shared output-scaling filter values used by GBFrame's OpenGL ES presenter. */
object PictureFilter {
    const val NEAREST = 0
    const val BILINEAR = 1
    const val DEFAULT = BILINEAR
    val labels = listOf("Nearest", "Bilinear")

    fun sanitize(value: Int): Int = value.coerceIn(NEAREST, BILINEAR)
}

/** Flycast's in-core PVR texture filter values, distinct from [PictureFilter]. */
object TextureFilter {
    const val NEAREST = 1
    const val BILINEAR = 2
    const val DEFAULT = BILINEAR
    val labels = listOf("Nearest", "Bilinear")

    fun sanitize(value: Int): Int = if (value == NEAREST) NEAREST else BILINEAR
    fun index(value: Int): Int = if (sanitize(value) == NEAREST) 0 else 1
    fun valueAt(index: Int): Int = if (index == 0) NEAREST else BILINEAR
}

@Composable
fun ShaderSettingsRow(value: Int, onChange: (Int) -> Unit) {
    val ids = Shaders.menuOrder
    ChoiceRow("Shader", "Applied by the GBFrame OpenGL ES presenter", ids.map(Shaders::name),
        ids.indexOf(Shaders.sanitize(value)).coerceAtLeast(0)) { onChange(ids[it]) }
}

@Composable
fun ShaderQuickMenuItem(value: Int, id: String = "shader", onChange: (Int) -> Unit) {
    val current = Shaders.sanitize(value)
    QuickMenuItemRow(id = id, icon = Icons.Default.Tune, label = "Shader", value = Shaders.name(current)) {
        onChange(Shaders.next(current))
    }
}

/** Output filter after the emulated frame; independent of the core's texture sampling filter. */
@Composable
fun PictureFilterSettingsRow(value: Int, title: String = "Filter", sub: String? = null, onChange: (Int) -> Unit) {
    ChoiceRow(title, sub, PictureFilter.labels, PictureFilter.sanitize(value)) {
        onChange(PictureFilter.sanitize(it))
    }
}

@Composable
fun PictureFilterQuickMenuItem(value: Int, id: String = "filter", title: String = "Filter", onChange: (Int) -> Unit) {
    val current = PictureFilter.sanitize(value)
    QuickMenuItemRow(id = id, icon = Icons.Default.FilterAlt, label = title, value = PictureFilter.labels[current]) {
        onChange((current + 1) % PictureFilter.labels.size)
    }
}

/** Core texture sampling filter; values are the core's Nearest=1 and Bilinear=2. */
@Composable
fun TextureFilterSettingsRow(value: Int, onChange: (Int) -> Unit) {
    ChoiceRow("Texture filtering", "Sampling used by the emulated graphics core", TextureFilter.labels,
        TextureFilter.index(value)) { onChange(TextureFilter.valueAt(it)) }
}

@Composable
fun TextureFilterQuickMenuItem(value: Int, id: String = "texture-filter", onChange: (Int) -> Unit) {
    val index = TextureFilter.index(value)
    QuickMenuItemRow(id = id, icon = Icons.Default.FilterAlt, label = "Texture filtering", value = TextureFilter.labels[index]) {
        onChange(TextureFilter.valueAt((index + 1) % TextureFilter.labels.size))
    }
}
