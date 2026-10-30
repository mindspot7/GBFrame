package gb.frame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Applies consumer-declared launcher aliases after Android replaces the installed package. */
class LauncherIconPackageUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (LauncherIconCompatibility.applyFromMetadata(context)) {
            Log.i(TAG, "Applied launcher icon compatibility after package replacement")
        }
    }

    private companion object {
        const val TAG = "GBFrameLauncherIcon"
    }
}
