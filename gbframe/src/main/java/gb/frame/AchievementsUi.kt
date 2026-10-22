package gb.frame

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Small badge image loaded from RetroAchievements (with a trophy placeholder). */
@Composable
fun RaBadge(url: String, size: Int, dim: Boolean = false) {
    var img by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { img = RetroAchievements.image(url) }
    val mod = Modifier.size(size.dp).clip(RoundedCornerShape(6.dp)).alpha(if (dim) 0.45f else 1f)
    val bitmap = img
    if (bitmap != null) {
        Image(bitmap, null, mod)
    } else {
        Box(mod.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.EmojiEvents, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shows RetroAchievements notifications (unlocks, game info...) one at a time at the top of the game screen. */
@Composable
fun RaNotifications(modifier: Modifier = Modifier) {
    val current = RetroAchievements.notifications.firstOrNull() ?: return
    LaunchedEffect(current) {
        delay(if (current.kind == "unlocked" || current.kind == "completed") 4500 else 3000)
        RetroAchievements.notifications = RetroAchievements.notifications.drop(1)
    }
    Surface(
        shape = RoundedCornerShape(16.dp), color = Color(0xE6101418), modifier = modifier.widthIn(max = 420.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RaBadge(current.badgeUrl, 44)
            Spacer(Modifier.width(10.dp))
            Column {
                val header = when (current.kind) {
                    "unlocked" -> "Achievement unlocked" + if (current.points > 0) " · ${current.points} pts" else ""
                    "completed" -> "Game mastered"
                    "game" -> "RetroAchievements"
                    "challenge" -> "Challenge active"
                    "progress" -> "Progress"
                    else -> current.title
                }
                Text(header, color = Color(0xFFFFC94D), style = MaterialTheme.typography.labelMedium)
                if (current.kind != "info" && current.kind != "leaderboard") {
                    Text(current.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (current.text.isNotBlank()) {
                    Text(current.text, color = Color(0xFFCCCCCC), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Achievement list for the running game. */
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    load: suspend () -> Pair<RetroAchievements.GameSummary?, List<RetroAchievements.Achievement>> = { RetroAchievements.achievements() },
) {
    var data by remember { mutableStateOf<Pair<RetroAchievements.GameSummary?, List<RetroAchievements.Achievement>>?>(null) }
    LaunchedEffect(Unit) { data = load() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .75f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 560.dp).heightIn(max = 600.dp).padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text("Achievements", style = MaterialTheme.typography.titleLarge)
                }
                val d = data
                when {
                    d == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    d.first == null -> Text(
                        if (RetroAchievements.user == null) "Log in to RetroAchievements in Settings to earn achievements."
                        else "No achievements available for this game (or it was not recognized).",
                        modifier = Modifier.padding(16.dp),
                    )
                    else -> {
                        val s = d.first!!
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RaBadge(s.badgeUrl, 56)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(s.title, style = MaterialTheme.typography.titleMedium)
                                Text("${s.unlocked}/${s.total} unlocked · ${s.pointsUnlocked}/${s.pointsTotal} points" +
                                    if (s.hardcore) " · Hardcore" else "", style = MaterialTheme.typography.bodySmall)
                                if (s.richPresence.isNotBlank()) Text(s.richPresence, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (s.total > 0) {
                            LinearProgressIndicator(progress = { s.unlocked / s.total.toFloat() },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp))
                        }
                        LazyColumn {
                            items(d.second) { a ->
                                ListItem(
                                    leadingContent = { RaBadge(if (a.unlocked) a.badgeUrl else a.lockedBadgeUrl.ifBlank { a.badgeUrl }, 48, dim = !a.unlocked) },
                                    headlineContent = { Text(a.title) },
                                    supportingContent = {
                                        Column {
                                            Text(a.description, style = MaterialTheme.typography.bodySmall)
                                            if (!a.unlocked && a.progress.isNotBlank()) Text("Progress: ${a.progress}",
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    trailingContent = { Text("${a.points}", style = MaterialTheme.typography.labelLarge) },
                                    overlineContent = { Text(a.bucket, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** RetroAchievements settings: enable, login form or logged-in account, hardcore mode. */
@Composable
fun RetroAchievementsSettings(appName: String) {
    val ra = RetroAchievements
    SettingsGroup("RetroAchievements", Icons.Default.EmojiEvents) {
        ToggleRow(Icons.Default.EmojiEvents, "Enable RetroAchievements", "Earn achievements from retroachievements.org (account and internet needed)",
            ra.enabled) { ra.changeEnabled(it) }
        if (ra.enabled) {
            SettingsDivider()
            if (ra.user != null) {
                ActionRow(Icons.Default.AccountCircle, "Logged in as ${ra.user}", "${ra.score} points" + (ra.gameTitle?.let { " · playing $it" } ?: ""),
                    "Log out", { ra.logout() })
            } else {
                var raUser by remember { mutableStateOf("") }
                var raPass by remember { mutableStateOf("") }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(raUser, { raUser = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(raPass, { raPass = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { ra.login(raUser, raPass); raPass = "" }, enabled = !ra.loginPending && raUser.isNotBlank() && raPass.isNotBlank()) {
                            Text(if (ra.loginPending) "Logging in…" else "Log in")
                        }
                        ra.loginError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 12.dp)) }
                    }
                    Text("Your password is only sent to retroachievements.org; $appName keeps the login token, not the password.",
                        style = MaterialTheme.typography.bodySmall, color = palette.dim)
                }
            }
            SettingsDivider()
            ToggleRow(Icons.Default.LocalFireDepartment, "Hardcore mode",
                "More points; save state loading and cheats are disabled. Turning it on during a game resets the game.",
                ra.hardcore) { ra.changeHardcore(it) }
            SettingsDivider()
            ToggleRow(Icons.Default.CloudOff, "Offline achievements",
                "Casual mode only: without a connection, unlocks are kept and sent when you are back online (the game must have been started online once)" +
                    if (ra.pendingSync > 0) " · ${ra.pendingSync} waiting to sync" else "",
                ra.offlineEnabled) { ra.changeOffline(it) }
        }
    }
}

/** Logged-in RetroAchievements user (picture, name, points), e.g. at the top of the library menu. */
@Composable
fun RaUserHeader() {
    val ra = RetroAchievements
    val name = ra.user ?: return
    if (!ra.enabled) return
    val p = palette
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        var pic by remember(ra.avatarUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(ra.avatarUrl) { pic = ra.avatarUrl?.let { ra.image(it) } }
        Box(Modifier.size(40.dp).clip(CircleShape).background(androidx.compose.ui.graphics.lerp(p.accent, p.surface, 0.7f)),
            contentAlignment = Alignment.Center) {
            val img = pic
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.AccountCircle, null, tint = Color.White.copy(alpha = .8f))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${ra.score} pts", style = MaterialTheme.typography.labelMedium, color = p.highlight, maxLines = 1)
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Color.White.copy(alpha = .12f))
}
