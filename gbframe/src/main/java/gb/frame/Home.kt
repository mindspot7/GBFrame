package gb.frame

import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------------------------------
// Home screen layouts ("themes"): the library and the way to the settings, in three arrangements. The app supplies the
// games, the save states and the actions; the top bar, the settings menu overlay and the dialogs stay in the app.
//
//   Showcase (default) — the selected game large on top (details, Continue / Start fresh /
//                        Options), the box art shelf below.
//   Grid               — the box art in a grid, All games / Recent chips.
//   Continue ("All in One") — the latest save state of each game first ("Continue playing"), then all games.
// ---------------------------------------------------------------------------------------------------------------------

enum class HomeStyle(val label: String) { Showcase("Showcase"), Grid("Grid"), Continue("All in One") }

/** Home layout chosen by the user (Settings → Extras → Layout), remembered between sessions. */
object HomeTheme {
    private const val KEY = "gbframe.homeStyle"
    private var prefs: SharedPreferences? = null

    val style = object : MutableState<HomeStyle> {
        private val state = mutableStateOf(HomeStyle.Showcase)
        override var value: HomeStyle
            get() = state.value
            set(v) {
                if (state.value == v) return
                state.value = v
                prefs?.edit()?.putString(KEY, v.name)?.apply()
            }
        override fun component1() = value
        override fun component2(): (HomeStyle) -> Unit = { value = it }
    }

    private const val HELP_KEY = "gbframe.buttonHelp"

    /** Shows the controller button help bar at the bottom of the home screen (when a controller is connected). */
    val buttonHelp = object : MutableState<Boolean> {
        private val state = mutableStateOf(true)
        override var value: Boolean
            get() = state.value
            set(v) {
                if (state.value == v) return
                state.value = v
                prefs?.edit()?.putBoolean(HELP_KEY, v)?.apply()
            }
        override fun component1() = value
        override fun component2(): (Boolean) -> Unit = { value = it }
    }

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        buttonHelp.value = prefs.getBoolean(HELP_KEY, true)
        style.value = HomeStyle.entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: HomeStyle.Showcase
    }
}

/** A game on the home screen. [key] identifies it (e.g. its path); [title] also picks its cover ([Covers]). */
data class HomeGame(val key: String, val title: String, val playSeconds: Long = 0, val hasAchievements: Boolean = false)

/** A settings entry of the main menu ([HomeMenuPanel]). [selected] gets the menu focus (e.g. the page just closed). */
data class HomeMenuItem(val icon: MenuIcon, val label: String, val selected: Boolean = false, val onClick: () -> Unit)

/** What the home screen can do; every layout uses the same actions. */
class HomeActions(
    /** A / tap on a game: start it (the app's auto load applies). */
    val play: (HomeGame) -> Unit,
    /** Start without loading a save state. */
    val fresh: (HomeGame) -> Unit,
    /** Y / long press: game options. */
    val options: (HomeGame) -> Unit,
    /** Resume a save state. */
    val resume: (ResumeEntry) -> Unit,
    /** Open the settings menu (touch: tabs and buttons that lead to it). */
    val openMenu: () -> Unit,
    /** Show recent save states (true) or all games (false). */
    val showRecent: (Boolean) -> Unit,
)

/**
 * Home screen content below the top bar, in the chosen [style]. [states] are the save states (the latest of each game is
 * used); [recent] shows them instead of the games ([ResumeGrid]). [empty] replaces the games while there are none to show
 * (no BIOS, no folder...). [menu] and [menuFocus] are for the Grid theme's icon rail ([onMenuFocus] tells when it has the focus). The first game (or save state in
 * Continue) takes the focus so the controller works at once; Y on a game opens its options. The library opens on the game
 * (or save state) selected last ([LibraryView.lastGame]), not on the first one.
 */
@Composable
fun HomeLibrary(
    style: HomeStyle, games: List<HomeGame>, states: List<ResumeEntry>, recent: Boolean, actions: HomeActions,
    coverVersion: Int = 0, empty: (@Composable () -> Unit)? = null,
    /** Change it to put the focus back on the selected game (e.g. after the menu closed or a touch cleared the focus). */
    refocus: Int = 0,
    modifier: Modifier = Modifier,
) {
    val latest = remember(states) { states.sortedByDescending { it.time }.distinctBy { it.game } }
    val latestByGame = remember(latest) { latest.associateBy { it.game } }
    val first = remember { FocusRequester() }
    // The selection only exists with a physical controller (handhelds' built-in controls count)
    val hasPad = rememberHasGamepad()
    LaunchedEffect(style, recent, games.firstOrNull()?.key, latest.firstOrNull()?.key, empty == null, refocus, hasPad) {
        if (empty == null && hasPad) { delay(60); runCatching { first.requestFocus() } }
    }
    Box(modifier.fillMaxSize()) {
        when (style) {
            HomeStyle.Grid -> GridHome(games, latest, latestByGame, recent, actions, first, coverVersion, empty)
            else -> when {
                empty != null -> empty()
                recent && style == HomeStyle.Showcase -> ShowcaseRecent(games, latest, actions, first)
                recent -> ResumeGrid(latest, actions.resume, first = first)
                style == HomeStyle.Continue -> ContinueHome(games, latest, actions, first, coverVersion)
                else -> ShowcaseHome(games, latestByGame, actions, first, coverVersion)
            }
        }
    }
}

// ---- Top bar ----------------------------------------------------------------------------------------------------------

/**
 * Home top bar: app icon, [label] with a menu arrow, clock and battery. Touch only (the controller opens the menu with X);
 * a tap anywhere on it opens the menu ([HomeMenuPanel]).
 */
@Composable
fun HomeTopBar(icon: Painter, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, menuOpen: Boolean = false) {
    val ctx = LocalContext.current
    val p = palette
    var time by remember { mutableStateOf("") }
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val fmt = android.text.format.DateFormat.getTimeFormat(ctx)
        while (true) {
            time = fmt.format(java.util.Date())
            ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { i ->
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                battery = if (level >= 0 && scale > 0) level * 100 / scale else -1
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
            delay(20_000)
        }
    }
    Surface(
        shape = RoundedCornerShape(28.dp), color = p.panel,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).focusProperties { canFocus = false }.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(if (menuOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Menu", tint = p.highlight)
            Spacer(Modifier.weight(1f))
            Text(time, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = .9f))
            if (battery >= 0) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    when {
                        charging -> Icons.Default.BatteryChargingFull
                        battery > 80 -> Icons.Default.BatteryFull
                        battery > 50 -> Icons.Default.Battery5Bar
                        battery > 20 -> Icons.Default.Battery3Bar
                        else -> Icons.Default.Battery1Bar
                    }, "Battery $battery%", tint = if (battery <= 20 && !charging) Color(0xFFFF6B6B) else Color(0xFF6BE58A),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

// ---- Shared pieces ----------------------------------------------------------------------------------------------------

/** Box art of [title] (null until loaded or when there is none). */
@Composable
fun rememberCover(title: String, version: Int = 0): ImageBitmap? {
    var cover by remember(title, version) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(title, version) { cover = runCatching { Covers.load(title) }.getOrNull() }
    return cover
}

private fun Modifier.optionsKey(onOptions: () -> Unit) = onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonY) { onOptions(); true } else false
}

private val TealSave = Color(0xFF7FD7C4)

/** Box art that grows when focused, with an optional soft reflection below it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverCard(
    title: String, version: Int, width: Dp, height: Dp, modifier: Modifier = Modifier,
    reflection: Boolean = true, focusScale: Float = 1.15f,
    onClick: () -> Unit, onOptions: () -> Unit, onFocused: (ImageBitmap?) -> Unit = {},
) {
    val cover = rememberCover(title, version)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "scale")
    val reflH = if (reflection) height * 0.16f else 0.dp
    // The cover area is tall enough for the tallest covers (ratio 0.66), so no cover is ever cut; covers sit on its bottom
    val boxH = width / 0.66f
    Column(modifier.zIndex(if (focused) 1f else 0f).width(width)
        .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0.5f, boxH / (boxH + reflH)) }) {
        Box(
            Modifier.size(width, boxH)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused(cover) }
                .optionsKey(onOptions)
                // No focus/press highlight box: the enlarged cover is the selection
                .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = onClick, onLongClick = onOptions),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (cover != null) {
                val ratio = (cover.width.toFloat() / cover.height).coerceIn(0.66f, 1.25f)
                Image(cover, title, Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(10.dp)), contentScale = ContentScale.FillBounds)
            } else {
                CoverPlaceholder(title, Modifier.size(width, height))
            }
        }
        if (reflection) {
            if (cover != null) {
                val ratio = (cover.width.toFloat() / cover.height).coerceIn(0.66f, 1.25f)
                // Mirror of the cover's bottom part, fading out, so it always starts right at the cover's bottom edge whatever
                // the cover's proportions. One draw with the cover and the fade combined in a shader: an offscreen layer per
                // visible cover (to fade with DstIn) was redrawn on every frame of a scroll, a large part of the Mali-G52's
                // frame time.
                val bmp = remember(cover) { cover.asAndroidBitmap() }
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(reflH).clipToBounds()) {
                    val w = size.width
                    val ih = w / ratio
                    val image = android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                        .apply {
                            // Flipped around the reflection's top edge: the cover's bottom row is drawn first
                            setLocalMatrix(android.graphics.Matrix().apply {
                                setScale(w / bmp.width, -ih / bmp.height)
                                postTranslate(0f, ih)
                            })
                        }
                    val fade = android.graphics.LinearGradient(0f, 0f, 0f, size.height,
                        android.graphics.Color.argb(82, 255, 255, 255), android.graphics.Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
                    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                        shader = android.graphics.ComposeShader(image, fade, android.graphics.PorterDuff.Mode.DST_IN)
                    }
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, w, size.height, paint)
                }
            } else Spacer(Modifier.height(reflH))
        }
    }
    // Keep the backdrop in sync when the cover finishes loading while selected
    LaunchedEffect(cover) { if (focused) onFocused(cover) }
}

@Composable
private fun CoverPlaceholder(title: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF241F3D))
            .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(10.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Album, null, Modifier.size(40.dp), tint = palette.accent.copy(alpha = .7f))
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

/** Play time, save state and achievements of a game, on one line. */
@Composable
private fun GameInfo(g: HomeGame, hasState: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Schedule, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (g.playSeconds >= 60) formatPlayTime(g.playSeconds) else "Not played", style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = .8f))
        if (hasState) {
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Default.Save, null, tint = TealSave, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
            Text("Save state", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .8f))
        }
        if (g.hasAchievements) {
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Default.EmojiEvents, "Has achievements", tint = palette.highlight, modifier = Modifier.size(17.dp))
        }
    }
}

/** Touch-only pill button (the controller uses A / Y on the games instead). */
@Composable
private fun PillButton(text: String, primary: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.height(42.dp).clip(RoundedCornerShape(50))
            .then(if (primary) Modifier.background(p.accent) else Modifier.border(1.dp, Color.White.copy(alpha = .3f), RoundedCornerShape(50)))
            .focusProperties { canFocus = false }.clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (primary) p.onAccent else Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = if (primary) p.onAccent else Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionLabel(text: String, color: Color = palette.highlight) =
    Text(text, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))

/** One row of box art; the selected cover is kept in the second slot. */
@Composable
private fun Shelf(
    games: List<HomeGame>, version: Int, cardW: Dp, cardH: Dp, firstFocus: FocusRequester?, reflection: Boolean, focusScale: Float,
    actions: HomeActions, startPad: Dp, topRoom: Dp = cardH * (focusScale - 1f) * 0.6f,
    /** Aligns the first and last covers with content [edge] from the sides (the zoomed cover when a controller selects it). */
    edge: Dp? = null, onSelected: (HomeGame, ImageBitmap?) -> Unit,
) {
    // Opens on the game selected last (kept in the second slot like any selection)
    val start = remember(games) { LibraryView.gameIndex(games) }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0))
    var focusIndex by remember { mutableIntStateOf(-1) }
    val pace = remember { RowPace() }
    val gap = 18.dp
    val slotPx = with(LocalDensity.current) { (cardW + gap).roundToPx() }
    LaunchedEffect(focusIndex) { if (focusIndex >= 0) pace.scroll(rowState, focusIndex, -slotPx) }
    LazyRow(
        state = rowState,
        // Side padding leaves room for the enlarged (focused) first and last covers, top padding for its growth
        contentPadding = edge?.let { e ->
            val side = e + if (rememberHasGamepad()) cardW * (focusScale - 1f) / 2 else 0.dp // the zoom grows from the center
            PaddingValues(start = side, end = side, top = topRoom, bottom = 4.dp)
        } ?: PaddingValues(start = startPad + cardW * (focusScale - 1f), end = 20.dp + cardW * (focusScale - 1f), top = topRoom, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().steadyKeys(pace),
    ) {
        items(games.size, key = { games[it].key }) { i ->
            val g = games[i]
            CoverCard(g.title, version, cardW, cardH, if (i == start && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                reflection = reflection, focusScale = focusScale,
                onClick = { LibraryView.lastGame = g.key; actions.play(g) }, onOptions = { actions.options(g) },
                onFocused = { cover -> LibraryView.lastGame = g.key; onSelected(g, cover); pace.focused(); focusIndex = i })
        }
    }
}

/**
 * Holding left/right on a shelf: focus moves at a steady pace (one cover every [STEP_MS]) instead of at the raw key-repeat
 * rate, and the row jumps instead of animating while it moves fast, so the selection never lags behind the key.
 */
private class RowPace {
    var lastMove = 0L
    var lastFocus = 0L
    fun focused() { lastFocus = android.os.SystemClock.uptimeMillis() }
    /**
     * Brings item [index] to [offset] px from the row's start. While the key is held each step glides linearly for exactly
     * one step's time, so consecutive steps join into one continuous movement; a single press eases in and out.
     */
    suspend fun scroll(state: androidx.compose.foundation.lazy.LazyListState, index: Int, offset: Int) {
        val held = lastMove != 0L && android.os.SystemClock.uptimeMillis() - lastMove < 2 * STEP_MS
        val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) { state.scrollToItem(index, offset); return }
        val delta = (item.offset + offset).toFloat() // where the item is now vs. where it should be
        if (delta == 0f) return
        state.animateScrollBy(delta, if (held) androidx.compose.animation.core.tween(STEP_MS.toInt(), easing = androidx.compose.animation.core.LinearEasing)
            else androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    companion object { const val STEP_MS = 120L }
}

private fun Modifier.steadyKeys(pace: RowPace) = onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown || (e.key != Key.DirectionLeft && e.key != Key.DirectionRight)) return@onPreviewKeyEvent false
    val now = android.os.SystemClock.uptimeMillis()
    val repeat = e.nativeKeyEvent.repeatCount > 0
    if (repeat && now - pace.lastMove < RowPace.STEP_MS) return@onPreviewKeyEvent true // held: wait for the next step
    pace.lastMove = if (repeat) now else 0L
    false // the normal focus move happens
}

// ---- A: Showcase ------------------------------------------------------------------------------------------------------

@Composable
private fun ShowcaseHome(games: List<HomeGame>, latestByGame: Map<String, ResumeEntry>, actions: HomeActions, first: FocusRequester, version: Int) {
    val p = palette
    var selected by remember(games) { mutableStateOf(games.getOrNull(LibraryView.gameIndex(games))) }
    val bg = p.background.last()
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(bg.copy(alpha = .35f))) {
        // Short screens (e.g. 640x480 at hdpi): smaller title, no buttons (tap or A plays, long press or Y opens the options)
        val compact = maxHeight < 300.dp
        Column(Modifier.fillMaxSize()) {
            selected?.let { g ->
                val st = latestByGame[g.key]
                Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = if (compact) 10.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                    HeroTitle(g.title, compact)
                    GameInfo(g, st != null)
                    if (!compact) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(if (st != null) "Continue" else "Play", true, Icons.Default.PlayArrow) { if (st != null) actions.resume(st) else actions.play(g) }
                        PillButton("Start fresh", false) { actions.fresh(g) }
                        PillButton("Options", false) { actions.options(g) }
                    }
                }
            }
            // The shelf gets the room left under the hero: covers are sized so that the focused (enlarged) one never
            // reaches the buttons, with the reflection only when there is room for it
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                val s = 1.15f
                val room = maxHeight - 12.dp - 4.dp
                val withRefl = !compact && room / 1.44f >= 110.dp
                val k = if (withRefl) 1.44f else 1.26f // (growth + cover box + reflection) per dp of card height
                val cardH = (room / k).coerceIn(64.dp, 260.dp)
                val boxH = cardH * 0.72f / 0.66f
                Shelf(games, version, cardH * 0.72f, cardH, first, reflection = withRefl, focusScale = s, actions = actions, startPad = 20.dp,
                    topRoom = boxH * (s - 1f) + 12.dp, edge = 24.dp) { g, _ -> selected = g } // lined up with the title
            }
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        }
    }
}

/** Big hero title on one line: a long name scrolls slowly instead of wrapping and pushing the layout down. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroTitle(title: String, compact: Boolean) {
    Text(title, Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, repeatDelayMillis = 2500),
        style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, softWrap = false)
}

@Composable
private fun rememberScreenshot(e: ResumeEntry?): ImageBitmap? {
    var shot by remember(e?.key, e?.time) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(e?.key, e?.time) {
        shot = withContext(Dispatchers.IO) { e?.screenshot?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() } }
    }
    return shot
}

/** Showcase in Recent mode: the same hero, with save-state screenshots in the shelf instead of box art. */
@Composable
private fun ShowcaseRecent(games: List<HomeGame>, latest: List<ResumeEntry>, actions: HomeActions, first: FocusRequester) {
    val p = palette
    val byKey = remember(games) { games.associateBy { it.key } }
    val start = remember(latest) { LibraryView.stateIndex(latest) }
    var selected by remember(latest) { mutableStateOf(latest.getOrNull(start)) }
    val bg = p.background.last()
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(bg.copy(alpha = .35f))) {
        val compact = maxHeight < 300.dp
        // Screenshots are 4:3, wider than box art: a little lower, so the hero keeps its room
        val shotH = (maxHeight * if (compact) 0.42f else 0.36f).coerceIn(80.dp, 220.dp)
        val shotW = shotH * (4f / 3f)
        Column(Modifier.fillMaxSize()) {
            selected?.let { e ->
                val g = byKey[e.game]
                val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(e.time))
                Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = if (compact) 10.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                    HeroTitle(g?.title ?: e.title, compact)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, null, tint = TealSave, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${e.label} · $date", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .8f))
                        if (g != null && g.playSeconds >= 60) {
                            Spacer(Modifier.width(14.dp))
                            Icon(Icons.Default.Schedule, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(formatPlayTime(g.playSeconds), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .8f))
                        }
                    }
                    if (!compact) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Continue", true, Icons.Default.PlayArrow) { actions.resume(e) }
                        if (g != null) {
                            PillButton("Start fresh", false) { actions.fresh(g) }
                            PillButton("Options", false) { actions.options(g) }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            val rowState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0))
            var focusIndex by remember { mutableIntStateOf(-1) }
            val pace = remember { RowPace() }
            val gap = 18.dp
            val slotPx = with(LocalDensity.current) { (shotW + gap).roundToPx() }
            LaunchedEffect(focusIndex) { if (focusIndex >= 0) pace.scroll(rowState, focusIndex, -slotPx) }
            // Lined up with the title (the zoomed screenshot when a controller selects it; the zoom grows from the center)
            val side = 24.dp + if (rememberHasGamepad()) shotW * 0.05f else 0.dp
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(start = side, end = side, top = shotH * 0.1f + 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth().steadyKeys(pace),
            ) {
                items(latest.size, key = { latest[it].key }) { i ->
                    val e = latest[i]
                    ShotCard(e, shotW, shotH, if (i == start) Modifier.focusRequester(first) else Modifier,
                        onClick = { LibraryView.lastState = e.key; actions.resume(e) }, onOptions = { byKey[e.game]?.let(actions.options) }) {
                        LibraryView.lastState = e.key; selected = e; pace.focused(); focusIndex = i
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
        }
    }
}

/** A save-state screenshot in the Showcase shelf: grows when focused, slot label in the corner. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShotCard(e: ResumeEntry, w: Dp, h: Dp, modifier: Modifier, onClick: () -> Unit, onOptions: () -> Unit, onFocused: () -> Unit) {
    val p = palette
    val shot = rememberScreenshot(e)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "scale")
    Box(
        modifier.size(w, h).zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0.5f, 1f) }
            .clip(RoundedCornerShape(12.dp)).background(Color.Black)
            .border(if (focused) 3.dp else 1.dp, if (focused) p.highlight else Color.White.copy(alpha = .18f), RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .optionsKey(onOptions)
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick, onLongClick = onOptions),
    ) {
        shot?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            ?: Icon(Icons.Default.Image, null, Modifier.align(Alignment.Center).size(36.dp), tint = Color.White.copy(alpha = .4f))
        Text(e.label, Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

// ---- B: Grid ----------------------------------------------------------------------------------------------------------

@Composable
private fun GridHome(
    games: List<HomeGame>, latest: List<ResumeEntry>, latestByGame: Map<String, ResumeEntry>, recent: Boolean, actions: HomeActions,
    first: FocusRequester, version: Int, empty: (@Composable () -> Unit)?,
) {
    // All games / Recent is switched from the main menu (or Select)
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    empty != null -> empty()
                    recent -> ResumeGrid(latest, actions.resume, first = first)
                    else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                        // At least 3 columns, so small and square screens don't get two huge covers per row
                        val cols = (maxWidth / 124.dp).toInt().coerceAtLeast(3)
                        val start = remember(games) { LibraryView.gameIndex(games) }
                        LazyVerticalGrid(
                            state = androidx.compose.foundation.lazy.grid.rememberLazyGridState(initialFirstVisibleItemIndex = start),
                            // Room around and between the covers for the enlarged (focused) one, and off the top/bottom bars
                            columns = GridCells.Fixed(cols), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(26.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(games.size, key = { games[it].key }) { i ->
                                val g = games[i]
                                GridCard(g, latestByGame[g.key] != null, version, if (i == start) Modifier.focusRequester(first) else Modifier, actions)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCard(g: HomeGame, hasState: Boolean, version: Int, modifier: Modifier, actions: HomeActions) {
    val cover = rememberCover(g.title, version)
    var focused by remember { mutableStateOf(false) }
    // 3D box (cover + spine). The selected one grows (over its neighbours: zIndex); the grid's spacing leaves room for it
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "scale")
    Column(Modifier.zIndex(if (focused) 1f else 0f).graphicsLayer { scaleX = scale; scaleY = scale }) {
        // Covers keep their proportions (Saturn box art ranges from square to tall), sitting on the cell's bottom
        Box(
            modifier.fillMaxWidth().aspectRatio(0.72f)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) LibraryView.lastGame = g.key }
                .optionsKey { actions.options(g) }
                .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { LibraryView.lastGame = g.key; actions.play(g) }, onLongClick = { actions.options(g) }),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (cover != null) BoxArt3D(cover, Modifier.fillMaxSize())
            else CoverPlaceholder(g.title, Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(10.dp)))
            if (hasState) Text("SAVED", Modifier.align(Alignment.BottomEnd).padding(6.dp)
                .background(Color.Black.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                color = TealSave, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

// ---- D: Continue playing + library ------------------------------------------------------------------------------------

@Composable
private fun ContinueHome(games: List<HomeGame>, latest: List<ResumeEntry>, actions: HomeActions, first: FocusRequester, version: Int) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hasStates = latest.isNotEmpty()
        val thumbW = ((maxHeight * 0.34f) * (4f / 3f)).coerceIn(140.dp, 320.dp)
        val cardH = (maxHeight * if (hasStates) 0.30f else 0.50f).coerceIn(90.dp, 240.dp)
        // Without save states the shelf alone is centered vertically
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = if (hasStates) Arrangement.spacedBy(8.dp) else Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            if (hasStates) {
                SectionLabel("CONTINUE PLAYING")
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(latest.size, key = { latest[it].key }) { i ->
                        val e = latest[i]
                        ResumeCard(e, Modifier.width(thumbW).then(if (i == 0) Modifier.focusRequester(first) else Modifier)) { actions.resume(e) }
                    }
                }
            }
            SectionLabel(if (games.isEmpty()) "ALL GAMES" else "ALL GAMES · ${games.size}", Color.White.copy(alpha = .75f))
            Shelf(games, version, cardH * 0.72f, cardH, if (hasStates) null else first, reflection = false, focusScale = 1.1f,
                actions = actions, startPad = 8.dp) { _, _ -> }
        }
    }
}

// ---- Settings ----------------------------------------------------------------------------------------------------------

/** "Layout" settings block (e.g. in Extras): picks the home screen layout. */
@Composable
fun HomeThemeSettings(onLayoutChanged: () -> Unit = {}) {
    var style by HomeTheme.style
    var help by HomeTheme.buttonHelp
    SettingsGroup("Layout", Icons.Default.Palette) {
        ChoiceRow(
            "Home screen",
            when (style) {
                HomeStyle.Showcase -> "The selected game large on top, box art below"
                HomeStyle.Grid -> "Box art in a grid"
                HomeStyle.Continue -> "Your latest save states first, then all games"
            },
            HomeStyle.entries.map { it.label }, style.ordinal,
        ) { style = HomeStyle.entries[it]; onLayoutChanged() }
        ToggleRow(Icons.Default.SportsEsports, "Button help", "The bar with the controller buttons at the bottom of the home screen", help) { help = it }
    }
}

// ---- Main menu ---------------------------------------------------------------------------------------------------------

/**
 * Main menu of the home screen: a panel that slides in from the left over the library (like the in-game menu), opened by
 * X or a tap on the top bar. From top to bottom: the app and a close button, an optional [header] (e.g. the
 * RetroAchievements user), the library view (All games / Recent, with counts), the settings as icon tiles and Quit.
 * The focus starts on the settings entry marked [HomeMenuItem.selected] (the page just closed), else on the first one.
 */
@Composable
fun HomeMenuPanel(
    open: Boolean, onDismiss: () -> Unit, focus: FocusRequester,
    appIcon: Painter, appName: String,
    recent: Boolean, gamesCount: Int, recentCount: Int, onShowRecent: (Boolean) -> Unit,
    items: List<HomeMenuItem>, onQuit: () -> Unit,
    header: @Composable () -> Unit = {},
    /** Smaller entries shown as compact tiles next to Quit (e.g. About). */
    extras: List<HomeMenuItem> = emptyList(),
    /** Whether something inside the panel has the focus (the app gives it back when a key comes while it hasn't). */
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val p = palette
    Box(modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(open, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        }
        androidx.compose.animation.AnimatedVisibility(
            open,
            enter = androidx.compose.animation.slideInHorizontally { -it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally { -it } + androidx.compose.animation.fadeOut(),
        ) {
            Surface(
                color = lerpOpaque(p.panel), shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, p.border), shadowElevation = 16.dp,
                modifier = Modifier.fillMaxHeight().widthIn(max = 400.dp).fillMaxWidth(0.88f).onFocusChanged { onFocusChanged(it.hasFocus) },
            ) {
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // App and close
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(appIcon, null, Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(appName, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).focusProperties { canFocus = false }.clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Close, "Close menu", tint = Color.White.copy(alpha = .8f)) }
                    }
                    header()
                    val selectedItem = items.indexOfFirst { it.selected }
                    // Focus: the settings page just closed, else the first settings entry
                    val viewFocus = items.isEmpty() && extras.none { it.selected }
                    val itemFocus = if (selectedItem >= 0) selectedItem else if (extras.none { it.selected }) 0 else -1
                    // Library view
                    MenuSection("LIBRARY")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ViewTile(Icons.Default.GridView, "All games", if (gamesCount == 1) "1 game" else "$gamesCount games", !recent,
                            Modifier.weight(1f).then(if (viewFocus && !recent) Modifier.focusRequester(focus) else Modifier)) { onShowRecent(false) }
                        ViewTile(Icons.Default.History, "Recent", if (recentCount == 1) "1 save state" else "$recentCount save states", recent,
                            Modifier.weight(1f).then(if (viewFocus && recent) Modifier.focusRequester(focus) else Modifier)) { onShowRecent(true) }
                    }
                    // Settings
                    MenuSection("SETTINGS")
                    BoxWithConstraints {
                        val cols = if (maxWidth < 280.dp) 2 else 3
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items.withIndex().chunked(cols).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { (i, m) ->
                                        SettingTile(m, Modifier.weight(1f).then(if (i == itemFocus) Modifier.focusRequester(focus) else Modifier))
                                    }
                                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        extras.forEachIndexed { k, m ->
                            MenuRow(m.icon, m.label, Modifier.weight(1f).then(if (m.selected && selectedItem < 0) Modifier.focusRequester(focus) else Modifier), m.onClick)
                        }
                        MenuRow(MenuIcon.Quit, "Quit", Modifier.weight(1f), onQuit)
                    }
                }
            }
        }
    }
}

private fun lerpOpaque(c: Color) = c.copy(alpha = 1f)

@Composable
private fun MenuSection(text: String) =
    Text(text, color = palette.highlight, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp))

/** Focus look shared by the menu tiles: highlight border and a lighter fill. */
@Composable
private fun Modifier.tile(focused: Boolean, selected: Boolean = false): Modifier {
    val p = palette
    val shape = RoundedCornerShape(18.dp)
    return clip(shape)
        .background(when { selected -> p.accent.copy(alpha = if (focused) .42f else .28f); focused -> Color.White.copy(alpha = .16f); else -> Color.White.copy(alpha = .06f) })
        .border(if (focused) 2.dp else 1.dp, if (focused) p.highlight else if (selected) p.accent.copy(alpha = .6f) else Color.White.copy(alpha = .08f), shape)
}

@Composable
private fun ViewTile(icon: ImageVector, label: String, sub: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.height(68.dp).onFocusChanged { focused = it.isFocused }.tile(focused, selected).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) palette.highlight else Color.White.copy(alpha = .85f), modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingTile(m: HomeMenuItem, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.height(88.dp).onFocusChanged { focused = it.isFocused }.tile(focused).clickable(onClick = m.onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        MenuIconView(m.icon, 34.dp)
        Spacer(Modifier.height(6.dp))
        Text(m.label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MenuRow(icon: MenuIcon, label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.height(56.dp).onFocusChanged { focused = it.isFocused }.tile(focused).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuIconView(icon, 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ---- 3D box art -------------------------------------------------------------------------------------------------------

/**
 * A 2D cover drawn as a 3D game box: the front turned slightly to the right in perspective, the spine on the left (made
 * from the cover's left edge, darker), a light gloss and a soft shadow. Sits on the bottom of [modifier]'s area and keeps
 * the cover's proportions. Real 3D box renders aren't needed: any cover works.
 */
@Composable
fun BoxArt3D(cover: ImageBitmap, modifier: Modifier = Modifier) {
    val bmp = remember(cover) { cover.asAndroidBitmap() }
    androidx.compose.foundation.Canvas(modifier) {
        val W = size.width; val H = size.height
        val ratio = (bmp.width.toFloat() / bmp.height).coerceIn(0.6f, 1.3f)
        val depth = W * 0.11f                    // visible spine width
        val faceW = W - depth
        val faceH = (faceW / ratio).coerceAtMost(H * 0.97f)
        val shrink = faceH * 0.055f              // far (right) edge is shorter: perspective
        val top = H - faceH - H * 0.02f
        val bottom = top + faceH
        val nat = drawContext.canvas.nativeCanvas
        // Shadow
        drawOval(Color.Black.copy(alpha = .35f), topLeft = androidx.compose.ui.geometry.Offset(W * 0.02f, bottom - faceH * 0.02f),
            size = androidx.compose.ui.geometry.Size(W * 0.98f, faceH * 0.06f))
        fun quad(src: android.graphics.RectF, dst: FloatArray, paint: android.graphics.Paint) {
            val m = android.graphics.Matrix()
            val s = floatArrayOf(src.left, src.top, src.right, src.top, src.right, src.bottom, src.left, src.bottom)
            m.setPolyToPoly(s, 0, dst, 0, 4)
            nat.save(); nat.concat(m)
            nat.drawBitmap(bmp, android.graphics.Rect(src.left.toInt(), src.top.toInt(), src.right.toInt(), src.bottom.toInt()), src, paint)
            nat.restore()
        }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        // Spine: the cover's left 7%, darkened, slanting away to the left
        val spineSrc = android.graphics.RectF(0f, 0f, bmp.width * 0.07f, bmp.height.toFloat())
        val spinePaint = android.graphics.Paint(paint).apply { colorFilter = android.graphics.LightingColorFilter(0xFF8A8A8A.toInt(), 0) }
        quad(spineSrc, floatArrayOf(0f, top + shrink * 0.8f, depth, top, depth, bottom, 0f, bottom - shrink * 0.8f), spinePaint)
        // Front
        val faceSrc = android.graphics.RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        val face = floatArrayOf(depth, top, W, top + shrink, W, bottom - shrink, depth, bottom)
        quad(faceSrc, face, paint)
        // Gloss on the front and an edge line between spine and front
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(depth, top); lineTo(W, top + shrink); lineTo(W, bottom - shrink); lineTo(depth, bottom); close()
        }
        drawPath(path, Brush.linearGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .12f)),
            start = androidx.compose.ui.geometry.Offset(depth, top), end = androidx.compose.ui.geometry.Offset(W, bottom)))
        drawLine(Color.White.copy(alpha = .35f), androidx.compose.ui.geometry.Offset(depth, top), androidx.compose.ui.geometry.Offset(depth, bottom), strokeWidth = 1.5f)
    }
}
