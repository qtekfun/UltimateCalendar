// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.sync.queue

import com.qtekfun.ultimatecalendar.data.local.StoredKey
import com.qtekfun.ultimatecalendar.data.local.model.OperationType
import com.qtekfun.ultimatecalendar.domain.model.AttendeeStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A local change waiting for the server, stored as the JSON payload of a pending operation.
 * Everything but [DeleteEvent] sends the event as it is when the operation runs, so repeating
 * one is harmless and a change already covered by another one waiting needs no operation of its
 * own (see [OperationQueue.enqueue]).
 */
@Serializable
sealed interface QueuedOperation {
    val type: OperationType

    /**
     * What a newer operation of the same kind replaces while neither was sent, or null when the
     * operation is not replaced by another one.
     */
    val slot: String? get() = null

    /** PUT with `If-None-Match: *` to the href the app chose: a lost answer is detectable (412). */
    @Serializable
    @SerialName("create_event")
    data object CreateEvent : QueuedOperation {
        override val type get() = OperationType.CREATE
    }

    @Serializable
    @SerialName("update_event")
    data object UpdateEvent : QueuedOperation {
        override val type get() = OperationType.UPDATE
    }

    /** Carries the href, since the event itself may be gone locally by then. */
    @Serializable
    @SerialName("delete_event")
    data class DeleteEvent(val href: String) : QueuedOperation {
        override val type get() = OperationType.DELETE
    }

    /**
     * The user's own answer to an invitation, to the whole series (a null [occurrence]) or to the one [occurrence].
     * The answer is applied again to the event before every upload, so that a change made
     * on the server meanwhile never overwrites it.
     */
    @Serializable
    @SerialName("respond")
    data class Respond(val email: String, val status: AttendeeStatus, val occurrence: StoredKey?) :
        QueuedOperation {
        override val type get() = OperationType.RESPOND

        override val slot get() = "respond:$email:${occurrence?.let { slotOf(it) }}"
    }

    /** One occurrence of a series was changed (its `RECURRENCE-ID` override was written). */
    @Serializable
    @SerialName("edit_instance")
    data class EditInstance(val occurrence: StoredKey) : QueuedOperation {
        override val type get() = OperationType.EDIT_INSTANCE

        override val slot get() = "instance:${slotOf(occurrence)}"
    }

    /**
     * One occurrence of a series was cancelled. Applied again to the event before every upload,
     * so an edit of that occurrence made on the server meanwhile does not bring it back.
     */
    @Serializable
    @SerialName("cancel_instance")
    data class CancelInstance(val occurrence: StoredKey) : QueuedOperation {
        override val type get() = OperationType.CANCEL_INSTANCE

        override val slot get() = "instance:${slotOf(occurrence)}"
    }

    companion object {
        /** Payload JSON; the class name goes in "op" to keep it apart from the fields. */
        val json: Json = Json {
            classDiscriminator = "op"
            ignoreUnknownKeys = true
        }

        fun encode(operation: QueuedOperation): String =
            json.encodeToString(serializer(), operation)

        fun decode(payload: String): QueuedOperation = json.decodeFromString(serializer(), payload)

        private fun slotOf(key: StoredKey) = key.day ?: key.at.toString()
    }
}
