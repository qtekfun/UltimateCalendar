// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.settings

import android.content.SharedPreferences

private val REMOVED = Any()

/** In-memory SharedPreferences that notifies listeners like the real one. */
internal class FakePreferences : SharedPreferences {
    val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = key in values
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners -= listener
    }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()

        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = this
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = REMOVED }
        override fun clear() = this
        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            changes.forEach { (key, value) ->
                if (value ===
                    REMOVED
                ) {
                    values -= key
                } else {
                    values[key] = value
                }
            }
            changes.keys.forEach { key ->
                listeners.forEach { it.onSharedPreferenceChanged(this@FakePreferences, key) }
            }
        }
    }
}
