/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.os.BundleCompat
import com.gaurav.avnc.model.ManagedField
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.model.db.MainDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps server profiles in database in sync with the `managed_servers` restriction.
 * See docs/emm-managed-configurations.md
 */
object ManagedServerSync {
    private const val TAG = "ManagedServerSync"
    private const val KEY_MANAGED_SERVERS = "managed_servers"
    private const val MAX_PROFILES = 500

    // Same as R.array.profile_editor_security_values
    private val SECURITY_TYPES = setOf(0, 1, 2, 18, 19, 30)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /**
     * Starts syncing profiles whenever managed configuration changes.
     * Must be called on main thread.
     */
    fun start(context: Context) {
        val dao = MainDb.getInstance(context).serverProfileDao
        // This also emits current restrictions, so initial sync is done right away
        ManagedConfig.obtain(context).liveRestrictions.observeForever { restrictions ->
            scope.launch {
                mutex.withLock {
                    runCatching { dao.syncManagedProfiles(parse(restrictions)) }
                            .onFailure { Log.e(TAG, "Unable to sync managed profiles", it) }
                }
            }
        }
    }

    /**
     * Converts the `managed_servers` restriction to server profiles.
     * Invalid entries are skipped. Returns an empty list if restriction is missing.
     */
    fun parse(restrictions: Bundle): List<ServerProfile> {
        // bundle_array restrictions are delivered as Parcelable[], not as ArrayList
        val bundles = BundleCompat.getParcelableArray(restrictions, KEY_MANAGED_SERVERS, Bundle::class.java)
                              ?.filterIsInstance<Bundle>() ?: return emptyList()

        if (bundles.size > MAX_PROFILES)
            Log.w(TAG, "Too many managed servers, only first $MAX_PROFILES will be used")

        return bundles.take(MAX_PROFILES)
                .mapNotNull { parseProfile(it.flattenGroups()) } // SSH & advanced fields are grouped
                .distinctBy { it.managedId }
    }

    private fun parseProfile(bundle: Bundle): ServerProfile? {
        val id = bundle.getString("id")
        val host = bundle.getString("host")
        if (id.isNullOrBlank() || host.isNullOrBlank()) {
            Log.w(TAG, "Skipping managed server without id or host")
            return null
        }

        return ServerProfile(
                name = bundle.getString("name")?.takeIf { it.isNotBlank() } ?: host,
                host = host,
                port = bundle.getInt("port", 5900).takeIf { it in 1..65535 } ?: 5900,
                username = bundle.getString("username") ?: "",
                // Values of 'choice' restrictions are strings
                securityType = bundle.getString("security_type")?.toIntOrNull()?.takeIf { it in SECURITY_TYPES } ?: 0,
                channelType = when (bundle.getString("channel_type")) {
                    "ssh-tunnel" -> ServerProfile.CHANNEL_SSH_TUNNEL
                    else -> ServerProfile.CHANNEL_TCP
                },
                viewMode = if (bundle.getBoolean("view_only", false)) ServerProfile.VIEW_MODE_NO_INPUT
                           else ServerProfile.VIEW_MODE_NORMAL,
                sshHost = bundle.getString("ssh_host") ?: "",
                sshPort = bundle.getInt("ssh_port", 22).takeIf { it in 1..65535 } ?: 22,
                sshUsername = bundle.getString("ssh_username") ?: "",
                sshAuthType = when (bundle.getString("ssh_auth_type")) {
                    "key" -> ServerProfile.SSH_AUTH_KEY
                    else -> ServerProfile.SSH_AUTH_PASSWORD
                },
                isManaged = true,
                managedId = id,
        ).apply {
            // Optional fields are enforced only if set in managed configuration
            managedFields = ManagedField.entries.filter { it.readInto(this, bundle) }.joinToString(",") { it.key }
        }
    }
}
