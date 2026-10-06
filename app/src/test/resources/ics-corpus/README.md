# iCalendar corpus

Event files used by the round-trip tests: every file must be written back byte for byte (in its own line endings and with CRLF or LF throughout), and changing one property must leave every other line untouched. The mapping tests (`VeventReadTest`, `VeventWriteTest`, `IcsEventsTest`) read the same files and check what the app understands of them.

Everything here is invented, shaped like what each server or client really sends (property order, vendor properties, quirks). Addresses are `example.com`.

- `google/`: Google Calendar: all-day multi-day, a recurring series with `EXDATE`, a moved and a cancelled `RECURRENCE-ID` override, an invitation with attendees in every `PARTSTAT` and role, `P0DT0H30M0S`-style alarm triggers, lines folded by Google.
- `nextcloud/`: Nextcloud Calendar (SabreDAV): a timed event with `VTIMEZONE`, every form of `VALARM` trigger, `RDATE` with dates and periods.
- `outlook/`: Exchange/Outlook: `W. Europe Standard Time` defined only by its `VTIMEZONE`, `MAILTO:` in capitals, a cancellation with `DURATION`, and a zone only its own `VTIMEZONE` explains, and one nobody defined.
- `apple/`: Apple Calendar: floating time, `X-APPLE-STRUCTURED-LOCATION`, default alarms, attendees given by `urn:uuid:` with an `EMAIL` parameter, all-day yearly events and UTC.
- `edge-cases/`: written for this project: CRLF and mixed line endings, folding inside multi-byte characters, quoted parameters, `VALARM`, `VTIMEZONE`, malformed lines, missing `END`.
