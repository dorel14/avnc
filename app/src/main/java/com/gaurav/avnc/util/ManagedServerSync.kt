/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.room.withTransaction
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.model.db.MainDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "ManagedServerSync"
private const val MAX_MANAGED_PROFILES = 500

object ManagedServerSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    fun initialize(context: Context, db: MainDb) {
        scope.launch {
            sync(context, db, ManagedConfig.obtain(context).restrictions)
        }
    }

    fun scheduleSync(context: Context, db: MainDb) {
        scope.launch {
            sync(context, db, ManagedConfig.obtain(context).restrictions)
        }
    }

    private suspend fun sync(context: Context, db: MainDb, restrictions: Bundle?) {
        syncMutex.lock()
        try {
            db.withTransaction {
                val dao = db.serverProfileDao
                if (restrictions == null || !restrictions.containsKey("managed_servers")) {
                    val managed = dao.getManagedProfiles()
                    for (p in managed) {
                        dao.delete(p)
                    }
                    return@withTransaction
                }

                val bundles = extractManagedServerBundles(restrictions)
                if (bundles == null) return@withTransaction
                val parsed = parseManagedServers(bundles)
                dao.replaceManagedProfiles(parsed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
        } finally {
            syncMutex.unlock()
        }
    }

    private fun extractManagedServerBundles(restrictions: Bundle): List<Bundle>? {
        if (Build.VERSION.SDK_INT < 23) {
            Log.w(TAG, "bundle_array not available on API <23, preserving managed profiles")
            return null
        }
        val array = restrictions.getParcelableArrayList<Bundle>("managed_servers") ?: return emptyList()
        val result = mutableListOf<Bundle>()
        for (item in array) {
            when (item) {
                is Bundle -> result.add(item)
                else -> Log.w(TAG, "Skipping non-Bundle entry in managed_servers")
            }
        }
        return result
    }

    private fun parseManagedServers(bundles: List<Bundle>): List<ServerProfile> {
        val seenIds = mutableSetOf<String>()
        val result = mutableListOf<ServerProfile>()

        for (bundle in bundles) {
            if (result.size >= MAX_MANAGED_PROFILES) {
                Log.w(TAG, "Exceeded max managed profiles ($MAX_MANAGED_PROFILES), truncating")
                break
            }

            val managedId = bundle.getString("id")?.takeIf { it.isNotBlank() } ?: run {
                Log.w(TAG, "Skipping managed server entry with empty id")
                continue
            }

            if (managedId in seenIds) {
                Log.w(TAG, "Skipping duplicate managed server id: $managedId")
                continue
            }
            seenIds.add(managedId)

            val host = bundle.getString("host")?.takeIf { it.isNotBlank() } ?: run {
                Log.w(TAG, "Skipping managed server entry with empty host (id=$managedId)")
                continue
            }

            val name = bundle.getString("name")?.takeIf { it.isNotBlank() } ?: host

            val rawPort = bundle.getInt("port", 5900)
            val port = if (rawPort in 1..65535) rawPort else 5900

            val securityType = mapSecurityType(bundle.getInt("security_type", 0))
            val channelType = mapChannelType(bundle.getString("channel_type", "tcp"))
            val sshHost = bundle.getString("ssh_host") ?: ""
            val rawSshPort = bundle.getInt("ssh_port", 22)
            val sshPort = if (rawSshPort in 1..65535) rawSshPort else 22
            val sshUsername = bundle.getString("ssh_username") ?: ""
            val sshAuthType = mapSshAuthType(bundle.getString("ssh_auth_type", "key"))
            val username = bundle.getString("username") ?: ""
            val viewOnly = bundle.getBoolean("view_only", false)
            val viewMode = if (viewOnly) ServerProfile.VIEW_MODE_NO_INPUT else ServerProfile.VIEW_MODE_NORMAL

            result.add(ServerProfile(
                    name = name,
                    host = host,
                    port = port,
                    securityType = securityType,
                    channelType = channelType,
                    sshHost = sshHost,
                    sshPort = sshPort,
                    sshUsername = sshUsername,
                    sshAuthType = sshAuthType,
                    username = username,
                    viewMode = viewMode,
                    isManaged = true,
                    managedId = managedId,
            ))
        }
        return result
    }

    private fun mapSecurityType(value: Int): Int {
        return when (value) {
            0, 1, 2, 18, 19, 30 -> value
            else -> 0
        }
    }

    private fun mapChannelType(value: String): Int {
        return when (value) {
            "tcp" -> ServerProfile.CHANNEL_TCP
            "ssh-tunnel" -> ServerProfile.CHANNEL_SSH_TUNNEL
            else -> ServerProfile.CHANNEL_TCP
        }
    }

    private fun mapSshAuthType(value: String): Int {
        return when (value) {
            "key" -> ServerProfile.SSH_AUTH_KEY
            "password" -> ServerProfile.SSH_AUTH_PASSWORD
            else -> ServerProfile.SSH_AUTH_KEY
        }
    }
}
