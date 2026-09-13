/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.gaurav.avnc.util.AppPreferences
import androidx.preference.PreferenceManager
import androidx.room.withTransaction
import com.gaurav.avnc.R
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.LiveEvent
import com.gaurav.avnc.util.debugCheck
import com.gaurav.avnc.util.isTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Viewmodel for preferences activity.
 */
class PrefsViewModel(app: Application) : BaseViewModel(app) {

    private val appPrefs = AppPreferences(app)

    /**
     * Returns true if server profile management is locked by EMM.
     */
    val lockServers: LiveData<Boolean> = appPrefs.managedRestrictions.map { restrictions ->
        restrictions?.getBoolean("lock_servers", false) ?: false
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_RESPONSE_BYTES = 1024 * 1024
    }

    /**************************************************************************
     * Import/Export
     *
     * Importing/Exporting is done on a background thread.
     **************************************************************************/

    @Serializable
    private data class Container(
            val version: Int = 1,
            var profiles: List<ServerProfile>,
            var preferences: Map<String, JsonElement> = emptyMap()
    )

    private val serializer = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    val exportSettings = MutableLiveData(true)
    val exportProfiles = MutableLiveData(true)
    val exportSecrets = MutableLiveData(false)
    val deleteCurrentServerBeforeImport = MutableLiveData(false)

    val canExportSecrets: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(exportProfiles) { value = it && lockServers.value != true }
        addSource(lockServers) { value = exportProfiles.value == true && lockServers.value != true }
    }

    val canExport: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(exportSettings) { value = (it || exportProfiles.value == true) && lockServers.value != true }
        addSource(exportProfiles) { value = (exportSettings.value == true || it) && lockServers.value != true }
        addSource(lockServers) { value = (exportSettings.value == true || exportProfiles.value == true) && lockServers.value != true }
    }

    val importExportFinishedEvent = LiveEvent<Result<String>>()

    /**
     * Exports data to given [uri].
     */
    fun export(uri: Uri) {
        if (lockServers.value == true) {
            importExportFinishedEvent.fireAsync(Result.failure(
                IllegalStateException(app.getString(R.string.msg_settings_locked))
            ))
            return
        }
        val exportSettings = exportSettings.isTrue
        val exportProfiles = exportProfiles.isTrue
        val exportSecrets = exportSecrets.isTrue && exportProfiles
        debugCheck(exportSettings || exportProfiles)

        launchImportExport {
            // Serialize
            val data = Container(
                    profiles = if (exportProfiles) serverProfileDao.getList() else emptyList(),
                    preferences = if (exportSettings) collectPreferences() else emptyMap()
            )

            if (!exportSecrets)
                data.profiles = scrubSecrets(data.profiles)

            val json = exportJson()

            // Write out
            app.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.writer().use { it.write(json) }
            } ?: throw IOException("Unable to write the file.")

            return@launchImportExport app.getString(R.string.msg_exported)
        }
    }

    /**
     * Exports data as JSON and posts the result on [jsonDestination],
     * for export, e.g. via QR code.
     */
    fun export(jsonDestination : MutableLiveData<String>) {
        launchImportExport {
            val json = exportJson()
            jsonDestination.postValue(json)
            app.getString(R.string.msg_exported)
        }
    }

    /**
     * Imports data from given [uri].
     */
    fun import(uri: Uri) {
        if (lockServers.value == true) {
            importExportFinishedEvent.fireAsync(Result.failure(
                IllegalStateException(app.getString(R.string.msg_settings_locked))
            ))
            return
        }
        val deleteCurrentServers = deleteCurrentServerBeforeImport.isTrue

        launchImportExport {
            val json = when (uri.scheme) {
                "http", "https" -> readFromNetwork(uri)
                else -> app.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.reader().use { it.readText() }
                } ?: throw IOException("Unable to read the file.")
            }

            importJson(json)

            return@launchImportExport app.getString(R.string.msg_imported)
        }
    }

    /**
     * Reads the entire body of an http/https [uri] into a String.
     */
    private fun readFromNetwork(uri: Uri): String {
        val connection = (URI(uri.toString()).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299)
                throw IOException("Unable to read the file: HTTP $code")
            return connection.inputStream.use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val output = ByteArrayOutputStream()
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    if (output.size() + n > MAX_RESPONSE_BYTES)
                        throw IOException(app.getString(R.string.err_import_too_large))
                    output.write(buffer, 0, n)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Imports data from given JSON string (e.g. read off a QR code).
     */
    fun import(json: String) {
        launchImportExport {
            importJson(json)
            app.getString(R.string.msg_imported)
        }
    }

    /**
     * Runs an import/export operation on a background thread and fires
     * [importExportFinishedEvent] with its [Result].
     */
    private fun launchImportExport(block: suspend () -> String) {
        launchIO {
            runCatching {
                block()
            }.let {
                importExportFinishedEvent.fireAsync(it)
            }
        }
    }

    /**
     * Serializes current profiles or settings into a backup JSON string.
     */
    private suspend fun exportJson(): String {
        val exportSettings = exportSettings.isTrue
        val exportProfiles = exportProfiles.isTrue
        val exportSecrets = exportSecrets.isTrue && exportProfiles
        debugCheck(exportSettings || exportProfiles)

        // Serialize
        val data = Container(
                profiles = if (exportProfiles) serverProfileDao.getList() else emptyList(),
                preferences = if (exportSettings) collectPreferences() else emptyMap()
        )

        if (!exportSecrets)
            scrubSecrets(data.profiles)

        return serializer.encodeToString(data)
    }

    /**
     * Deserializes given backup JSON and updates the database.
     */
    private suspend fun importJson(json: String) {
        val deleteCurrentServers = deleteCurrentServerBeforeImport.isTrue

        // Deserialize
        val data = serializer.decodeFromString<Container>(json)

        //This is where migrations would be applied (if required in future)

        if (data.profiles.isNotEmpty()) {
            if (deleteCurrentServers) {
                db.withTransaction {
                    serverProfileDao.deleteAll()
                    serverProfileDao.save(data.profiles)
                }
            } else {
                //Reset IDs so that they don't conflict with saved profiles
                data.profiles.forEach { it.ID = 0 }
                serverProfileDao.save(data.profiles)
            }
        }

        // Replay app preferences (best-effort, unknown keys are ignored)
        applyPreferences(data.preferences)
    }

    /**
     * Reads all preferences as typed [JsonElement]s.
     */
    private fun collectPreferences(): Map<String, JsonElement> {
        val all = PreferenceManager.getDefaultSharedPreferences(app).all
        val map = mutableMapOf<String, JsonElement>()
        for ((key, value) in all) {
            if (key.startsWith("run_info_"))
                continue

            when (value) {
                null -> Log.w("PrefsViewModel", "Skipping null preference: $key")
                is String -> map[key] = JsonPrimitive(value)
                is Boolean -> map[key] = JsonPrimitive(value)
                is Int -> map[key] = JsonPrimitive(value)
                is Float -> map[key] = JsonPrimitive(value)
                is Long -> map[key] = JsonPrimitive(value)
                is Set<*> -> if (value.all { it is String })
                    map[key] = JsonArray(value.filterIsInstance<String>().map { JsonPrimitive(it) })
                else -> Log.w("PrefsViewModel", "Skipping unsupported preference type for key: $key")
            }
        }
        return map
    }

    /**
     * Imports preferences from JSON
     */
    private fun applyPreferences(prefs: Map<String, JsonElement>) {
        PreferenceManager.getDefaultSharedPreferences(app).edit {
            for ((key, element) in prefs) {
                when (element) {
                    is JsonPrimitive if element.isString ->
                        putString(key, element.content)
                    is JsonPrimitive -> {
                        val content = element.content
                        when {
                            content == "true" || content == "false" -> putBoolean(key, content.toBoolean())
                            content.contains('.') || content.contains('e') || content.contains('E') -> {
                                content.toFloatOrNull()?.let { putFloat(key, it) }
                                    ?: Log.w("PrefsViewModel", "Ignoring unknown preference: $key")
                            }
                            content.toIntOrNull() != null -> putInt(key, content.toInt())
                            content.toLongOrNull() != null -> putLong(key, content.toLong())
                            else -> Log.w("PrefsViewModel", "Ignoring unknown preference: $key")
                        }
                    }
                    is JsonArray -> putStringSet(key, element.mapNotNull { (it as? JsonPrimitive)?.content }.toSet())
                    else -> Log.w("PrefsViewModel", "Ignoring unknown preference: $key")
                }
            }
        }
    }

    private fun scrubSecrets(profiles: List<ServerProfile>): List<ServerProfile> =
        profiles.map {
            it.copy(password = "", sshPassword = "", sshPrivateKey = "")
        }
}
