package com.flashloop.app

import android.app.Application
import com.flashloop.app.data.AppDatabase
import com.flashloop.app.data.Repository
import com.flashloop.app.data.SettingsRepo
import com.flashloop.app.scheduling.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Tiny "service locator": one database, one settings store, one repository for the whole app. */
class FlashLoopApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase by lazy { AppDatabase.get(this) }
    val settings: SettingsRepo by lazy { SettingsRepo(this) }
    val repository: Repository by lazy { Repository(this, db, settings) }

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
    }
}
