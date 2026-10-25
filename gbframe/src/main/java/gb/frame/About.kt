package gb.frame

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One line of the Credits card of [AboutPage]: a project or person, and what it provides. */
data class AboutCredit(val name: String, val text: String)

/**
 * The label every app built on the kit uses to describe itself: "<Manufacturer> <Console> Emulator for Android",
 * e.g. "Sega Saturn Emulator for Android". [AboutPage] shows it under the app's name.
 */
fun emulatorLabel(manufacturer: String, console: String) = "$manufacturer $console Emulator for Android"

/**
 * The standard About page of every app built on the kit (Settings → About): icon, app name, the [emulatorLabel], the
 * version, the Application id (the installed package name), then Credits and License cards. Apps only supply their own
 * name, console, icon, credits and license text; the layout is the same everywhere.
 */
@Composable
fun AboutPage(
    appName: String,
    manufacturer: String,
    console: String,
    icon: Painter,
    credits: List<AboutCredit>,
    license: String,
) {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    val p = palette
    SettingsGroup(null, null) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(icon, null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.height(12.dp))
            Text(appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(emulatorLabel(manufacturer, console), color = p.dim)
            Spacer(Modifier.height(4.dp))
            Text("Version $version", color = p.highlight, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(2.dp))
            Text("Application id ${ctx.packageName}", color = p.dim, style = MaterialTheme.typography.labelMedium)
        }
    }
    SettingsGroup("Credits", Icons.Default.Favorite) {
        credits.forEachIndexed { i, credit ->
            if (i > 0) SettingsDivider()
            InfoRow(credit.name, credit.text)
        }
    }
    SettingsGroup("License", Icons.Default.Gavel) {
        Text(license, style = MaterialTheme.typography.bodySmall, color = p.dim, modifier = Modifier.padding(16.dp))
    }
}
