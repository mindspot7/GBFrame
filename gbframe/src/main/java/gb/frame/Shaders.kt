package gb.frame

/**
 * Picture shaders and colour corrections of the OpenGL ES presenter (`native/gl_renderer`), by the ids it takes
 * (`glr::SetOptions`, `glr::SetColorCorrection`), with the names and order the menus show.
 */
object Shaders {
    const val NONE = 0
    const val SCANLINES = 1
    const val CRT = 2
    const val LCD3X = 3
    // 4 was the dot matrix shader (removed: it looked bad in every game); it shows the plain picture
    const val CRT_ROYALE = 5 // single-pass approximation of RetroArch's crt-royale
    const val GTU = 6        // aliaspider's GTU v0.50 (RGB) in one pass

    private val names = mapOf(NONE to "None", SCANLINES to "Scanlines", CRT to "CRT", LCD3X to "LCD3x",
        CRT_ROYALE to "CRT Royale", GTU to "GTU")

    /** Shader ids in the order the menus offer them. */
    val menuOrder = listOf(NONE, CRT, CRT_ROYALE, GTU, LCD3X, SCANLINES)

    fun name(id: Int): String = names[id] ?: "None"

    /** A saved id that no longer exists (dot matrix, unknown) becomes [NONE]. */
    fun sanitize(id: Int): Int = if (id in names) id else NONE

    /** The next shader in [menuOrder] (in-game menu: each press cycles). */
    fun next(id: Int): Int = menuOrder[(menuOrder.indexOf(id) + 1) % menuOrder.size]

    // Colour corrections (glr::SetColorCorrection), applied to the source colours with any shader
    const val COLOR_NONE = 0
    const val COLOR_BSNES_GAMMA = 1 // bsnes gamma ramp (byuu's SNES colour correction)
}
