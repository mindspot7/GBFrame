package gb.frame

private val discNumberPattern = Regex(
    """(?i)(?:\(\s*|\[\s*)?(?:disc|disk|cd|dvd)\s*0*(\d+)(?:\s*(?:of|/)\s*0*\d+)?(?:\s*\)|\s*\])?""",
)

/** The library shows one entry for a multi-disc title: its first disc. The other files remain available to swap discs. */
internal fun keepFirstDiscOnly(games: List<Game>): List<Game> = games.filter { game ->
    val number = discNumberPattern.find(game.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
    number == null || number == 1
}
