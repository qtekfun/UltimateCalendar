<!--
SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Privacy

UltimateCalendar has no servers, no analytics, no ads and no telemetry. Your calendars stay on your phone and on the servers of the accounts you already use (Google, your CalDAV server through DAVx⁵…). The app only talks to the network in the future built-in CalDAV mode, and only to the server you configure.

## Permissions

| Permission | Why |
|---|---|
| Read / write calendar | Show your events, create and edit them, answer invitations. |
| Notifications | Reminders, new invitations and (optional) changes or cancellations. |
| Alarms & reminders (`USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`) | Deliver reminders exactly on time. |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Reschedule reminders after a reboot or an update. |
| Ignore battery optimisations (optional) | Keep reminders reliable on phones that kill background apps. |
| Foreground service (optional robust mode) | Same, on the most aggressive phones; shows a silent notification. |
| Contacts (optional) | Suggest attendees' addresses while inviting. Never uploaded. |

Settings backups are encrypted with a password you choose and saved where you decide.

*Draft — reviewed in PLAN T30 against the final manifest.*
