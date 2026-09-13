/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import android.app.Application
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ProcessLifecycleOwner
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.util.ManagedConfig
import com.gaurav.avnc.util.ManagedServerSync
import com.gaurav.avnc.model.db.MainDb

class App : Application() {

    @Keep
    lateinit var prefs: AppPreferences

    override fun onCreate() {
        super.onCreate()
        configureLeakCanary()

        ManagedConfig.obtain(this)
        MainDb.getInstance(this).let { db ->
            ManagedServerSync.initialize(this, db)
            val owner = ProcessLifecycleOwner.get()
            ManagedConfig.obtain(this).restrictionsChanged.observe(owner, Observer { bundle ->
                if (bundle != null) {
                    ManagedServerSync.scheduleSync(this, db)
                }
            })
        }

        prefs = AppPreferences(this)
        prefs.ui.theme.observeForever { updateNightMode(it) }
    }

    private fun updateNightMode(theme: String) {
        val nightMode = when (theme) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun configureLeakCanary() {
        if (BuildConfig.DEBUG) {
            Class.forName("com.gaurav.avnc.LeakCanaryInitializer")
                    .getMethod("initialize", Application::class.java)
                    .invoke(null, this)
        }
    }
}