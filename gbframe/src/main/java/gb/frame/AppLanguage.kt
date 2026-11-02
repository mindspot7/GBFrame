package gb.frame

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.util.Locale

/** Per-app language preference shared by GBFrame-based emulators. */
object AppLanguage {
    const val ENGLISH = "en"
    const val PORTUGUESE_BRAZIL = "pt-BR"

    private const val PREFS = "gbframe_preferences"
    private const val KEY_LANGUAGE = "app_language"

    fun currentTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, ENGLISH)
            ?.takeIf { it == ENGLISH || it == PORTUGUESE_BRAZIL }
            ?: ENGLISH

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
        require(languageTag == ENGLISH || languageTag == PORTUGUESE_BRAZIL)
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
    )
    val selected = if (AppLanguage.currentTag(context) == AppLanguage.PORTUGUESE_BRAZIL) 1 else 0
    SettingsGroup(stringResource(R.string.gbframe_app_language), null) {
        ChoiceRow(
            title = stringResource(R.string.gbframe_app_language),
            sub = stringResource(R.string.gbframe_app_language_description),
            options = options,
            selected = selected,
            onSelect = { index ->
                AppLanguage.select(
                    context,
                    if (index == 1) AppLanguage.PORTUGUESE_BRAZIL else AppLanguage.ENGLISH,
                )
            },
        )
    }
}
