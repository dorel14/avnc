/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.gaurav.avnc.model.ServerProfile

@Dao
interface ServerProfileDao {

    @Query("SELECT * FROM profiles")
    fun getLiveList(): LiveData<List<ServerProfile>>

    @Query("SELECT * FROM profiles ORDER BY name COLLATE NOCASE")
    fun getSortedLiveList(): LiveData<List<ServerProfile>>

    //Synchronous version
    @Query("SELECT * FROM profiles")
    suspend fun getList(): List<ServerProfile>

    @Query("SELECT * FROM profiles WHERE ID = :id")
    suspend fun getByID(id: Long): ServerProfile?

    @Query("SELECT * FROM profiles WHERE name = :name")
    suspend fun getByName(name: String): List<ServerProfile>

    @Query("SELECT * FROM profiles WHERE flags & ${ServerProfile.FLAG_CONNECT_ON_APP_START} != 0")
    suspend fun getConnectableOnAppStart(): List<ServerProfile>

    @Query("SELECT * FROM profiles WHERE name LIKE :query OR host LIKE :query OR sshHost LIKE :query ORDER BY useCount DESC")
    fun search(query: String): LiveData<List<ServerProfile>>

    /**
     * Adds given [profile] to database, and returns its ID.
     * If a profile with given ID already exits, updates it, and returns -1.
     */
    @Upsert
    suspend fun save(profile: ServerProfile): Long

    @Upsert
    suspend fun save(profiles: List<ServerProfile>)

    /**
     * [save] can be used for this, but [update] makes the intent more clear.
     */
    @Update
    suspend fun update(profile: ServerProfile)

    @Delete
    suspend fun delete(profile: ServerProfile)

    @Query("SELECT * FROM profiles WHERE isManaged = 1")
    suspend fun getManagedProfiles(): List<ServerProfile>

    @Transaction
    suspend fun replaceManagedProfiles(profiles: List<ServerProfile>) {
        val existing = getManagedProfiles()
        val existingByManagedId = existing.associateBy { it.managedId }

        val incomingManagedIds = profiles.mapNotNull { it.managedId }.toSet()

        // Delete managed profiles no longer in EMM list
        for (p in existing) {
            if (p.managedId !in incomingManagedIds) {
                delete(p)
            }
        }

        // Insert or update incoming profiles
        for (p in profiles) {
            val current = existingByManagedId[p.managedId]
            if (current != null) {
                // Preserve local state, update EMM-controlled fields
                p.ID = current.ID
                p.isManaged = current.isManaged
                p.managedId = current.managedId
                p.useCount = current.useCount
                p.sshPrivateKey = current.sshPrivateKey
                update(p)
            } else {
                save(p)
            }
        }
    }

    @Query("UPDATE profiles SET useCount = :useCount, zoom1 = :zoom1, zoom2 = :zoom2 WHERE ID = :id")
    suspend fun saveManagedRuntimeState(id: Long, useCount: Int, zoom1: Float, zoom2: Float)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}