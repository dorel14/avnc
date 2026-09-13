/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import androidx.room.testing.MigrationTestHelper
import com.gaurav.avnc.instrumentation
import com.gaurav.avnc.model.db.MainDb
import org.junit.Rule
import org.junit.Test

class ManagedMigrationTest {
    private val dbName = "Bond. James Bond."
    private val minVersion = 1
    private val maxVersion = MainDb.VERSION

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MainDb::class.java)

    @Test
    fun migrations() {
        for (i in minVersion until maxVersion)
            for (j in i + 1..maxVersion)
                runCatching {
                    helper.createDatabase(dbName, i).close()
                    helper.runMigrationsAndValidate(dbName, j, false).close()
                }.onFailure {
                    throw Exception("Failed to migrate MainDb from [$i] to [$j]", it)
                }
    }

    @Test
    fun migration7to8_addsManagedColumns() {
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL(
                "INSERT INTO profiles (name, host, port, username, password, securityType, channelType, " +
                "colorLevel, imageQuality, useRawEncoding, zoom1, zoom2, viewMode, useLocalCursor, " +
                "serverTypeHint, flags, gestureStyle, screenOrientation, useCount, useRepeater, " +
                "idOnRepeater, resizeRemoteDesktop, enableWol, wolMAC, wolBroadcastAddress, wolPort, " +
                "sshHost, sshPort, sshUsername, sshAuthType, sshPassword, sshPrivateKey) " +
                "VALUES ('Test', '192.168.1.1', 5900, 'user', 'pass', 0, 1, 7, 5, 0, 1.0, 1.0, 0, 1, " +
                "'', 0, 'auto', 'auto', 0, 0, 0, 0, 0, '', '', 9, " +
                "'', 22, '', 1, '', '')"
            )
            db.execSQL(
                "INSERT INTO profiles (name, host, port, username, password, securityType, channelType, " +
                "colorLevel, imageQuality, useRawEncoding, zoom1, zoom2, viewMode, useLocalCursor, " +
                "serverTypeHint, flags, gestureStyle, screenOrientation, useCount, useRepeater, " +
                "idOnRepeater, resizeRemoteDesktop, enableWol, wolMAC, wolBroadcastAddress, wolPort, " +
                "sshHost, sshPort, sshUsername, sshAuthType, sshPassword, sshPrivateKey) " +
                "VALUES ('Managed', '10.0.0.1', 5901, 'user', 'pass', 0, 1, 7, 5, 0, 1.0, 1.0, 0, 1, " +
                "'', 0, 'auto', 'auto', 0, 0, 0, 0, 0, '', '', 9, " +
                "'', 22, '', 1, '', '')"
            )
        }

        helper.runMigrationsAndValidate(dbName, 8, false).use { db ->
            val cursor = db.query("SELECT isManaged, managedId FROM profiles")
            val profiles = mutableListOf<Pair<Boolean, String?>>()
            while (cursor.moveToNext()) {
                val isManaged = cursor.getInt(0) == 1
                val managedId = cursor.getString(1)
                profiles.add(isManaged to managedId)
            }
            cursor.close()

            assert(profiles.size == 2) { "Expected 2 profiles, got ${profiles.size}" }
            assert(profiles[0] == (false to null)) { "Manual profile corrupted: ${profiles[0]}" }
            assert(profiles[1] == (false to null)) { "Managed profile should not have managedId set via raw insert: ${profiles[1]}" }
        }
    }
}
