// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [ContactsGateway] over the contacts provider. It reads names and addresses to suggest them in
 * the guests field, only while `READ_CONTACTS` is granted, and keeps nothing: no copy, no log.
 */
class ContentResolverContactsGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : ContactsGateway {
    override fun isGranted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    override fun search(query: String, limit: Int): List<ContactRow> {
        val uri = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(Email.DISPLAY_NAME_PRIMARY, Email.ADDRESS)
        val rows = mutableListOf<ContactRow>()
        // A revoked permission or a missing provider is not an error here: no suggestions.
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                while (rows.size < limit && cursor.moveToNext()) {
                    rows += ContactRow(cursor.getString(0), cursor.getString(1))
                }
            }
        }
        return rows
    }
}
