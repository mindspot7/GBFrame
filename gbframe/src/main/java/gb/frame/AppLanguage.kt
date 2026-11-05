package gb.frame

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.util.Locale

/** Per-app language preference shared by GBFrame-based emulators. */
object AppLanguage {
    const val ENGLISH = "en"
    const val PORTUGUESE_BRAZIL = "pt-BR"
    const val SPANISH_LATIN_AMERICA = "es-419"

    private const val PREFS = "gbframe_preferences"
    private const val KEY_LANGUAGE = "app_language"

    fun currentTag(context: Context): String {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedTag = preferences.getString(KEY_LANGUAGE, null)
        if (savedTag == ENGLISH || savedTag == PORTUGUESE_BRAZIL || savedTag == SPANISH_LATIN_AMERICA) {
            return savedTag
        }

        // Capture the device language once. After this first read, only an explicit choice in Extras can change it.
        val detectedTag = deviceLanguageTag()
        preferences.edit().putString(KEY_LANGUAGE, detectedTag).apply()
        return detectedTag
    }

    /** Wizard default follows the device locale; an explicit language choice still takes precedence. */
    fun wizardTag(context: Context): String {
        return currentTag(context)
    }

    private fun deviceLanguageTag(): String {
        val deviceTag = Resources.getSystem().configuration.locales[0]?.toLanguageTag().orEmpty()
        return when {
            deviceTag.startsWith("pt", ignoreCase = true) -> PORTUGUESE_BRAZIL
            deviceTag.startsWith("es", ignoreCase = true) -> SPANISH_LATIN_AMERICA
            else -> ENGLISH
        }
    }

    /** Wrap each Activity base context so resource lookups use the selected app language. */
    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(currentTag(context))
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(configuration)
    }

    /** Keep non-Activity consumers, including native locale callbacks, in sync with the app choice. */
    fun applyProcessLocale(context: Context) {
        Locale.setDefault(Locale.forLanguageTag(currentTag(context)))
    }

    fun select(context: Context, languageTag: String) {
        require(languageTag == ENGLISH || languageTag == PORTUGUESE_BRAZIL || languageTag == SPANISH_LATIN_AMERICA)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageTag)
            .apply()
        Locale.setDefault(Locale.forLanguageTag(languageTag))
        (context as? Activity)?.recreate()
    }
}

/** Shared Extras-page control for the language preference. */
@Composable
fun AppLanguageSettings() {
    val context = LocalContext.current
    val options = listOf(
        stringResource(R.string.gbframe_language_english),
        stringResource(R.string.gbframe_language_portuguese_brazil),
        stringResource(R.string.gbframe_language_spanish_latin_america),
    )
    val selected = when (AppLanguage.currentTag(context)) {
        AppLanguage.PORTUGUESE_BRAZIL -> 1
        AppLanguage.SPANISH_LATIN_AMERICA -> 2
        else -> 0
    }
    SettingsGroup(stringResource(R.string.gbframe_app_language), null) {
        ChoiceRow(
            title = stringResource(R.string.gbframe_app_language),
            sub = stringResource(R.string.gbframe_app_language_description),
            options = options,
            selected = selected,
            onSelect = { index ->
                AppLanguage.select(
                    context,
                    when (index) {
                        1 -> AppLanguage.PORTUGUESE_BRAZIL
                        2 -> AppLanguage.SPANISH_LATIN_AMERICA
                        else -> AppLanguage.ENGLISH
                    },
                )
            },
        )
    }
}
