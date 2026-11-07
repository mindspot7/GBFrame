package gb.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class MobileLibraryPage { Library, Recent, Search }

class MobileLibraryState {
    var page by mutableStateOf(MobileLibraryPage.Library)
    var searchQuery by mutableStateOf("")

    /** Return to the root tab when Back is pressed; return false when it is already selected. */
    fun returnToLibrary(): Boolean {
        if (page == MobileLibraryPage.Library) return false
        page = MobileLibraryPage.Library
        return true
    }
}

@Composable
fun rememberMobileLibraryState() = remember { MobileLibraryState() }

/** Shared breakpoint for the phone library layout in every emulator app. */
fun isMobileLibraryLayout(maxWidth: androidx.compose.ui.unit.Dp, maxHeight: androidx.compose.ui.unit.Dp): Boolean =
    maxWidth < 600.dp && maxWidth < maxHeight

/** Smartphone navigation shared by emulator libraries. Settings is owned by the host's existing menu. */
@Composable
fun MobileLibraryNavigation(state: MobileLibraryState, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val selectedColor = palette.highlight
    val inactiveColor = Color.White.copy(alpha = .70f)
    Row(
        modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101117)).selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        @Composable
        fun Item(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
            var focused by remember { mutableStateOf(false) }
            val color = if (focused) palette.onAccent else if (selected) selectedColor else inactiveColor
            Column(
                Modifier.weight(1f).fillMaxHeight().onFocusChanged { focused = it.isFocused }
                    .kitFocusHighlight(focused, RoundedCornerShape(12.dp))
                    .selectable(selected = selected, role = Role.Tab, onClick = onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(23.dp))
                Spacer(Modifier.height(3.dp))
                KitText(label, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        Item("Library", Icons.Default.VideoLibrary, state.page == MobileLibraryPage.Library) { state.page = MobileLibraryPage.Library }
        Item("Recent", Icons.Default.History, state.page == MobileLibraryPage.Recent) { state.page = MobileLibraryPage.Recent }
        Item("Search", Icons.Default.Search, state.page == MobileLibraryPage.Search) { state.page = MobileLibraryPage.Search }
        Item("Settings", Icons.Default.Settings, false, onSettings)
    }
}

/** Library content for mobile tabs. Game lists, screenshots and actions are supplied by the host app. */
@Composable
fun MobileLibraryContent(
    state: MobileLibraryState,
    style: HomeStyle,
    games: List<HomeGame>,
    states: List<ResumeEntry>,
    actions: HomeActions,
    coverVersion: Int,
    empty: (@Composable () -> Unit)?,
    refocus: Int,
    fullscreen: Boolean,
    focusEnabled: Boolean,
    showcaseWatermark: (@Composable (Modifier) -> Unit)? = null,
) {
    if (state.page == MobileLibraryPage.Library || empty != null) {
        HomeLibrary(style, games, states, actions, coverVersion, empty, refocus = refocus,
            fullscreen = fullscreen, focusEnabled = focusEnabled, portraitShowcaseScreenshot = true,
            showcaseWatermark = showcaseWatermark)
        return
    }
    val displayedGames = if (state.page == MobileLibraryPage.Recent) {
        val byKey = games.associateBy { it.key }
        LibraryView.recentGames.mapNotNull { byKey[it] }
    } else games.filter { it.title.contains(state.searchQuery.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        if (state.page == MobileLibraryPage.Search) {
            OutlinedTextField(
                value = state.searchQuery, onValueChange = { state.searchQuery = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { KitText("Search games") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
        } else {
            KitText("Recently played", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        if (displayedGames.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    KitText(if (state.page == MobileLibraryPage.Recent) "No recent games" else "No games match your search",
                        color = Color.White, style = MaterialTheme.typography.titleMedium)
                    if (state.page == MobileLibraryPage.Recent) KitText("Games you open will appear here.",
                        color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            HomeLibrary(HomeStyle.Grid, displayedGames, states, actions, coverVersion, refocus = refocus,
                fullscreen = fullscreen, focusEnabled = focusEnabled && state.page != MobileLibraryPage.Search)
        }
    }
}
