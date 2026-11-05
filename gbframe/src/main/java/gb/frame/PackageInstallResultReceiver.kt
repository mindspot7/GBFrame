package gb.frame

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build

/**
 * Receives the private PackageInstaller session callback and opens only a verified system installer screen.
 * The nested intent is treated as untrusted input: it is never passed to startActivity directly.
 */
class PackageInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, callback: Intent) {
        if (callback.action != "${context.packageName}.${AppUpdates.ACTION_INSTALL_RESULT}") return
        if (callback.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE) != PackageInstaller.STATUS_PENDING_USER_ACTION) return

        val callbackSessionId = callback.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (callbackSessionId <= 0) return

        val suppliedConfirmation = callback.confirmationIntent() ?: return
        val confirmationAction = suppliedConfirmation.action
        if (confirmationAction !in ALLOWED_CONFIRM_ACTIONS) return
        val sessionId = suppliedConfirmation.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (sessionId != callbackSessionId) return

        val suppliedComponent = suppliedConfirmation.component
        val suppliedPackage = suppliedConfirmation.`package`
        if (suppliedComponent != null && suppliedPackage != null && suppliedComponent.packageName != suppliedPackage) return

        // Resolve a clean probe rather than the supplied intent, so selector/data/flags/clip data cannot affect
        // component resolution. Restrict the result to the explicitly supplied component/package when present.
        val probe = Intent(confirmationAction).apply {
            when {
                suppliedComponent != null -> component = suppliedComponent
                suppliedPackage != null -> setPackage(suppliedPackage)
            }
        }
        val activityInfo = probe.resolveInstallerActivity(context) ?: return
        val resolvedComponent = ComponentName(activityInfo.packageName, activityInfo.name)
        if (suppliedComponent != null && resolvedComponent != suppliedComponent) return
        if (suppliedPackage != null && resolvedComponent.packageName != suppliedPackage) return
        if (!activityInfo.exported) return

        val appFlags = activityInfo.applicationInfo?.flags ?: return
        val systemAppFlags = android.content.pm.ApplicationInfo.FLAG_SYSTEM or
            android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        if (appFlags and systemAppFlags == 0) return

        // Rebuild from the validated fields only. In particular, do not retain URI grants, selectors, arbitrary
        // extras, or launch flags from the nested intent.
        val safeConfirmation = Intent(confirmationAction)
            .setComponent(resolvedComponent)
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(safeConfirmation) }
    }

    private fun Intent.confirmationIntent(): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_INTENT)
    }

    @Suppress("DEPRECATION")
    private fun Intent.resolveInstallerActivity(context: Context) = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.resolveActivity(this, PackageManager.ResolveInfoFlags.of(0))?.activityInfo
    } else {
        context.packageManager.resolveActivity(this, 0)?.activityInfo
    }

    private companion object {
        // These platform actions cover Android 9's permission-confirmation flow and the install flow used by newer
        // Android releases. The constants are hidden from the public SDK.
        const val ACTION_CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"
        const val ACTION_CONFIRM_PERMISSIONS = "android.content.pm.action.CONFIRM_PERMISSIONS"
        val ALLOWED_CONFIRM_ACTIONS = setOf(ACTION_CONFIRM_INSTALL, ACTION_CONFIRM_PERMISSIONS)
    }
}
