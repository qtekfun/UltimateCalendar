<!--
SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Changelog

All notable changes to UltimateCalendar. Written by hand in the release PR: move the notes of
`[Unreleased]` under `## [X.Y.Z] - YYYY-MM-DD` (see RELEASING.md). The Release workflow publishes
the notes of the tagged version.

## [Unreleased]

## [1.0.0-rc.2] - 2026-10-07

Fixes from the first real-phone tests of 1.0.0-rc.1.

### Added
- A refresh button: one tap syncs your Android accounts, the built-in CalDAV account and the subscriptions, and checks for new invitations. In the top bar on wide screens, in the menu on phones, and pull-to-refresh in the agenda.

### Fixed
- Invitations are now sent promptly: after creating or changing an event with guests, or answering an invitation, the app asks your account to sync right away instead of waiting for its next turn.
- Answering an invitation that was sent to one of your own address aliases always failed with "not sent"; it now works.
- An answer that could not be uploaded because the account was offline or had sync off is retried automatically, and the notification says "Accepted" and that the reply will be sent when the account syncs.
- Changes to events with guests on a CalDAV account sync after 2 seconds instead of 10.

## [1.0.0-rc.1] - 2026-10-06

First release candidate.

### Added
- Views like Google Calendar: agenda, day, 3 days, week and month, with adaptive layouts for tablets and foldables, and drag to move or resize events.
- Home-screen widgets: agenda and month.
- One inbox for every unanswered invitation, from every calendar on the phone, with Accept / Maybe / Decline from the app or from the notification. Optional notices when an event you are going to is moved or cancelled, and optional reminders to answer.
- Event reminders that survive sleep, reboots and time-zone changes, with snooze, dismiss, map and join actions, and recovery of reminders that did not arrive.
- Create and edit events with several reminders, time zones, attendees and repeat rules; for repeating events choose only this event, this and the following, or all.
- Works with every calendar on the phone (Google, DAVx5 and others, no Google Play Services), with a built-in CalDAV connection (Nextcloud) and with read-only ICS/webcal subscriptions.
- Search by title, place, description and attendees.
- Light, dark and pure-black AMOLED themes, dynamic colors, English and Spanish, TalkBack and large-font support.
- Encrypted backup of the settings and, optionally, the CalDAV sign-in.
