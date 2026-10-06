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
| Ignore battery optimisations (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, optional) | Lets you exempt the app from battery savers that delay reminders. Asked only from the setup guide, and you can say no. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, optional robust mode) | Same, on the most aggressive phones; shows a silent notification. Off by default; does no network or other work. |
| Query installed apps (`<queries>`, 12 known calendar apps) | The setup guide checks whether another calendar app is installed, so it can tell you how to turn off its duplicate reminders. Nothing is read beyond whether those packages exist, and nothing leaves the phone. |
| Contacts (optional) | Suggest attendees' addresses while inviting. Never uploaded. |

Settings backups are encrypted with a password you choose and saved where you decide.

*Reviewed against the manifest on 2026-10-06 (PLAN T30). The optional contacts permission is added with the event editor (T20) and will be reviewed with it.*
