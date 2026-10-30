package gb.frame

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Chooses the consumer-provided adaptive launcher alias on RG DS, whose launcher adds a white
 * plate to the plain bitmap icon used by other Android 13+ devices.
 *
 * Consumers declare two launcher aliases targeting their existing main activity: the normal
 * alias points at the transparent bitmap resource, and the adaptive alias points at a distinct
 * adaptive-icon resource. The main activity itself must not be disabled during this switch.
 */
object LauncherIconCompatibility {
    fun shouldUseAdaptiveIcon(manufacturer: String, model: String): Boolean =
        manufacturer.trim().equals("Anbernic", ignoreCase = true) &&
            model.trim().equals("RG DS", ignoreCase = true)

    /**
     * Apply the consumer-declared launcher aliases from app metadata.
     *
     * Call this from the consumer Application.onCreate as well as after package replacement:
     * Android does not send MY_PACKAGE_REPLACED on first install, so relying on the receiver
     * alone leaves the manifest-default bitmap alias active on RG DS until a later update.
     */
    @JvmStatic
    fun applyFromMetadata(context: Context): Boolean {
        val applicationInfo = try {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        val metadata = applicationInfo.metaData ?: return false
        val bitmapAlias = metadata.getString(BITMAP_ALIAS_METADATA) ?: return false
        val adaptiveAlias = metadata.getString(ADAPTIVE_ALIAS_METADATA) ?: return false
        apply(context, bitmapAlias, adaptiveAlias)
        return true
    }

    /** Apply consumer-owned launcher aliases using the current Android device identity. */
    @JvmStatic
    fun apply(
        context: Context,
        bitmapLauncherClassName: String,
        adaptiveLauncherClassName: String,
    ) {
        apply(
            context = context,
            bitmapLauncherClassName = bitmapLauncherClassName,
            adaptiveLauncherClassName = adaptiveLauncherClassName,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
        )
    }

    internal fun apply(
        context: Context,
        bitmapLauncherClassName: String,
        adaptiveLauncherClassName: String,
        manufacturer: String,
        model: String,
    ) {
        val useAdaptive = shouldUseAdaptiveIcon(manufacturer, model)
        if (useAdaptive) {
            // Keep a launcher entry available throughout the switch and don't disable the alias
            // Android is currently resolving until its replacement is already enabled.
            setAliasEnabled(context, adaptiveLauncherClassName, enabled = true, manifestDefaultEnabled = false)
            setAliasEnabled(context, bitmapLauncherClassName, enabled = false, manifestDefaultEnabled = true)
        } else {
            setAliasEnabled(context, bitmapLauncherClassName, enabled = true, manifestDefaultEnabled = true)
            setAliasEnabled(context, adaptiveLauncherClassName, enabled = false, manifestDefaultEnabled = false)
        }
    }

    private fun setAliasEnabled(
        context: Context,
        className: String,
        enabled: Boolean,
        manifestDefaultEnabled: Boolean,
    ) {
        val component = ComponentName(context.packageName, className)
        val packageManager = context.packageManager
        val currentEnabled = when (packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            else -> manifestDefaultEnabled
        }
        if (currentEnabled != enabled) {
            packageManager.setComponentEnabledSetting(
                component,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private const val BITMAP_ALIAS_METADATA = "gb.frame.bitmapLauncherAlias"
    private const val ADAPTIVE_ALIAS_METADATA = "gb.frame.adaptiveLauncherAlias"
}
