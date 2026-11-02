package gb.frame

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** One line of the Credits card of [AboutPage]: a project or person, and what it provides. */
data class AboutCredit(val name: String, val text: String)

/** Full license text shipped in the consuming app's assets directory. */
data class AboutLicenseDocument(val name: String, val assetPath: String)

/**
 * The label every app built on the kit uses to describe itself: "<Manufacturer> <Console> Emulator for Android",
 * e.g. "Sega Saturn Emulator for Android". [AboutPage] shows it under the app's name.
 */
fun emulatorLabel(manufacturer: String, console: String) = "$manufacturer $console Emulator for Android"

/**
 * The standard About page of every app built on the kit (Settings → About): icon, app name, the [emulatorLabel], the
 * version, the Application id (the installed package name), then Credits and License cards. Apps only supply their own
 * name, console, icon, core/data credits, license summary and license documents; shared kit data sources are credited
 * here. Credit entries that name the app itself or GBFrame are suppressed so this card stays focused on external
 * contributions. Each app supplies its own public privacy-policy URL.
 */
@Composable
fun AboutPage(
    appName: String,
    manufacturer: String,
    console: String,
    icon: Painter,
    credits: List<AboutCredit>,
    license: String,
    privacyPolicyUrl: String,
    tagline: String? = null,
    licenseDocuments: List<AboutLicenseDocument> = emptyList(),
) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    var openLicense by remember { mutableStateOf<AboutLicenseDocument?>(null) }
    val openLicenseText = remember(openLicense) {
        openLicense?.let { document ->
            runCatching { ctx.assets.open(document.assetPath).bufferedReader().use { it.readText() } }
                .getOrElse { "License text is unavailable in this build." }
        }
    }
    val p = palette
    val visibleCredits = (credits + AboutCredit(
        "libretro-thumbnails",
        "Optional game box art source, fetched from the Libretro thumbnail service.",
    )).filterNot { credit ->
        val entry = "${credit.name} ${credit.text}"
        entry.contains("GBFrame", ignoreCase = true) || entry.contains(appName, ignoreCase = true)
    }
    SettingsGroup(null, null) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(icon, null, Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text(appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(emulatorLabel(manufacturer, console), color = p.dim)
            tagline?.let {
                Text(
                    brandedAboutTagline(it, p.highlight),
                    color = p.dim,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { runCatching { uriHandler.openUri(GAMEBEACON_URL) } }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Version $version", color = p.highlight, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(2.dp))
            Text("appid: ${ctx.packageName}", color = p.dim, style = MaterialTheme.typography.labelMedium)
        }
    }
    if (visibleCredits.isNotEmpty()) SettingsGroup("Credits", Icons.Default.Favorite) {
        visibleCredits.forEachIndexed { i, credit ->
            if (i > 0) SettingsDivider()
            InfoRow(credit.name, credit.text)
        }
    }
    SettingsGroup("License", Icons.Default.Gavel) {
        Text(license, style = MaterialTheme.typography.bodySmall, color = p.dim, modifier = Modifier.padding(16.dp))
        licenseDocuments.forEach { document ->
            TextButton(onClick = { openLicense = document }) { Text(document.name) }
        }
    }
    SettingsGroup("Privacy policy", null) {
        TextButton(
            onClick = {
                val opened = runCatching { uriHandler.openUri(privacyPolicyUrl) }.isSuccess
                if (!opened) {
                    Toast.makeText(ctx, "Couldn't open the privacy policy", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Read the privacy policy")
        }
    }
    openLicense?.let { document ->
        AlertDialog(
            onDismissRequest = { openLicense = null },
            title = { Text(document.name) },
            text = {
                Text(
                    openLicenseText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { openLicense = null }) { Text("Close") } },
        )
    }
}

private const val GAMEBEACON_URL = "https://gamebeacon.dev"

private fun brandedAboutTagline(text: String, brandColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        val brand = "GameBeacon.dev"
        val index = text.indexOf(brand)
        if (index < 0) {
            append(text)
        } else {
            append(text.substring(0, index))
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = brandColor,
                    textDecoration = TextDecoration.Underline,
                ),
            ) { append(brand) }
            append(text.substring(index + brand.length))
        }
    }
