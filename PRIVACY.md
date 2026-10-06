<!--
SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Privacy

UltimateCalendar has no servers, no analytics, no ads and no telemetry. Your calendars stay on your phone and on the servers of the accounts you already use (Google, your CalDAV server through DAVx⁵…). The app only talks to the network in the future built-in CalDAV mode, and only to the server you configure, always over HTTPS (plain HTTP is refused). The building blocks of that mode (CalDAV client, iCalendar reader and Nextcloud Login Flow v2) are already in the code, but nothing uses them yet and the app does not declare the `INTERNET` permission until the mode ships. When it does, the app password of your account will be kept encrypted with a key that never leaves the Android Keystore, excluded from Android backups, and never written to logs.

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
| Read sync settings (`READ_SYNC_SETTINGS`) | Before asking an account to sync, the app checks whether that account has calendar sync turned on, so it does not wake accounts that would do nothing. It only reads the on/off state. |
| Network state (`ACCESS_NETWORK_STATE`) | Lets the periodic invitation check skip sync requests while the phone has no connection. It reads whether a network exists, nothing about what you do on it. |
| Contacts (optional) | Suggest attendees' addresses while inviting. Never uploaded. |

Settings backups are encrypted with a password you choose and saved where you decide.

*Reviewed against the manifest on 2026-10-06 (PLAN T30). The optional contacts permission is added with the event editor (T20) and will be reviewed with it.*
