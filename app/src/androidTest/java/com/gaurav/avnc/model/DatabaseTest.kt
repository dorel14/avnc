/*
 * Copyright (c) 2023  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import androidx.room.testing.MigrationTestHelper
import com.gaurav.avnc.instrumentation
import com.gaurav.avnc.model.db.MainDb
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DatabaseTest {
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
                    helper.runMigrationsAndValidate(dbName, j, false, *MainDb.MANUAL_MIGRATIONS).close()
                }.onFailure {
                    throw Exception("Failed to migrate MainDb from [$i] to [$j]", it)
                }
    }

    @Test
    fun existingProfilesAreNotManagedAfterMigration7to8() {
        helper.createDatabase(dbName, 7).use {
            it.execSQL("INSERT INTO profiles (name, host, port, username, password, securityType, channelType, " +
                       "colorLevel, imageQuality, useRawEncoding, zoom1, zoom2, viewMode, useLocalCursor, " +
                       "serverTypeHint, flags, gestureStyle, screenOrientation, useCount, useRepeater, " +
                       "idOnRepeater, resizeRemoteDesktop, enableWol, wolMAC, sshHost, sshPort, sshUsername, " +
                       "sshAuthType, sshPassword, sshPrivateKey) " +
                       "VALUES ('Test', '10.0.0.1', 5900, '', '', 0, 1, 7, 5, 0, 1.0, 1.0, 0, 1, " +
                       "'', 0, 'auto', 'auto', 0, 0, 0, 0, 0, '', '', 22, '', 1, '', '')")
        }

        helper.runMigrationsAndValidate(dbName, 8, false, *MainDb.MANUAL_MIGRATIONS).use { db ->
            db.query("SELECT isManaged, managedId FROM profiles").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
                assertEquals(true, cursor.isNull(1))
            }
        }
    }
}
