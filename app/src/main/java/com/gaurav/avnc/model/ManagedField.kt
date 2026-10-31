/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import android.os.Bundle

/**
 * Optional fields of a managed [ServerProfile].
 *
 * If managed configuration sets a value for one of these, the field is enforced.
 * Otherwise, it is left to the user. Unset values are represented by blank strings
 * (default value of 'choice' restrictions), and 0 for integers.
 *
 * Fields which are always controlled by managed configuration are handled by
 * [ServerProfile.copyManagedFieldsFrom].
 */
enum class ManagedField(
        /** Key in managed configuration */
        val key: String,
        /** Reads value from [Bundle] & applies it to profile. Returns false if value is not set. */
        private val reader: ServerProfile.(Bundle) -> Boolean,
        /** Copies value of this field from given profile */
        private val copier: ServerProfile.(ServerProfile) -> Unit
) {
    GestureStyle("gesture_style",
                 { readChoice(it, "gesture_style", setOf("auto", "touchscreen", "touchpad")) { v -> gestureStyle = v } },
                 { gestureStyle = it.gestureStyle }),

    ScreenOrientation("screen_orientation",
                      { readChoice(it, "screen_orientation", setOf("auto", "portrait", "landscape")) { v -> screenOrientation = v } },
                      { screenOrientation = it.screenOrientation }),

    ImageQuality("image_quality",
                 { readChoice(it, "image_quality", (0..9).map { q -> "$q" }.toSet()) { v -> imageQuality = v.toInt() } },
                 { imageQuality = it.imageQuality }),

    RawEncoding("use_raw_encoding",
                { readBoolChoice(it, "use_raw_encoding") { v -> useRawEncoding = v } },
                { useRawEncoding = it.useRawEncoding }),

    ResizeRemoteDesktop("resize_remote_desktop",
                        { readBoolChoice(it, "resize_remote_desktop") { v -> resizeRemoteDesktop = v } },
                        { resizeRemoteDesktop = it.resizeRemoteDesktop }),

    ButtonUpDelay("button_up_delay",
                  { readBoolChoice(it, "button_up_delay") { v -> fButtonUpDelay = v } },
                  { fButtonUpDelay = it.fButtonUpDelay }),

    ConnectOnAppStart("connect_on_app_start",
                      { readBoolChoice(it, "connect_on_app_start") { v -> fConnectOnAppStart = v } },
                      { fConnectOnAppStart = it.fConnectOnAppStart }),

    EnableWol("enable_wol",
              { readBoolChoice(it, "enable_wol") { v -> enableWol = v } },
              { enableWol = it.enableWol }),

    WolMAC("wol_mac",
           { readString(it, "wol_mac") { v -> wolMAC = v } },
           { wolMAC = it.wolMAC }),

    WolBroadcastAddress("wol_broadcast_address",
                        { readString(it, "wol_broadcast_address") { v -> wolBroadcastAddress = v } },
                        { wolBroadcastAddress = it.wolBroadcastAddress }),

    WolPort("wol_port",
            { readPort(it, "wol_port") { v -> wolPort = v } },
            { wolPort = it.wolPort });

    fun readInto(profile: ServerProfile, bundle: Bundle) = reader(profile, bundle)
    fun copy(to: ServerProfile, from: ServerProfile) = copier(to, from)

    companion object {
        fun fromKey(key: String) = entries.find { it.key == key }
    }
}

private inline fun readChoice(bundle: Bundle, key: String, allowed: Set<String>, apply: (String) -> Unit): Boolean {
    val value = bundle.getString(key)?.takeIf { it in allowed } ?: return false
    apply(value)
    return true
}

private inline fun readBoolChoice(bundle: Bundle, key: String, apply: (Boolean) -> Unit) =
        readChoice(bundle, key, setOf("true", "false")) { apply(it.toBoolean()) }

private inline fun readString(bundle: Bundle, key: String, apply: (String) -> Unit): Boolean {
    val value = bundle.getString(key)?.takeIf { it.isNotBlank() } ?: return false
    apply(value)
    return true
}

private inline fun readPort(bundle: Bundle, key: String, apply: (Int) -> Unit): Boolean {
    val value = bundle.getInt(key, 0).takeIf { it in 1..65535 } ?: return false
    apply(value)
    return true
}
