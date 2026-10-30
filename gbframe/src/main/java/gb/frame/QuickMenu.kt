package gb.frame

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A page in the GB-family in-game quick menu. The app supplies page content and core actions;
 * [QuickMenuHost] supplies the standard panel, Back stack, theme, and focus restoration.
 *
 * Set [fullScreen] for an existing app-owned screen (e.g. Cheats) that should be presented as a
 * full-window page while remaining in the same quick-menu Back stack.
 */
class QuickMenuPage(
    val id: String,
    val title: String,
    val fullScreen: Boolean = false,
    val content: @Composable QuickMenuScope.() -> Unit,
) {
    init { require(id.isNotBlank()) { "QuickMenuPage id must not be blank" } }
}

/** Navigation commands available to each app-provided quick-menu page. */
@Stable
class QuickMenuScope internal constructor(
    val isRoot: Boolean,
    private val navigate: (QuickMenuPage, String) -> Unit,
    private val backAction: () -> Unit,
    private val dismissAction: () -> Unit,
) {
    /** Push [page] and restore focus to [returnFocusItemId] when navigating back to this page. */
    fun openPage(page: QuickMenuPage, returnFocusItemId: String) = navigate(page, returnFocusItemId)

    /** Return one level; at the root this dismisses the quick menu. */
    fun back() = backAction()

    /** Dismiss the whole quick menu and let the app resume its game session. */
    fun dismiss() = dismissAction()
}

private data class QuickMenuStackEntry(val page: QuickMenuPage, val returnFocusItemId: String?)
private val LocalQuickMenuFocusScope = compositionLocalOf<QuickMenuFocusScope?> { null }

private class QuickMenuFocusScope(private val preferredItemId: String?) {
    private val requesters = LinkedHashMap<String, FocusRequester>()
    private var focusedItemId: String? = null

    fun register(id: String, requester: FocusRequester) {
        requesters[id] = requester
    }

    fun unregister(id: String, requester: FocusRequester) {
        if (requesters[id] === requester) requesters.remove(id)
        if (focusedItemId == id) focusedItemId = null
    }

    fun target(): FocusRequester? = requesters[preferredItemId] ?: requesters.values.firstOrNull()

    fun onFocused(id: String, focused: Boolean) {
        if (focused) focusedItemId = id else if (focusedItemId == id) focusedItemId = null
    }

    /** Wrap vertical focus between the first and last rows on this page. */
    fun wrapVertical(key: androidx.compose.ui.input.key.Key): Boolean {
        val ids = requesters.keys.toList()
        val index = ids.indexOf(focusedItemId)
        val targetId = when {
            key == androidx.compose.ui.input.key.Key.DirectionUp && index == 0 -> ids.lastOrNull()
            key == androidx.compose.ui.input.key.Key.DirectionDown && index == ids.lastIndex -> ids.firstOrNull()
            else -> null
        } ?: return false
        requesters[targetId]?.requestFocus()
        return true
    }
}

/**
 * Standard GB-family quick-menu host.
 *
 * - Shows the GBSat-style left panel and scrim.
 * - Owns the nested-page Back stack and Android BackHandler.
 * - Focuses the first enabled row on a page, or the row that opened it when returning.
 * - Uses the current GBFrame [palette], not app-specific hard-coded colors.
 *
 * Opening/closing the host itself does not pause or resume emulation. The app remains responsible
 * for pausing before showing the host and resuming in [onDismiss].
 */
@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun QuickMenuHost(
    rootPage: QuickMenuPage,
    gameTitle: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = remember(rootPage.id) { mutableStateListOf(QuickMenuStackEntry(rootPage, null)) }
    val current = stack.lastOrNull() ?: QuickMenuStackEntry(rootPage, null)
    val scope = QuickMenuScope(
        isRoot = stack.size == 1,
        navigate = { page, returnFocusItemId ->
            if (stack.lastOrNull()?.page?.id != page.id) {
                stack[stack.lastIndex] = stack.last().copy(returnFocusItemId = returnFocusItemId)
                stack.add(QuickMenuStackEntry(page, null))
            }
        },
        backAction = {
            if (stack.size > 1) stack.removeAt(stack.lastIndex) else onDismiss()
        },
        dismissAction = onDismiss,
    )
    val focusScope = remember(current.page.id, current.returnFocusItemId) {
        QuickMenuFocusScope(current.returnFocusItemId)
    }

    BackHandler(enabled = true) { scope.back() }

    val inputMode = LocalInputModeManager.current

    LaunchedEffect(focusScope, current.page.id) {
        // Let the new page and its first rows attach before requesting focus; retry while Compose
        // is laying out or a dynamically supplied menu row becomes available.
        repeat(40) {
            val target = focusScope.target()
            if (target != null) {
                val focused = runCatching {
                    inputMode.requestInputMode(InputMode.Keyboard)
                    target.requestFocus() // Unit in Compose 1.7; throws while the row isn't attached yet
                    true
                }.getOrDefault(false)
                if (focused) return@LaunchedEffect
            }
            delay(50)
        }
    }

    if (current.page.fullScreen) {
        key(current.page.id) {
            current.page.content(scope)
        }
    } else {
        val p = palette
        Box(modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = .45f))
                        .focusProperties { canFocus = false }
                        .clickable { scope.dismiss() },
                )
            }
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it },
            ) {
                Surface(
                    color = p.panel,
                    shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    border = BorderStroke(1.dp, p.border),
                    modifier = Modifier.fillMaxHeight().widthIn(max = 340.dp).fillMaxWidth(.85f)
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                ) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (scope.isRoot) {
                                Icon(Icons.Default.Pause, null, tint = p.highlight, modifier = Modifier.padding(12.dp))
                            } else {
                                IconButton(onClick = { scope.back() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.highlight)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    current.page.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                if (scope.isRoot && !gameTitle.isNullOrBlank()) {
                                    Text(
                                        gameTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = p.dim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(onClick = { scope.dismiss() }) {
                                Icon(Icons.Default.Close, "Close", tint = p.dim)
                            }
                        }
                        HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = Color.White.copy(alpha = .1f),
                        )
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)
                                .onPreviewKeyEvent { event ->
                                    val vertical = event.key == androidx.compose.ui.input.key.Key.DirectionUp ||
                                        event.key == androidx.compose.ui.input.key.Key.DirectionDown
                                    if (event.type == KeyEventType.KeyDown && vertical) {
                                        focusScope.wrapVertical(event.key)
                                    } else false
                                },
                        ) {
                            CompositionLocalProviderQuickMenuFocus(focusScope) {
                                current.page.content(scope)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A standard quick-menu action row. [id] must be unique on its current page. */
@Composable
fun QuickMenuItemRow(
    id: String,
    icon: ImageVector,
    label: String,
    value: String? = null,
    hasSubmenu: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    QuickMenuItemContainer(id = id, enabled = enabled, onClick = onClick) {
        Icon(icon, null, tint = palette.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) Color.White else palette.dim,
        )
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                color = palette.highlight,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hasSubmenu) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = palette.dim)
        }
    }
}

/** Standard quick-menu switch row for a consumer's core-provided widescreen patches. */
@Composable
fun WidescreenGameHacksItem(id: String = "widescreen-game-hacks", value: Boolean, onChange: (Boolean) -> Unit) =
    QuickMenuItemRow(
        id = id,
        icon = Icons.Default.Tune,
        label = "Widescreen game hacks",
        value = if (value) "On" else "Off",
        onClick = { onChange(!value) },
    )

/** Shared in-game Performance row for adaptive render skipping; app supplies and persists the core setting. */
@Composable
fun AdaptiveGraphicsItem(id: String = "adaptive-graphics", value: Boolean, onChange: (Boolean) -> Unit) =
    QuickMenuItemRow(
        id = id,
        icon = Icons.Default.SkipNext,
        label = "Adaptive graphics",
        value = if (value) "Auto" else "Off",
        onClick = { onChange(!value) },
    )

/** Standard in-game Performance row for a core- or code-provided frame-rate hack. */
@Composable
fun FrameRateHackItem(
    control: CheatPatchControl,
    enabled: Boolean = true,
    id: String = "frame-rate-hack",
    onChange: (Boolean) -> Unit,
) = QuickMenuItemRow(
    id = id,
    icon = Icons.Default.Speed,
    label = control.label,
    value = if (control.enabled) "On" else "Off",
    enabled = enabled,
    onClick = { onChange(!control.enabled) },
)

/**
 * A focusable/clickable row container for a custom menu row such as a save-state slot with a thumbnail.
 * Content receives the standard 12 dp horizontal and vertical padding.
 */
@Composable
fun QuickMenuItemContainer(
    id: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    val requester = remember(id) { FocusRequester() }
    val focusScope = LocalQuickMenuFocusScope.current
    DisposableEffect(focusScope, id, requester, enabled) {
        if (enabled) focusScope?.register(id, requester)
        onDispose { focusScope?.unregister(id, requester) }
    }
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                focusScope?.onFocused(id, it.isFocused)
            }
            .background(if (focused) palette.accent.copy(alpha = .16f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun CompositionLocalProviderQuickMenuFocus(
    focusScope: QuickMenuFocusScope,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalQuickMenuFocusScope provides focusScope, content = content)
}
