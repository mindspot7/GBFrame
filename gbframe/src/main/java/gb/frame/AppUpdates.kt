package gb.frame

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.ActivityResult as PlayActivityResult
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.resume

/**
 * Shared update check for every app built on the kit. An app supplies only [AppUpdateConfig] (its official GitHub
 * repository); the kit decides where the install came from and uses that channel's updater:
 *  - [UpdateChannel.PLAY]: Google Play In-App Updates (the install was made by `com.android.vending`);
 *  - [UpdateChannel.GITHUB]: the latest release of the official repository, verified and installed through Android's
 *    own installer, which always asks the user to confirm.
 *
 * Channel rules: only the Play Store installer identifies Play. APKs published on GitHub carry the manifest metadata
 * `gb.frame.updateChannel = github`; the only official channels are these two, so an install from any other source
 * (ADB, file manager, browser, unknown) is treated as GitHub and is never identified as Play.
 */
data class AppUpdateConfig(
    /** Official repository as "owner/name", e.g. "mindspotgb/GBSat". */
    val githubRepo: String,
    /** Release asset used for the app: the first `.apk` asset whose name contains this text, or any `.apk` when empty. */
    val assetNameContains: String = "",
)

enum class UpdateChannel { PLAY, GITHUB }

/** Outcome of a check. Every failure is a value; nothing here throws into the UI. */
sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    /** [versionName] is the available version, [notes] the release notes (GitHub only). */
    data class Available(
        val versionName: String,
        val notes: String,
        val releasePage: String,
        val channel: UpdateChannel = UpdateChannel.GITHUB,
        /** Preferred Play update type, or null when Play has no in-app flow available. */
        val playUpdateType: Int? = null,
    ) : UpdateResult
    /** The check failed (network, unknown channel, missing APK...). [releasePage] is the manual alternative. */
    data class Failed(val reason: String, val releasePage: String) : UpdateResult
}

object AppUpdates {
    const val PLAY_INSTALLER = "com.android.vending"
    const val CHANNEL_METADATA = "gb.frame.updateChannel"
    private const val ACTION_INSTALL_RESULT = "gb.frame.UPDATE_INSTALL_RESULT"

    /** Where this installation came from. See the class rules above. */
    fun channelOf(context: Context): UpdateChannel {
        val pm = context.packageManager
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(context.packageName).installingPackageName
            else @Suppress("DEPRECATION") pm.getInstallerPackageName(context.packageName)
        }.getOrNull()
        // Only the Play Store installer means Play; the `github` build metadata and every other installer (ADB,
        // file manager, browser, unknown) mean GitHub. A Play-installed copy cannot be a GitHub APK.
        return if (installer == PLAY_INSTALLER) UpdateChannel.PLAY else UpdateChannel.GITHUB
    }

    fun releasePage(config: AppUpdateConfig) = "https://github.com/${config.githubRepo}/releases/latest"

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** Compares dotted numeric versions ("1.0 (build 5)" is read as 1.0): positive when [a] is newer than [b]. */
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.trim().removePrefix("v").takeWhile { it.isDigit() || it == '.' }
            .split('.').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }) - (y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    // -- Check ---------------------------------------------------------------------------------------------------

    suspend fun check(context: Context, config: AppUpdateConfig): UpdateResult {
        val page = releasePage(config)
        return runCatching {
            when (channelOf(context)) {
                UpdateChannel.PLAY -> checkPlay(context, playStorePage(context))
                UpdateChannel.GITHUB -> checkGitHub(context, config, page)
            }
        }.getOrElse {
            val fallback = if (channelOf(context) == UpdateChannel.PLAY) playStorePage(context) else page
            UpdateResult.Failed("Could not check for updates. Check your connection and try again.", fallback)
        }
    }

    private suspend fun checkPlay(context: Context, page: String): UpdateResult {
        val info = suspendCancellableCoroutine<com.google.android.play.core.appupdate.AppUpdateInfo?> { cont ->
            AppUpdateManagerFactory.create(context).appUpdateInfo
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return UpdateResult.Failed("Google Play could not be reached", page)
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return UpdateResult.UpToDate
        val updateType = when {
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
            else -> null
        }
        return UpdateResult.Available(
            versionName = "build ${info.availableVersionCode()}",
            notes = "",
            releasePage = page,
            channel = UpdateChannel.PLAY,
            playUpdateType = updateType,
        )
    }

    private fun playStorePage(context: Context) =
        "https://play.google.com/store/apps/details?id=${Uri.encode(context.packageName)}"

    private class Release(val tag: String, val notes: String, val apkUrl: String?, val apkName: String?, val sha256: String?, val shaUrl: String?)

    private suspend fun fetchRelease(config: AppUpdateConfig): Release = withContext(Dispatchers.IO) {
        val json = JSONObject(httpText("https://api.github.com/repos/${config.githubRepo}/releases/latest"))
        val assets = json.optJSONArray("assets")
        var apk: JSONObject? = null
        var shaAsset: JSONObject? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (apk == null && name.endsWith(".apk", true) && name.contains(config.assetNameContains, true)) apk = a
        }
        if (apk != null && assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").equals(apk.optString("name") + ".sha256", true)) shaAsset = a
        }
        val digest = apk?.optString("digest").orEmpty().removePrefix("sha256:").takeIf { it.length == 64 }
        Release(
            tag = json.optString("tag_name"), notes = json.optString("body"),
            apkUrl = apk?.optString("browser_download_url"), apkName = apk?.optString("name"),
            sha256 = digest, shaUrl = shaAsset?.optString("browser_download_url"),
        )
    }

    private suspend fun checkGitHub(context: Context, config: AppUpdateConfig, page: String): UpdateResult {
        val release = fetchRelease(config)
        if (release.tag.isBlank()) return UpdateResult.Failed("No release found", page)
        if (compareVersions(release.tag, currentVersion(context)) <= 0) return UpdateResult.UpToDate
        if (release.apkUrl == null) return UpdateResult.Failed("The release has no APK", page)
        return UpdateResult.Available(release.tag.removePrefix("v"), release.notes, page)
    }

    private fun httpText(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    // -- Install -------------------------------------------------------------------------------------------------

    /** Starts the update for the current channel. Returns a short failure reason, or null when the flow was handed over. */
    suspend fun install(activity: Activity, config: AppUpdateConfig): String? = runCatching {
        when (channelOf(activity)) {
            UpdateChannel.PLAY -> installPlay(activity)
            UpdateChannel.GITHUB -> installGitHub(activity, config)
        }
    }.getOrElse { "Could not start the update. Try again later." }

    private suspend fun installPlay(activity: Activity): String? {
        val manager = AppUpdateManagerFactory.create(activity)
        val info = suspendCancellableCoroutine<com.google.android.play.core.appupdate.AppUpdateInfo?> { cont ->
            manager.appUpdateInfo.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
        } ?: return "Google Play could not be reached"
        val updateType = when {
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
            else -> return "An update is available, but Google Play cannot apply it right now."
        }
        val listener = object : com.google.android.play.core.install.InstallStateUpdatedListener {
            override fun onStateUpdate(state: com.google.android.play.core.install.InstallState) {
                // Downloaded in the background; the user confirms the restart that applies it.
                if (state.installStatus() == InstallStatus.DOWNLOADED) {
                    manager.unregisterListener(this)
                    manager.completeUpdate()
                }
            }
        }
        if (updateType == AppUpdateType.FLEXIBLE) manager.registerListener(listener)
        return suspendCancellableCoroutine { continuation ->
            val task = try {
                manager.startUpdateFlow(info, activity, AppUpdateOptions.newBuilder(updateType).build())
            } catch (_: Exception) {
                if (updateType == AppUpdateType.FLEXIBLE) manager.unregisterListener(listener)
                if (continuation.isActive) continuation.resume("Google Play could not start the update. Try again later.")
                return@suspendCancellableCoroutine
            }
            task.addOnSuccessListener { result ->
                    // A canceled flow is a normal user choice; no update listener is needed afterward.
                    if (updateType == AppUpdateType.FLEXIBLE &&
                        (result == Activity.RESULT_CANCELED || result == PlayActivityResult.RESULT_IN_APP_UPDATE_FAILED)
                    ) {
                        manager.unregisterListener(listener)
                    }
                    if (continuation.isActive) continuation.resume(
                        if (result == PlayActivityResult.RESULT_IN_APP_UPDATE_FAILED)
                            "Google Play could not start the update. Try again later."
                        else null,
                    )
                }
                .addOnFailureListener {
                    if (updateType == AppUpdateType.FLEXIBLE) manager.unregisterListener(listener)
                    if (continuation.isActive) continuation.resume("Google Play could not start the update. Try again later.")
                }
        }
    }

    private suspend fun installGitHub(activity: Activity, config: AppUpdateConfig): String? = withContext(Dispatchers.IO) {
        val release = fetchRelease(config)
        val url = release.apkUrl ?: return@withContext "The release has no APK"
        if (!activity.packageManager.canRequestPackageInstalls()) {
            withContext(Dispatchers.Main) {
                activity.startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            }
            return@withContext "Allow installing from this app, then try again"
        }
        val dir = File(activity.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, release.apkName ?: "update.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            if (conn.responseCode != 200) return@withContext "Download failed (HTTP ${conn.responseCode})"
            conn.inputStream.use { input -> file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); digest.update(buf, 0, n) }
            } }
        } finally { conn.disconnect() }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        val expected = release.sha256 ?: release.shaUrl?.let { runCatching { httpText(it).trim().take(64).lowercase() }.getOrNull() }
        if (expected != null && !expected.equals(actual, true)) { file.delete(); return@withContext "SHA-256 does not match; update rejected" }
        verifyApk(activity, file)?.let { file.delete(); return@withContext it }
        withContext(Dispatchers.Main) { commit(activity, file) }
        null
    }

    /** The APK must be this app (same package name) signed with the same certificate as the installed one. */
    private fun verifyApk(context: Context, apk: File): String? {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return "The downloaded file is not a valid APK"
        if (archive.packageName != context.packageName) return "The APK is for a different app; update rejected"
        val installed = pm.getPackageInfo(context.packageName, flags)
        fun certs(p: android.content.pm.PackageInfo) =
            p.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val a = certs(archive); val b = certs(installed)
        return if (a.isEmpty() || a != b) "The APK signature does not match this app; update rejected" else null
    }

    /** Hands the APK to Android's installer, which always shows its own confirmation. */
    private fun commit(activity: Activity, apk: File) {
        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(activity.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input -> session.openWrite("update.apk", 0, apk.length()).use { out ->
                input.copyTo(out); session.fsync(out)
            } }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        @Suppress("DEPRECATION")
                        val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { c.startActivity(confirm) }
                    } else runCatching { c.applicationContext.unregisterReceiver(this) }
                }
            }
            val filter = IntentFilter(ACTION_INSTALL_RESULT)
            if (Build.VERSION.SDK_INT >= 33) activity.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("UnspecifiedRegisterReceiverFlag") activity.applicationContext.registerReceiver(receiver, filter)
            val pending = PendingIntent.getBroadcast(
                activity, id, Intent(ACTION_INSTALL_RESULT).setPackage(activity.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Update flow for one screen: checks once when first shown (silent: no dialog when up to date or on failure), and
 * shows the "update available" dialog. Place it once in the app's root. [manualTrigger] increments for a user-started
 * check from Extras/About ([UpdateCheckRow]), which also reports "up to date" and failures.
 */
@Composable
fun AppUpdatePrompt(config: AppUpdateConfig, manual: UpdateController = rememberUpdateController(config)) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { manual.checkAutomatically() }
    val state = manual.dialog
    if (state != null) {
        val page = (state as? UpdateResult.Available)?.releasePage ?: (state as? UpdateResult.Failed)?.releasePage ?: AppUpdates.releasePage(config)
        val openPage = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page))) }; manual.dialog = null }
        when (state) {
            is UpdateResult.Available -> AlertDialog(
                onDismissRequest = { manual.dialog = null },
                title = { KitText("Update available") },
                text = { KitText("Version ${state.versionName} is available.") },
                confirmButton = {
                    TextButton(onClick = {
                        manual.dialog = null
                        if (state.channel == UpdateChannel.PLAY && state.playUpdateType == null) {
                            openPage()
                            return@TextButton
                        }
                        val a = activity ?: return@TextButton
                        scope.launch { AppUpdates.install(a, config)?.let { manual.dialog = UpdateResult.Failed(it, page) } }
                    }) {
                        KitText(if (state.channel == UpdateChannel.PLAY && state.playUpdateType == null) "Open Google Play" else "Update")
                    }
                },
                dismissButton = { TextButton(onClick = { manual.dialog = null }) { KitText("Later") } },
            )
            is UpdateResult.Failed -> AlertDialog(
                onDismissRequest = { manual.dialog = null },
                title = { KitText("Update not available") },
                text = { KitText(state.reason) },
                confirmButton = {
                    val label = if (state.releasePage.contains("play.google.com")) "Open Google Play" else "Release page"
                    TextButton(onClick = openPage) { KitText(label) }
                },
                dismissButton = { TextButton(onClick = { manual.dialog = null }) { KitText("Close") } },
            )
            UpdateResult.UpToDate -> AlertDialog(
                onDismissRequest = { manual.dialog = null },
                title = { KitText("Up to date") },
                text = { KitText("You have the latest version.") },
                confirmButton = { TextButton(onClick = { manual.dialog = null }) { KitText("OK") } },
            )
        }
    }
}

class UpdateController(private val context: Context, private val config: AppUpdateConfig) {
    var dialog by mutableStateOf<UpdateResult?>(null)
    var busy by mutableStateOf(false)

    /** On open: only an available update is shown; "up to date" and errors stay silent. */
    suspend fun checkAutomatically() {
        if (checkedThisProcess) return
        checkedThisProcess = true
        (AppUpdates.check(context, config) as? UpdateResult.Available)?.let { dialog = it }
    }

    /** User-started: every outcome is reported. */
    suspend fun checkManually() {
        busy = true
        dialog = AppUpdates.check(context, config)
        busy = false
    }

    companion object {
        private var checkedThisProcess = false
        private var instance: UpdateController? = null
        internal fun shared(context: Context, config: AppUpdateConfig) =
            instance ?: UpdateController(context, config).also { instance = it }
    }
}

@Composable
fun rememberUpdateController(config: AppUpdateConfig): UpdateController {
    val ctx = LocalContext.current.applicationContext
    // One controller per process, so the root prompt and the Extras row share the same dialog state.
    return remember(config) { UpdateController.shared(ctx, config) }
}

/** The "Check for updates" row for Extras/About. Pair with an [AppUpdatePrompt] sharing the same [controller]. */
@Composable
fun UpdateCheckRow(controller: UpdateController) {
    val scope = rememberCoroutineScope()
    ActionRow(
        Icons.Default.SystemUpdate, "Check for updates",
        if (controller.busy) "Checking…" else "Current version ${AppUpdates.currentVersion(LocalContext.current)}",
        "Check", { if (!controller.busy) scope.launch { controller.checkManually() } },
    )
}
