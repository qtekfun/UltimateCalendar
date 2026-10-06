// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatecalendar.R
import com.qtekfun.ultimatecalendar.domain.model.EventInstance
import com.qtekfun.ultimatecalendar.domain.search.SearchResult
import com.qtekfun.ultimatecalendar.ui.components.EmptyState
import com.qtekfun.ultimatecalendar.ui.components.EventListSkeleton
import com.qtekfun.ultimatecalendar.ui.components.SectionHeader
import com.qtekfun.ultimatecalendar.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Search (RF-09), fed by [SearchViewModel]; a tap on a result opens that event. */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenEvent: (EventInstance) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The ViewModel outlives the screen while an event's detail covers it.
    var text by rememberSaveable { mutableStateOf(viewModel.currentQuery) }
    // After a restart of the process the text comes back but the ViewModel is new.
    LaunchedEffect(Unit) { if (text != viewModel.currentQuery) viewModel.onQuery(text) }
    val callbacks = SearchCallbacks(
        onBack = {
            // Leaving for good: the next search starts empty.
            viewModel.onQuery("")
            onBack()
        },
        onTextChange = {
            text = it
            viewModel.onQuery(it)
        },
        onSubmit = {
            text = it
            viewModel.submit(it)
        },
        onIncludeHidden = viewModel::setIncludeHidden,
        onOpenEvent = {
            viewModel.onResultOpened()
            onOpenEvent(it)
        },
        onForgetRecent = viewModel::forgetRecent,
        onClearRecent = viewModel::clearRecent
    )
    SearchContent(state, text, callbacks, modifier)
}

/**
 * The search field on top, the hidden-calendars switch and then the recent searches, the
 * results, or a message. Back clears the text first and leaves when there is none.
 */
@Composable
internal fun SearchContent(
    state: SearchState,
    text: String,
    callbacks: SearchCallbacks,
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (text.isEmpty()) callbacks.onBack() else callbacks.onTextChange("")
    }
    Scaffold(
        modifier = modifier,
        topBar = { SearchField(text, callbacks) }
    ) { padding ->
        Column(Modifier.padding(padding).imePadding().fillMaxSize()) {
            FilterChip(
                selected = state.includeHidden,
                onClick = { callbacks.onIncludeHidden(!state.includeHidden) },
                label = { Text(stringResource(R.string.search_include_hidden)) },
                modifier = Modifier.padding(horizontal = Spacing.l),
                leadingIcon = if (state.includeHidden) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                }
            )
            Body(state, callbacks)
        }
    }
}

@Composable
private fun SearchField(text: String, callbacks: SearchCallbacks) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (text.isEmpty()) focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(end = Spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = callbacks.onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.shell_back)
            )
        }
        TextField(
            value = text,
            onValueChange = callbacks.onTextChange,
            modifier = Modifier.weight(1f).focusRequester(focus),
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            trailingIcon = if (text.isNotEmpty()) {
                {
                    IconButton(onClick = { callbacks.onTextChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.search_clear)
                        )
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    callbacks.onSubmit(text)
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun Body(state: SearchState, callbacks: SearchCallbacks) {
    when (state.status) {
        SearchStatus.IDLE -> Recent(state, callbacks)

        SearchStatus.FAILED -> EmptyState(
            title = stringResource(R.string.search_failed_title),
            body = stringResource(R.string.search_failed_body)
        )

        SearchStatus.SEARCHING, SearchStatus.DONE -> when {
            !state.sections.isEmpty -> Results(state, callbacks)

            state.status == SearchStatus.SEARCHING -> {
                val searching = stringResource(R.string.search_searching)
                EventListSkeleton(
                    Modifier.fillMaxSize().semantics {
                        contentDescription = searching
                    }
                )
            }

            else -> NoResults(state, callbacks)
        }
    }
}

@Composable
private fun NoResults(state: SearchState, callbacks: SearchCallbacks) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.search_none_title),
            body = stringResource(R.string.search_none_body, state.query.trim()),
            actionLabel = stringResource(R.string.search_include_hidden)
                .takeIf { !state.includeHidden },
            onAction = { callbacks.onIncludeHidden(true) }
        )
    }
}

@Composable
private fun Recent(state: SearchState, callbacks: SearchCallbacks) {
    if (state.recent.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            EmptyState(
                title = stringResource(R.string.search_idle_title),
                body = stringResource(R.string.search_idle_body)
            )
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "header") { RecentHeader(callbacks.onClearRecent) }
            items(state.recent, key = { "recent-$it" }) { query ->
                RecentRow(
                    query = query,
                    onUse = { callbacks.onSubmit(query) },
                    onForget = { callbacks.onForgetRecent(query) }
                )
            }
        }
    }
}

@Composable
private fun Results(state: SearchState, callbacks: SearchCallbacks) {
    val list = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    // Scrolling the results means the typing is over: put the keyboard away.
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }
            .distinctUntilChanged()
            .filter { it }
            .collect { focusManager.clearFocus() }
    }
    ResultList(list, state, callbacks.onOpenEvent)
}

@Composable
internal fun ResultList(
    list: LazyListState,
    state: SearchState,
    onOpenEvent: (EventInstance) -> Unit,
    modifier: Modifier = Modifier
) {
    val fallback = MaterialTheme.colorScheme.primary.toArgb()
    fun colorOf(result: SearchResult) = result.instance.color
        ?: state.calendarColors[result.instance.calendarId]
        ?: fallback
    LazyColumn(modifier.fillMaxSize(), list, verticalArrangement = Arrangement.Top) {
        if (state.sections.upcoming.isNotEmpty()) {
            item(key = "upcoming") {
                SectionHeader(
                    stringResource(R.string.search_upcoming, state.sections.upcoming.size)
                )
            }
            items(state.sections.upcoming, key = { "u-${it.instance.eventId.value}" }) {
                SearchResultRow(it, colorOf(it), state.zone, onOpenEvent)
            }
        }
        if (state.sections.past.isNotEmpty()) {
            item(key = "past") {
                SectionHeader(stringResource(R.string.search_past, state.sections.past.size))
            }
            items(state.sections.past, key = { "p-${it.instance.eventId.value}" }) {
                SearchResultRow(it, colorOf(it), state.zone, onOpenEvent)
            }
        }
    }
}
