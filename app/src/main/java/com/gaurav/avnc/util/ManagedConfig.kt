/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class ManagedConfig private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val restrictionManager: RestrictionsManager =
        appContext.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager

    @Volatile
    private var _restrictions: Bundle = restrictionManager.getApplicationRestrictions()

    private val _restrictionsChanged = MutableLiveData<Bundle?>(null)

    val restrictions: Bundle
        get() = _restrictions

    val restrictionsChanged: LiveData<Bundle?> = _restrictionsChanged

    fun isManaged(key: String): Boolean = _restrictions.containsKey(key)

    fun getManagedString(key: String, default: String?): String? =
        if (isManaged(key)) _restrictions.getString(key) else default

    fun getManagedInt(key: String, default: Int): Int =
        if (isManaged(key)) _restrictions.getInt(key, default) else default

    fun getManagedBoolean(key: String, default: Boolean): Boolean =
        if (isManaged(key)) _restrictions.getBoolean(key, default) else default

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED) {
                _restrictions = restrictionManager.getApplicationRestrictions()
                _restrictionsChanged.value = _restrictions
            }
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    companion object {
        @Volatile
        private var INSTANCE: ManagedConfig? = null

        fun obtain(context: Context): ManagedConfig {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ManagedConfig(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
