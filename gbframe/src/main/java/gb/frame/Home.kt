package gb.frame

import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
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
// Home screen layouts ("themes"): the library and the way to the settings, in four arrangements. The app supplies the
// games, the save states and the actions; the top bar, the settings menu overlay and the dialogs stay in the app.
//
//   Showcase (default) — the selected game large on top (blurred cover behind it, details, Continue / Start fresh /
//                        Options), the box art shelf below.
//   Grid               — settings as an icon rail on the left, the box art in a grid, All games / Recent chips.
//   List               — Games / Recent / Settings tabs, a list of titles and a preview of the selected game with its
//                        latest save state; the most readable on small screens.
//   Continue           — the latest save state of each game first ("Continue playing"), then all games.
// ---------------------------------------------------------------------------------------------------------------------

enum class HomeStyle(val label: String) { Showcase("Showcase"), Grid("Grid"), List("List"), Continue("Continue") }

/** Home layout chosen by the user (Settings → Extras → Theme), remembered between sessions. */
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

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        style.value = HomeStyle.entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: HomeStyle.Showcase
    }
}

/** A game on the home screen. [key] identifies it (e.g. its path); [title] also picks its cover ([Covers]). */
data class HomeGame(val key: String, val title: String, val playSeconds: Long = 0, val hasAchievements: Boolean = false)

/** A settings entry of the Grid theme's icon rail. [selected] gets the menu focus (e.g. the page just closed). */
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
 * Continue) takes the focus so the controller works at once; Y on a game opens its options.
 */
@Composable
fun HomeLibrary(
    style: HomeStyle, games: List<HomeGame>, states: List<ResumeEntry>, recent: Boolean, actions: HomeActions,
    menu: List<HomeMenuItem>, menuFocus: FocusRequester, coverVersion: Int = 0,
    empty: (@Composable () -> Unit)? = null, onMenuFocus: (Boolean) -> Unit = {}, modifier: Modifier = Modifier,
) {
    val latest = remember(states) { states.sortedByDescending { it.time }.distinctBy { it.game } }
    val latestByGame = remember(latest) { latest.associateBy { it.game } }
    val first = remember { FocusRequester() }
    LaunchedEffect(style, recent, games.firstOrNull()?.key, latest.firstOrNull()?.key, empty == null) {
        if (!recent && empty == null) { delay(60); runCatching { first.requestFocus() } }
    }
    Box(modifier.fillMaxSize()) {
        when (style) {
            HomeStyle.Grid -> GridHome(games, latest, latestByGame, recent, actions, menu, menuFocus, first, coverVersion, empty, onMenuFocus)
            HomeStyle.List -> ListHome(games, latest, latestByGame, recent, actions, first, coverVersion, empty)
            else -> when {
                empty != null -> empty()
                recent -> ResumeGrid(latest, actions.resume)
                style == HomeStyle.Continue -> ContinueHome(games, latest, actions, first, coverVersion)
                else -> ShowcaseHome(games, latestByGame, actions, first, coverVersion)
            }
        }
    }
}

// ---- Top bar ----------------------------------------------------------------------------------------------------------

/** Home top bar: app icon, [label], clock and battery. Touch only (the controller never moves to it); a tap opens the menu. */
@Composable
fun HomeTopBar(icon: Painter, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = .85f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun rememberScreenshot(e: ResumeEntry?): ImageBitmap? {
    var shot by remember(e?.key, e?.time) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(e?.key, e?.time) {
        shot = withContext(Dispatchers.IO) { e?.screenshot?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() } }
    }
    return shot
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
                Box(
                    Modifier.fillMaxWidth().height(reflH).clipToBounds()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .30f), Color.Transparent)), blendMode = BlendMode.DstIn)
                        },
                ) {
                    Image(cover, null, Modifier.fillMaxWidth().aspectRatio(ratio).wrapContentHeight(Alignment.Top, unbounded = true)
                        .graphicsLayer { scaleY = -1f }.clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.FillBounds)
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

/** Touch-only tab/chip. */
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(p.accent) else Modifier.border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(50)))
            .focusProperties { canFocus = false }.clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) p.onAccent else Color.White, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SectionLabel(text: String, color: Color = palette.highlight) =
    Text(text, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))

/** One row of box art; the selected cover is kept in the second slot. */
@Composable
private fun Shelf(
    games: List<HomeGame>, version: Int, cardW: Dp, cardH: Dp, firstFocus: FocusRequester?, reflection: Boolean, focusScale: Float,
    actions: HomeActions, startPad: Dp, onSelected: (HomeGame, ImageBitmap?) -> Unit,
) {
    val rowState = rememberLazyListState()
    var focusIndex by remember { mutableIntStateOf(-1) }
    val gap = 18.dp
    val slotPx = with(LocalDensity.current) { (cardW + gap).roundToPx() }
    LaunchedEffect(focusIndex) { if (focusIndex >= 0) rowState.animateScrollToItem(focusIndex, -slotPx) }
    LazyRow(
        state = rowState,
        // Side padding leaves room for the enlarged (focused) first and last covers
        contentPadding = PaddingValues(start = startPad + cardW * (focusScale - 1f), end = 20.dp + cardW * (focusScale - 1f),
            top = cardH * (focusScale - 1f) * 0.6f, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth(),
    ) {
        items(games.size, key = { games[it].key }) { i ->
            val g = games[i]
            CoverCard(g.title, version, cardW, cardH, if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                reflection = reflection, focusScale = focusScale,
                onClick = { actions.play(g) }, onOptions = { actions.options(g) },
                onFocused = { cover -> onSelected(g, cover); focusIndex = i })
        }
    }
}

// ---- A: Showcase ------------------------------------------------------------------------------------------------------

@Composable
private fun ShowcaseHome(games: List<HomeGame>, latestByGame: Map<String, ResumeEntry>, actions: HomeActions, first: FocusRequester, version: Int) {
    val p = palette
    var selected by remember(games) { mutableStateOf(games.firstOrNull()) }
    // Loaded for the selected game itself, so it also shows with touch (no focus) and before the first key press
    val backdrop = rememberCover(selected?.title ?: "", version).takeIf { selected != null }
    val bg = p.background.last()
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(bg.copy(alpha = .35f))) {
        // Short screens (e.g. 640x480 at hdpi): smaller title, no buttons (tap or A plays, long press or Y opens the options)
        val compact = maxHeight < 300.dp
        val cardH = (maxHeight * if (compact) 0.50f else 0.44f).coerceIn(96.dp, 260.dp)
        val cardW = cardH * 0.72f
        // Backdrop: the selected game's cover, blurred (Android 12+) and darkened towards the text and the shelf
        backdrop?.let { Image(it, null, Modifier.fillMaxSize().blur(30.dp).graphicsLayer { alpha = .55f }, contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to bg.copy(alpha = .92f), 0.6f to bg.copy(alpha = .55f), 1f to bg.copy(alpha = .2f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to bg.copy(alpha = .9f))))
        Column(Modifier.fillMaxSize()) {
            selected?.let { g ->
                val st = latestByGame[g.key]
                Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = if (compact) 10.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                    Text(g.title, style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black, color = Color.White, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                    GameInfo(g, st != null)
                    if (!compact) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(if (st != null) "Continue" else "Play", true, Icons.Default.PlayArrow) { if (st != null) actions.resume(st) else actions.play(g) }
                        PillButton("Start fresh", false) { actions.fresh(g) }
                        PillButton("Options", false) { actions.options(g) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Shelf(games, version, cardW, cardH, first, reflection = !compact, focusScale = 1.15f, actions = actions, startPad = 20.dp) { g, _ -> selected = g }
            Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
        }
    }
}

// ---- B: Grid ----------------------------------------------------------------------------------------------------------

@Composable
private fun GridHome(
    games: List<HomeGame>, latest: List<ResumeEntry>, latestByGame: Map<String, ResumeEntry>, recent: Boolean, actions: HomeActions,
    menu: List<HomeMenuItem>, menuFocus: FocusRequester, first: FocusRequester, version: Int, empty: (@Composable () -> Unit)?,
    onMenuFocus: (Boolean) -> Unit,
) {
    val p = palette
    Row(Modifier.fillMaxSize()) {
        // Settings as an icon rail; the controller reaches it with left (or X)
        Column(
            Modifier.width(68.dp).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(p.panel)
                .onFocusChanged { onMenuFocus(it.hasFocus) }.verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val focusIdx = menu.indexOfFirst { it.selected }.coerceAtLeast(0)
            menu.forEachIndexed { i, m -> RailButton(m, if (i == focusIdx) Modifier.focusRequester(menuFocus) else Modifier) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(if (games.isEmpty()) "All games" else "All games · ${games.size}", !recent) { actions.showRecent(false) }
                Chip("Recent", recent) { actions.showRecent(true) }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    empty != null -> empty()
                    recent -> ResumeGrid(latest, actions.resume)
                    else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                        // At least 3 columns, so small and square screens don't get two huge covers per row
                        val cols = (maxWidth / 124.dp).toInt().coerceAtLeast(3)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols), contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(games.size, key = { games[it].key }) { i ->
                                val g = games[i]
                                GridCard(g, latestByGame[g.key] != null, version, if (i == 0) Modifier.focusRequester(first) else Modifier, actions)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RailButton(m: HomeMenuItem, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color.White.copy(alpha = .18f) else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) palette.highlight else Color.Transparent, RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = m.label }
            .clickable(onClick = m.onClick),
        contentAlignment = Alignment.Center,
    ) { MenuIconView(m.icon, 30.dp) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCard(g: HomeGame, hasState: Boolean, version: Int, modifier: Modifier, actions: HomeActions) {
    val p = palette
    val cover = rememberCover(g.title, version)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "scale")
    Column(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.zIndex(if (focused) 1f else 0f)) {
        // Covers keep their proportions (Saturn box art ranges from square to tall), sitting on the cell's bottom
        Box(
            modifier.fillMaxWidth().aspectRatio(0.72f)
                .onFocusChanged { focused = it.isFocused }
                .optionsKey { actions.options(g) }
                .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { actions.play(g) }, onLongClick = { actions.options(g) }),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val shape = RoundedCornerShape(10.dp)
            val frame = Modifier.border(if (focused) 3.dp else 1.dp, if (focused) p.highlight else Color.White.copy(alpha = .16f), shape)
            if (cover != null) {
                val ratio = (cover.width.toFloat() / cover.height).coerceIn(0.66f, 1.25f)
                Image(cover, g.title, Modifier.fillMaxWidth().aspectRatio(ratio).clip(shape).then(frame), contentScale = ContentScale.FillBounds)
            } else CoverPlaceholder(g.title, Modifier.fillMaxSize().then(frame))
            if (hasState) Text("SAVED", Modifier.align(Alignment.BottomEnd).padding(6.dp)
                .background(Color.Black.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                color = TealSave, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(g.title, color = if (focused) p.highlight else Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- C: List + preview ------------------------------------------------------------------------------------------------

@Composable
private fun ListHome(
    games: List<HomeGame>, latest: List<ResumeEntry>, latestByGame: Map<String, ResumeEntry>, recent: Boolean, actions: HomeActions,
    first: FocusRequester, version: Int, empty: (@Composable () -> Unit)?,
) {
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Games", !recent) { actions.showRecent(false) }
            Chip("Recent", recent) { actions.showRecent(true) }
            Chip("Settings", false) { actions.openMenu() }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                empty != null -> empty()
                recent -> ResumeGrid(latest, actions.resume)
                else -> {
                    var selected by remember(games) { mutableStateOf(games.firstOrNull()) }
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val wide = maxWidth >= 480.dp
                        val listW = (maxWidth * 0.42f).coerceAtMost(380.dp)
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LazyColumn(
                                Modifier.then(if (wide) Modifier.width(listW) else Modifier.fillMaxWidth())
                                    .fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(palette.panel),
                                contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                items(games.size, key = { games[it].key }) { i ->
                                    val g = games[i]
                                    ListRow(g, latestByGame[g.key] != null, if (i == 0) Modifier.focusRequester(first) else Modifier, actions) { selected = g }
                                }
                            }
                            if (wide) selected?.let { g -> Preview(g, latestByGame[g.key], version, actions, Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(g: HomeGame, hasState: Boolean, modifier: Modifier, actions: HomeActions, onFocused: () -> Unit) {
    val p = palette
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
            .background(if (focused) p.accent.copy(alpha = .22f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .optionsKey { actions.options(g) }
            .combinedClickable(onClick = { actions.play(g) }, onLongClick = { actions.options(g) })
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(when { focused -> p.highlight; hasState -> TealSave; else -> Color.White.copy(alpha = .2f) }))
        Spacer(Modifier.width(12.dp))
        Text(g.title, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (g.playSeconds >= 60) Text(formatPlayTime(g.playSeconds), color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Preview(g: HomeGame, st: ResumeEntry?, version: Int, actions: HomeActions, modifier: Modifier) {
    val cover = rememberCover(g.title, version)
    val shot = rememberScreenshot(st)
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val coverH = (maxHeight * 0.48f).coerceIn(120.dp, 260.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.height(coverH).aspectRatio(0.72f).clip(RoundedCornerShape(12.dp))) {
                    if (cover != null) Image(cover, g.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else CoverPlaceholder(g.title, Modifier.fillMaxSize())
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(g.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    GameInfo(g, st != null)
                    Spacer(Modifier.height(2.dp))
                    PillButton(if (st != null) "Continue" else "Play", true, Icons.Default.PlayArrow) { if (st != null) actions.resume(st) else actions.play(g) }
                    if (st != null) PillButton("Start fresh", false) { actions.fresh(g) }
                }
            }
            if (st != null) {
                SectionLabel("LATEST SAVE STATE")
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                    Box(Modifier.fillMaxHeight().aspectRatio(4f / 3f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(12.dp))
                        .background(Color.Black).border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(12.dp))) {
                        shot?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        Text(st.label, Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
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

/** "Theme" settings block (e.g. in Extras): picks the home screen layout. */
@Composable
fun HomeThemeSettings() {
    var style by HomeTheme.style
    SettingsGroup("Theme", Icons.Default.Palette) {
        ChoiceRow(
            "Home screen",
            when (style) {
                HomeStyle.Showcase -> "The selected game large on top, box art below"
                HomeStyle.Grid -> "Box art in a grid, settings as icons on the left"
                HomeStyle.List -> "A list of titles with a preview of the selected game; easiest to read on small screens"
                HomeStyle.Continue -> "Your latest save states first, then all games"
            },
            HomeStyle.entries.map { it.label }, style.ordinal,
        ) { style = HomeStyle.entries[it] }
    }
}
