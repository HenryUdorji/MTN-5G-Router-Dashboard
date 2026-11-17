package com.routerdash.app

import android.app.Application
import com.routerdash.app.data.SettingsStore

class RouterDashApp : Application() {
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
    }
}
