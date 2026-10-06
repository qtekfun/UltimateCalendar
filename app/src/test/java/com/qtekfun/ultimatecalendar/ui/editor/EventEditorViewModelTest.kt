// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.editor

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.qtekfun.ultimatecalendar.data.calendar.CalendarRepository
import com.qtekfun.ultimatecalendar.data.calendar.EventSaver
import com.qtekfun.ultimatecalendar.data.local.UltimateCalendarDatabase
import com.qtekfun.ultimatecalendar.data.local.inMemoryDatabase
import com.qtekfun.ultimatecalendar.data.settings.AppSettings
import com.qtekfun.ultimatecalendar.data.settings.SettingsRepository
import com.qtekfun.ultimatecalendar.data.source.CalendarSource
import com.qtekfun.ultimatecalendar.data.source.FakeCalendarSource
import com.qtekfun.ultimatecalendar.domain.detail.EventRef
import com.qtekfun.ultimatecalendar.domain.editor.ContactSuggestion
import com.qtekfun.ultimatecalendar.domain.editor.ContactSuggestions
import com.qtekfun.ultimatecalendar.domain.editor.EditorRequest
import com.qtekfun.ultimatecalendar.domain.editor.FormIssue
import com.qtekfun.ultimatecalendar.domain.editor.RepeatSetting
import com.qtekfun.ultimatecalendar.domain.editor.withEndTime
import com.qtekfun.ultimatecalendar.domain.editor.withStartTime
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccess
import com.qtekfun.ultimatecalendar.domain.model.CalendarAccount
import com.qtekfun.ultimatecalendar.domain.model.CalendarId
import com.qtekfun.ultimatecalendar.domain.model.CalendarInfo
import com.qtekfun.ultimatecalendar.domain.model.Event
import com.qtekfun.ultimatecalendar.domain.model.EventDraft
import com.qtekfun.ultimatecalendar.domain.model.EventId
import com.qtekfun.ultimatecalendar.domain.model.EventTime
import com.qtekfun.ultimatecalendar.domain.model.TimeRange
import com.qtekfun.ultimatecalendar.domain.recurrence.RecurrenceScope
import com.qtekfun.ultimatecalendar.domain.result.CalendarError
import com.qtekfun.ultimatecalendar.domain.result.CalendarResult
import com.qtekfun.ultimatecalendar.notify.SystemZone
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditorViewModelTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    // 10:07:30 in Madrid on 2026-03-10.
    private val clock = Clock.fixed(Instant.parse("2026-03-10T09:07:30Z"), ZoneId.of("UTC"))
    private val google = CalendarAccount("me@example.com", "com.google")
    private val local = CalendarAccount("Phone", "LOCAL")
    private val work = calendar(1, "Work", google)
    private val phone = calendar(2, "Personal", local)

    private lateinit var database: UltimateCalendarDatabase
    private lateinit var source: FakeCalendarSource
    private var settings = AppSettings()
    private var contactsGranted = false
    private var contactList = emptyList<ContactSuggestion>()
    private val main = UnconfinedTestDispatcher()
    private val march = TimeRange(
        Instant.parse("2026-03-01T00:00:00Z"),
        Instant.parse("2026-04-01T00:00:00Z")
    )

    @BeforeEach
    fun open() {
        Dispatchers.setMain(main)
        database = inMemoryDatabase()
        source = FakeCalendarSource(listOf(work, phone))
    }

    @AfterEach
    fun close() {
        main.scheduler.advanceUntilIdle()
        database.close()
    }

    private fun calendar(
        id: Long,
        name: String,
        account: CalendarAccount,
        access: CalendarAccess = CalendarAccess.OWNER
    ) = CalendarInfo(
        CalendarId(id),
        account,
        name,
        0xFF0B63CE.toInt(),
        access,
        ownerEmail = "me@example.com"
    )

    private val contacts = object : ContactSuggestions {
        override fun isAvailable() = contactsGranted

        override suspend fun find(query: String) = if (contactsGranted) contactList else emptyList()
    }

    private fun viewModel(over: CalendarSource = source): EventEditorViewModel {
        val repository =
            CalendarRepository(over, database.calendarSettingsDao(), Dispatchers.Unconfined)
        val prefs = mockk<SettingsRepository>()
        every { prefs.current() } answers { settings }
        val loader = EditorLoader(repository, over, prefs, contacts, clock, SystemZone { madrid })
        return EventEditorViewModel(loader, EventSaver(over), contacts)
    }

    private suspend fun EventEditorViewModel.opened(request: EditorRequest): EditorUiState {
        start(request)
        return await { it !is EditorUiState.Loading }
    }

    private suspend fun EventEditorViewModel.await(
        matches: (EditorUiState) -> Boolean
    ): EditorUiState {
        var found: EditorUiState? = null
        state.test {
            while (found == null) {
                val next = awaitItem()
                if (matches(next)) found = next
            }
            cancelAndIgnoreRemainingEvents()
        }
        return requireNotNull(found)
    }

    private fun EventEditorViewModel.ready() = state.value as EditorUiState.Ready

    private suspend fun storeSeries(
        calendar: CalendarInfo = work,
        rrule: String? = "FREQ=DAILY;COUNT=5"
    ): EventRef {
        val start = Instant.parse("2026-03-10T09:00:00Z")
        val id = (
            source.create(
                EventDraft(
                    calendar.id,
                    "Standup",
                    EventTime.Timed(start, start.plusSeconds(3600), madrid),
                    rrule = rrule
                )
            ) as CalendarResult.Success
            ).value
        return EventRef(id, start.toEpochMilli(), start.plusSeconds(3600).toEpochMilli(), false)
    }

    @Test
    fun `a new event opens at the next half hour in the default calendar`() = runTest {
        val model = viewModel()

        val state = model.opened(EditorRequest.New()) as EditorUiState.Ready

        assertTrue(state.isNew)
        assertEquals(LocalDateTime.parse("2026-03-10T10:30"), state.form.start.toLocalDateTime())
        assertEquals(LocalDateTime.parse("2026-03-10T11:30"), state.form.end.toLocalDateTime())
        assertEquals(work.id, state.form.calendarId)
        assertEquals(listOf(work, phone), state.calendars)
        assertFalse(state.dirty)
        assertEquals("me@example.com", state.organizer)
    }

    @Test
    fun `a tapped slot and the settings shape the new event`() = runTest {
        settings = AppSettings(
            defaultCalendar = phone.id,
            defaultDurationMinutes = 30,
            defaultReminders = listOf(5, 15)
        )
        val model = viewModel()

        val state = model.opened(
            EditorRequest.New(LocalDateTime.parse("2026-04-02T14:00"))
        ) as EditorUiState.Ready

        assertEquals(phone.id, state.form.calendarId)
        assertEquals(LocalDateTime.parse("2026-04-02T14:30"), state.form.end.toLocalDateTime())
        assertEquals(listOf(5, 15), state.form.reminders.map { it.minutesBefore })
    }

    @Test
    fun `read-only calendars are not offered`() = runTest {
        source.addCalendar(calendar(3, "Holidays", google, CalendarAccess.READ))

        val state = viewModel().opened(EditorRequest.New()) as EditorUiState.Ready

        assertEquals(listOf(work, phone), state.calendars)
    }

    @Test
    fun `with no calendar to write to the editor says so`() = runTest {
        val bare = FakeCalendarSource(listOf(calendar(3, "Holidays", google, CalendarAccess.READ)))

        val state = viewModel(bare).opened(EditorRequest.New())

        assertEquals(EditorUiState.Failed(LoadFailure.NO_CALENDAR), state)
    }

    @Test
    fun `an event that is gone cannot be edited`() = runTest {
        val gone = EventRef(EventId(404), 0, 1000, false)

        val state = viewModel().opened(EditorRequest.Edit(gone))

        assertEquals(EditorUiState.Failed(LoadFailure.NOT_FOUND), state)
    }

    @Test
    fun `an event in a calendar that cannot be changed cannot be edited`() = runTest {
        val contribute = calendar(4, "Shared", google, CalendarAccess.CONTRIBUTE)
        source.addCalendar(contribute)
        val ref = storeSeries(contribute, rrule = null)

        val state = viewModel().opened(EditorRequest.Edit(ref))

        assertEquals(EditorUiState.Failed(LoadFailure.READ_ONLY), state)
    }

    @Test
    fun `failing to read the calendars is reported`() = runTest {
        val broken = object : CalendarSource by source {
            override suspend fun calendars(): CalendarResult<List<CalendarInfo>> =
                CalendarResult.Failure(CalendarError.PermissionDenied)
        }

        val state = viewModel(broken).opened(EditorRequest.New())

        assertEquals(EditorUiState.Failed(LoadFailure.PERMISSION_DENIED), state)
    }

    @Test
    fun `editing an event opens its fields and a change makes it dirty`() = runTest {
        val ref = storeSeries(rrule = null)
        val model = viewModel()

        val state = model.opened(EditorRequest.Edit(ref)) as EditorUiState.Ready

        assertFalse(state.isNew)
        assertEquals("Standup", state.form.title)
        assertFalse(state.dirty)
        assertFalse(state.canSave)
        model.edit { it.copy(title = "Standup!") }
        assertTrue(model.ready().dirty)
        assertTrue(model.ready().canSave)
    }

    @Test
    fun `leaving with nothing changed closes at once`() = runTest {
        val model = viewModel()
        model.opened(EditorRequest.New())

        model.leave()

        assertEquals(EditorUiState.Closed, model.state.value)
    }

    @Test
    fun `leaving with changes asks, and discarding or keeping answers`() = runTest {
        val model = viewModel()
        model.opened(EditorRequest.New())
        model.edit { it.copy(title = "Draft") }

        model.leave()
        assertEquals(EditorPrompt.DISCARD, model.ready().prompt)

        model.dismiss()
        assertNull(model.ready().prompt)
        assertEquals("Draft", model.ready().form.title)

        model.leave(discard = true)
        assertEquals(EditorUiState.Closed, model.state.value)
    }

    @Test
    fun `changes undone are not changes`() = runTest {
        val model = viewModel()
        model.opened(EditorRequest.New())

        model.edit { it.copy(title = "Draft") }
        model.edit { it.copy(title = "") }
        model.leave()

        assertEquals(EditorUiState.Closed, model.state.value)
    }

    @Test
    fun `leaving while there is no form just closes`() = runTest {
        val model = viewModel()

        model.leave()

        assertEquals(EditorUiState.Closed, model.state.value)
    }

    @Test
    fun `saving a new event stores it and closes`() = runTest {
        val model = viewModel()
        model.opened(EditorRequest.New())
        model.edit { it.copy(title = "Lunch", location = " Cafe ") }

        model.save()

        assertEquals(EditorUiState.Closed, model.state.value)
        val found = (source.instances(march) as CalendarResult.Success).value
        assertEquals(listOf("Lunch"), found.map { it.title })
        assertEquals("Cafe", found.single().location)
    }

    @Test
    fun `a form with a problem cannot be saved`() = runTest {
        val model = viewModel()
        model.opened(EditorRequest.New())
        model.edit { it.withEndTime(LocalTime.of(9, 0)) }

        model.save()

        val state = model.ready()
        assertEquals(setOf(FormIssue.END_BEFORE_START), state.issues)
        assertFalse(state.canSave)
        assertFalse(state.saving)
        assertTrue((source.instances(march) as CalendarResult.Success).value.isEmpty())
    }

    @Test
    fun `a failed save keeps everything the user typed and says why`() = runTest {
        val down = object : CalendarSource by source {
            override suspend fun create(draft: EventDraft): CalendarResult<EventId> =
                CalendarResult.Failure(CalendarError.SourceFailure("down"))
        }
        val model = viewModel(down)
        model.opened(EditorRequest.New())
        model.edit { it.copy(title = "Lunch").withStartTime(LocalTime.of(12, 0)) }
        val typed = model.ready().form

        model.save()

        val state = model.ready()
        assertEquals(CalendarError.SourceFailure("down"), state.saveError)
        assertFalse(state.saving)
        assertEquals(typed, state.form)
        assertTrue(state.canSave)

        model.dismiss()
        assertNull(model.ready().saveError)
        assertEquals(typed, model.ready().form)
    }

    @Test
    fun `the saving flag is up while the source works and down after`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : CalendarSource by source {
            override suspend fun create(draft: EventDraft): CalendarResult<EventId> {
                gate.await()
                return source.create(draft)
            }
        }
        val model = viewModel(slow)
        model.opened(EditorRequest.New())
        model.edit { it.copy(title = "Lunch") }

        model.state.test {
            awaitItem()
            model.save()
            assertTrue((awaitItem() as EditorUiState.Ready).saving)
            // Neither a second press nor back does anything while the source is working.
            model.save()
            model.leave()
            expectNoEvents()
            gate.complete(Unit)
            assertEquals(EditorUiState.Closed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving a repeating event asks which occurrences first`() = runTest {
        val ref = storeSeries()
        val model = viewModel()
        model.opened(EditorRequest.Edit(ref))
        model.edit { it.copy(title = "Retro") }

        model.save()

        assertEquals(EditorPrompt.SCOPE, model.ready().prompt)
        assertEquals(RecurrenceScope.entries, model.ready().scopes)
        assertEquals("Standup", (source.event(ref.eventId) as CalendarResult.Success).value.title)
    }

    @Test
    fun `each answer applies to its own part of the series`() = runTest {
        RecurrenceScope.entries.forEach { scope ->
            val fresh = FakeCalendarSource(listOf(work, phone))
            source = fresh
            val ref = storeSeries()
            val second = ref.copy(
                startMillis = ref.startMillis + 86_400_000,
                endMillis = ref.endMillis + 86_400_000
            )
            val model = viewModel()
            model.opened(EditorRequest.Edit(second))
            model.edit { it.copy(title = "Retro") }

            model.save()
            model.save(scope)

            assertEquals(EditorUiState.Closed, model.state.value, scope.name)
            val titles = (fresh.instances(march) as CalendarResult.Success).value
                .map { it.title }
            val expected = when (scope) {
                RecurrenceScope.THIS -> listOf("Standup", "Retro", "Standup", "Standup", "Standup")

                RecurrenceScope.THIS_AND_FOLLOWING -> listOf(
                    "Standup",
                    "Retro",
                    "Retro",
                    "Retro",
                    "Retro"
                )

                RecurrenceScope.ALL -> List(5) { "Retro" }
            }
            assertEquals(expected, titles, scope.name)
        }
    }

    @Test
    fun `in a local calendar only this event is not offered and not accepted`() = runTest {
        val ref = storeSeries(phone)
        val model = viewModel()
        model.opened(EditorRequest.Edit(ref))
        model.edit { it.copy(title = "Retro") }
        model.save()

        assertEquals(
            listOf(RecurrenceScope.THIS_AND_FOLLOWING, RecurrenceScope.ALL),
            model.ready().scopes
        )
        val asked = model.state.value
        model.save(RecurrenceScope.THIS)

        assertEquals(asked, model.state.value)
    }

    @Test
    fun `a single event is saved without asking`() = runTest {
        val ref = storeSeries(rrule = null)
        val model = viewModel()
        model.opened(EditorRequest.Edit(ref))
        model.edit { it.copy(title = "Brunch") }

        model.save()

        assertEquals(EditorUiState.Closed, model.state.value)
        assertEquals("Brunch", (source.event(ref.eventId) as CalendarResult.Success).value.title)
    }

    @Test
    fun `making an event repeat is part of the form`() = runTest {
        val model = viewModel()
        model.opened(EditorRequest.New())

        model.edit {
            it.copy(
                title = "Gym",
                repeat = RepeatSetting.Custom(
                    com.qtekfun.ultimatecalendar.domain.recurrence.CustomRepeat(
                        end = com.qtekfun.ultimatecalendar.domain.recurrence.RepeatEnd.AFTER_COUNT,
                        count = 3
                    )
                )
            )
        }
        model.save()

        val found = (source.instances(march) as CalendarResult.Success).value
        assertEquals(3, found.size)
        assertTrue(found.all { it.isRecurring })
    }

    @Test
    fun `typed addresses become guests and a bad one is flagged`() = runTest {
        val model = viewModel()
        model.opened(EditorRequest.New())

        assertTrue(model.addGuests("ana@x.org, bob@x.org"))
        assertEquals(listOf("ana@x.org", "bob@x.org"), model.ready().form.guests.map { it.email })

        assertFalse(model.addGuests("carol"))
        assertEquals("carol", model.ready().invalidGuest)
        assertEquals(2, model.ready().form.guests.size)

        model.dismiss()
        assertNull(model.ready().invalidGuest)
    }

    @Test
    fun `adding guests with no editor open does nothing`() = runTest {
        assertFalse(viewModel().addGuests("ana@x.org"))
    }

    @Test
    fun `contact suggestions only come once the permission is granted`() = runTest {
        contactList = listOf(ContactSuggestion("Ana", "ana@x.org"))
        val model = viewModel()
        model.opened(EditorRequest.New())
        assertFalse(model.ready().contactsAvailable)

        model.suggestGuests("an")
        assertEquals(emptyList<ContactSuggestion>(), model.ready().contacts)

        contactsGranted = true
        model.suggestGuests("an")

        assertTrue(model.ready().contactsAvailable)
        assertEquals(contactList, model.ready().contacts)

        assertTrue(model.addGuests("ana@x.org"))
        assertEquals(emptyList<ContactSuggestion>(), model.ready().contacts)
    }

    @Test
    fun `starting the same request again keeps what was typed`() = runTest {
        val model = viewModel()
        model.opened(EditorRequest.New())
        model.edit { it.copy(title = "Typed") }

        model.start(EditorRequest.New())

        assertEquals("Typed", model.ready().form.title)
    }

    @Test
    fun `a session is forgotten on reset and a new one starts clean`() = runTest {
        val model = viewModel()
        model.opened(EditorRequest.New())
        model.edit { it.copy(title = "Typed") }

        model.reset()
        assertEquals(EditorUiState.Loading, model.state.value)
        val state = model.opened(EditorRequest.New()) as EditorUiState.Ready

        assertEquals("", state.form.title)
    }

    @Test
    fun `edits with no editor open are ignored`() = runTest {
        val model = viewModel()

        model.edit { it.copy(title = "x") }
        model.dismiss()
        model.save()
        model.save(RecurrenceScope.ALL)

        assertEquals(EditorUiState.Loading, model.state.value)
        assertInstanceOf(EditorUiState.Loading::class.java, model.state.value)
    }
}
