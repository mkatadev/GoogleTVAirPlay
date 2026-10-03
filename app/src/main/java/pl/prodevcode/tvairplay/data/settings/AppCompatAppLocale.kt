package pl.prodevcode.tvairplay.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import javax.inject.Inject
import javax.inject.Singleton
import pl.prodevcode.tvairplay.platform.AppLocale

@Singleton
class AppCompatAppLocale @Inject constructor() : AppLocale {
    override fun current(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    override fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }
}
