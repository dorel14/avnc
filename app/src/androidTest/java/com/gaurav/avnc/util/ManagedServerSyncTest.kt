/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.os.Bundle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.model.ManagedField
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.targetContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagedServerSyncTest {

    private val db = Room.inMemoryDatabaseBuilder(targetContext, MainDb::class.java).build()
    private val dao = db.serverProfileDao

    @After
    fun tearDown() = db.close()

    private fun server(id: String?, host: String?, configure: Bundle.() -> Unit = {}) = Bundle().apply {
        putString("id", id)
        putString("host", host)
        configure()
    }

    // Same format as delivered by RestrictionsManager for bundle_array
    private fun restrictions(vararg servers: Bundle) = Bundle().apply {
        putParcelableArray("managed_servers", servers)
    }

    @Test
    fun parseFields() {
        val profile = ManagedServerSync.parse(restrictions(server("a", "10.0.0.1") {
            putString("name", "Office")
            putInt("port", 5901)
            putString("security_type", "2")
            putString("channel_type", "ssh-tunnel")
            putString("ssh_auth_type", "key")
            putBoolean("view_only", true)
        })).single()

        assertTrue(profile.isManaged)
        assertEquals("a", profile.managedId)
        assertEquals("Office", profile.name)
        assertEquals("10.0.0.1", profile.host)
        assertEquals(5901, profile.port)
        assertEquals(2, profile.securityType)
        assertEquals(ServerProfile.CHANNEL_SSH_TUNNEL, profile.channelType)
        assertEquals(ServerProfile.SSH_AUTH_KEY, profile.sshAuthType)
        assertEquals(ServerProfile.VIEW_MODE_NO_INPUT, profile.viewMode)
    }

    @Test
    fun parseDefaults() {
        val profile = ManagedServerSync.parse(restrictions(server("a", "host") {
            putInt("port", 70000)
            putString("security_type", "42")
        })).single()

        assertEquals("host", profile.name)
        assertEquals(5900, profile.port)
        assertEquals(0, profile.securityType)
        assertEquals(ServerProfile.CHANNEL_TCP, profile.channelType)
        assertEquals(ServerProfile.SSH_AUTH_PASSWORD, profile.sshAuthType)
        assertEquals(ServerProfile.VIEW_MODE_NORMAL, profile.viewMode)
    }

    @Test
    fun parseSkipsInvalidEntries() {
        val profiles = ManagedServerSync.parse(restrictions(
                server(null, "host"),
                server("a", ""),
                server("b", "host1"),
                server("b", "host2"),
        ))
        assertEquals(listOf("host1"), profiles.map { it.host })
    }

    @Test
    fun parseOptionalFields() {
        // Optional fields are grouped, as in the schema
        val profile = ManagedServerSync.parse(restrictions(server("a", "host") {
            putBundle("advanced", Bundle().apply {
                putString("gesture_style", "touchpad")
                putString("image_quality", "8")
                putString("enable_wol", "true")
                putString("screen_orientation", "")    // Defined by user
                putString("use_raw_encoding", "maybe") // Invalid
                putInt("wol_port", 0)                  // Defined by user
            })
        })).single()

        assertEquals("touchpad", profile.gestureStyle)
        assertEquals(8, profile.imageQuality)
        assertTrue(profile.enableWol)
        assertTrue(profile.isEnforced(ManagedField.GestureStyle))
        assertTrue(profile.isEnforced(ManagedField.ImageQuality))
        assertTrue(profile.isEnforced(ManagedField.EnableWol))
        assertFalse(profile.isEnforced(ManagedField.ScreenOrientation))
        assertFalse(profile.isEnforced(ManagedField.RawEncoding))
        assertFalse(profile.isEnforced(ManagedField.WolPort))
    }

    @Test
    fun enforcedOptionalFieldsAreKept() = runBlocking {
        dao.syncManagedProfiles(ManagedServerSync.parse(restrictions(server("a", "host") {
            putString("gesture_style", "touchpad")
        })))
        val stored = dao.getManagedList().single()

        dao.updateLocalState(stored.copy(gestureStyle = "touchscreen", screenOrientation = "landscape"))

        val updated = dao.getByID(stored.ID)!!
        assertEquals("touchpad", updated.gestureStyle)     // Enforced
        assertEquals("landscape", updated.screenOrientation) // Defined by user
    }

    @Test
    fun parseMissingRestriction() {
        assertTrue(ManagedServerSync.parse(Bundle()).isEmpty())
    }

    @Test
    fun syncInsertsUpdatesAndDeletes() = runBlocking {
        val manual = ServerProfile(name = "Manual", host = "manual")
        dao.save(manual)

        dao.syncManagedProfiles(ManagedServerSync.parse(restrictions(server("a", "host-a"), server("b", "host-b"))))
        val a = dao.getManagedList().first { it.managedId == "a" }

        // Local state of a managed profile
        dao.updateLocalState(a.copy(zoom1 = 2f, password = "secret", host = "changed-locally"))

        dao.syncManagedProfiles(ManagedServerSync.parse(restrictions(server("a", "new-host-a"))))

        val managed = dao.getManagedList()
        assertEquals(listOf("a"), managed.map { it.managedId })
        assertEquals(a.ID, managed[0].ID)
        assertEquals("new-host-a", managed[0].host)
        assertEquals(2f, managed[0].zoom1)
        assertEquals("secret", managed[0].password)

        // Manual profiles are never touched
        assertEquals(listOf("manual"), dao.getList().filterNot { it.isManaged }.map { it.host })
    }

    @Test
    fun updateLocalStateKeepsManagedFields() = runBlocking {
        dao.syncManagedProfiles(ManagedServerSync.parse(restrictions(server("a", "host-a"))))
        val stored = dao.getManagedList().single()

        dao.updateLocalState(stored.copy(host = "other", username = "other", useCount = 5))

        val updated = dao.getByID(stored.ID)!!
        assertEquals("host-a", updated.host)
        assertEquals("", updated.username)
        assertEquals(5, updated.useCount)
        assertFalse(updated.managedId.isNullOrEmpty())
    }
}
