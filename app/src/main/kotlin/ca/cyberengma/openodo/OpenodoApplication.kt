// SPDX-License-Identifier: GPL-3.0-only
package ca.cyberengma.openodo

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import dagger.hilt.android.HiltAndroidApp
import ca.cyberengma.openodo.work.ReminderWorkScheduler
import java.util.Locale

@HiltAndroidApp
class OpenodoApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyPersianLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        applyPersianLocale(this)
        runCatching { ReminderWorkScheduler.schedule(this) }
    }

    companion object {
        fun applyPersianLocale(context: Context): Context {
            val locale = Locale.forLanguageTag("fa-IR")
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocales(LocaleList(locale))
            config.setLayoutDirection(locale)
            return context.createConfigurationContext(config)
        }
    }
}
