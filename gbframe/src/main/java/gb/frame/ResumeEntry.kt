package gb.frame

import java.io.File

/**
 * A save state shown inside the All in One library layout: unique [key], [game] it belongs to (e.g. the game's path),
 * title, slot label ("Quick save", "Slot 2"...), time and screenshot.
 */
data class ResumeEntry(
    val key: String,
    val title: String,
    val label: String,
    val time: Long,
    val screenshot: File?,
    val game: String = title,
)
