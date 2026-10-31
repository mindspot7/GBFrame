package gb.frame

private val discNumberPattern = Regex(
    """(?i)(?:\(\s*|\[\s*)?(?:disc|disk|cd|dvd)\s*0*(\d+)(?:\s*(?:of|/)\s*0*\d+)?(?:\s*\)|\s*\])?""",
)

/** Stable title key used to find the other discs belonging to a multi-disc release. */
fun discSetKey(title: String): String = discNumberPattern.replace(title, "")
    .lowercase()
    .filter(Char::isLetterOrDigit)

/** The library shows one entry for a multi-disc title: its first disc. The other files remain available to swap discs. */
internal fun keepFirstDiscOnly(games: List<Game>): List<Game> = games.filter { game ->
    val number = discNumberPattern.find(game.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
    number == null || number == 1
}
