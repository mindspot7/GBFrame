package gb.frame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/** Applies consumer-declared launcher aliases after Android replaces the installed package. */
class LauncherIconPackageUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val applicationInfo = try {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }
        val metadata = applicationInfo.metaData ?: return
        val bitmapAlias = metadata.getString(BITMAP_ALIAS_METADATA) ?: return
        val adaptiveAlias = metadata.getString(ADAPTIVE_ALIAS_METADATA) ?: return
        LauncherIconCompatibility.apply(context, bitmapAlias, adaptiveAlias)
        Log.i(TAG, "Applied launcher icon compatibility after package replacement")
    }

    private companion object {
        const val TAG = "GBFrameLauncherIcon"
        const val BITMAP_ALIAS_METADATA = "gb.frame.bitmapLauncherAlias"
        const val ADAPTIVE_ALIAS_METADATA = "gb.frame.adaptiveLauncherAlias"
    }
}
