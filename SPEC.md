<!--
SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# UltimateCalendar — Especificación (SPEC)

Fuente: entrevista con el autor (2026-10-06). Lo que no esté aquí no se inventa: se pregunta.

## 1. Objetivo

Calendario Android bonito, con la interfaz de **Google Calendar** y la gestión de invitaciones de **Apple Calendar**, para cualquier cuenta que haya en el teléfono (Google, DAVx5, Nextcloud…) y, más adelante, con una conexión CalDAV propia. Software libre (GPLv3), offline, sin servicios de Google, destino final F-Droid. Tercera app de la familia Ultimate, junto a [UltimateDeck](https://github.com/qtekfun/UltimateDeck) y [UltimateTasks](https://github.com/qtekfun/UltimateTasks).

### Problemas que resuelve
- Los calendarios libres (Etar, Fossify…) son funcionales pero feos y lejos de la experiencia de Google Calendar.
- En Android no hay un sitio único donde ver **todas las invitaciones pendientes de responder**, de todas las cuentas, como la bandeja de Apple.
- Los avisos fallan en móviles de fabricantes chinos (OPPO/ColorOS, vivo, Xiaomi, Honor): si el sistema duerme o mata la app, el aviso no llega y nadie se entera.

## 2. Alcance y supuestos
- **Fuentes de datos**, detrás de una misma abstracción (`CalendarSource`):
  1. **Proveedor de calendario de Android** (`CalendarContract`), en lectura y escritura. Cubre **Google** (la cuenta de Google ya vuelca sus calendarios ahí; no hace falta Play Services ni ningún SDK de Google), **DAVx5** y cualquier otra cuenta con adaptador de sincronización. **Es el MVP.**
  2. **CalDAV propio** (Nextcloud como referencia), reutilizando el cliente CalDAV, el lector/escritor iCalendar y el Login Flow v2 de UltimateTasks. **Fase posterior al MVP.**
- Un mismo evento se edita siempre en su fuente de origen; la app no copia eventos entre fuentes.
- Volumen de referencia: ~10 calendarios, ~5.000 eventos en el proveedor, recurrencias incluidas. Debe ir fluido con 20.000.
- `minSdk` 26, `targetSdk` el último estable. Idiomas: inglés (por defecto) y español.

## 3. Requisitos funcionales (v1)

### RF-01 Primer arranque y asistente
Permiso de calendario (`READ_CALENDAR`/`WRITE_CALENDAR`), notificaciones, alarmas exactas y exención de batería; pantallas de inicio automático del fabricante. Es el **asistente de fiabilidad de UltimateTasks**, adaptado, con aviso de prueba. Si no hay ninguna cuenta con calendarios, explica cómo añadir Google o DAVx5 (y, cuando exista, ofrece la conexión CalDAV propia).

### RF-02 Calendarios y cuentas
Cajón lateral estilo Google Calendar: calendarios agrupados por cuenta, con su color, casilla de visible/oculto y acceso a ajustes del calendario (nombre y color locales si la fuente no permite cambiarlos). Calendario por defecto para eventos nuevos.

### RF-03 Vistas
**Agenda** (lista continua), **Día**, **3 días**, **Semana** y **Mes**, como Google Calendar:
- Deslizar lateralmente entre periodos; botón "Hoy"; selector de fecha desde la cabecera.
- Eventos de todo el día en una franja superior; eventos que se solapan, en columnas.
- Línea de la hora actual; zona horaria del dispositivo, con indicación cuando un evento tiene otra.
- Tocar un hueco libre abre la creación rápida en esa hora; mantener pulsado un evento permite **moverlo y cambiar su duración** arrastrando (Día, 3 días y Semana).
- Los eventos con invitación sin responder se dibujan como en Google (contorno, sin relleno).
- Material 3 con colores dinámicos, modo oscuro y AMOLED. Tablet: diseño adaptativo.

### RF-04 Detalle de evento
Título, fecha y hora, todo el día, zona horaria, repetición, lugar (abre la app de mapas por `geo:`), descripción, calendario, color, avisos, asistentes con su estado, organizador, enlaces de videollamada detectados en lugar/descripción. Botones de respuesta si eres invitado.

### RF-05 Crear y editar eventos
- Campos: título, todo el día, inicio/fin, zona horaria, lugar, descripción, calendario, color del evento (si la fuente lo admite), disponibilidad (ocupado/libre), avisos (varios).
- **Repeticiones**: presets y personalizado (cada N días/semanas/meses/años, días de la semana, "el 3er viernes", fin nunca/tras N/hasta fecha). Al editar o borrar una repetición: **solo este evento / este y los siguientes / todos**.
- **Invitar a gente**: añadir asistentes por correo (sugerencias desde los contactos solo si se concede `READ_CONTACTS`, opcional). Quien manda las invitaciones es la fuente (Google, o el servidor CalDAV con planificación en el servidor); la app no envía correos.
- Guardado explícito ("Guardar"), con aviso al salir si hay cambios.

### RF-06 Invitaciones pendientes (estilo Apple)
- Una invitación pendiente es **cualquier evento futuro, de cualquier calendario visible u oculto, en el que el usuario figura como asistente y su estado es "sin responder"** (`NEEDS-ACTION` / `ATTENDEE_STATUS_INVITED`). Así lo hace Apple: no lee una bandeja especial, mira el estado del asistente en todos los calendarios. Como Google, Nextcloud (planificación implícita) y DAVx5 dejan la invitación en el calendario, funciona igual para todas las cuentas.
- "Yo" se identifica por calendario: correo de la cuenta/propietario del calendario, más alias que el usuario puede añadir en Ajustes.
- **Bandeja de invitaciones**: icono con contador en la barra superior; lista con Aceptar / Quizá / Rechazar, y abrir el detalle.
- **Comprobación periódica** cada X (15 min por defecto; 15/30/60 min o solo manual en Ajustes) con WorkManager, además de al abrir la app, al tirar para refrescar y cuando el proveedor avisa de cambios (`ContentObserver`). Antes de buscar, pide a la fuente que sincronice si es posible.
- Al responder se cambia el estado del asistente en la fuente; la fuente (Google, o el servidor CalDAV) manda la respuesta al organizador.

### RF-07 Notificaciones
1. **Invitación nueva sin responder** (siempre activada por defecto): una notificación por invitación, con acciones Aceptar / Quizá / Rechazar. Varias a la vez se agrupan. Una invitación ya notificada no se vuelve a notificar salvo que el organizador la cambie.
2. **Cambios y cancelaciones** de eventos a los que vas (opcional, desactivado por defecto; activable en Ajustes): cambio de fecha/hora/lugar o cancelación por el organizador.
3. **Recordatorios de eventos** (los avisos `VALARM` / filas `Reminders` del evento), con acciones Posponer (5 min / 15 min / 1 hora) y Descartar, y "Abrir mapa"/"Unirse" si el evento tiene lugar o enlace de videollamada.
- Canales de notificación separados para cada tipo, para que el usuario ajuste el sonido en Android.
- Si otra app de calendario del teléfono también avisa, el asistente lo detecta y explica cómo desactivar sus avisos para no recibirlos dos veces.

### RF-08 Fiabilidad y recuperación de avisos perdidos
Copia y adapta la solución de UltimateTasks (T32–T34):
- Avisos **solo locales**, sin servidor: alarmas exactas (`USE_EXACT_ALARM` en Android 13+), modo alarma opcional (`setAlarmClock`), reprogramación al arrancar, actualizar la app, y cambiar la hora o la zona horaria.
- **Recuperación**: cada aviso mostrado se registra; si la app se durmió o la mataron y un aviso no llegó, se muestra al volver ("No llegó a su hora (10:30)") dentro de una ventana configurable (6 / 24 / 48 h / nunca; 24 h por defecto), sin avalancha la primera vez. Se ejecuta al abrir la app, tras cada comprobación periódica, en cada alarma y en cada latido.
- **Latido**: alarma exacta cada 30 min mientras haya avisos pendientes que recupera y replanifica.
- **Modo robusto** opcional: servicio en primer plano de importancia mínima (`specialUse`).
- Asistente con pasos por fabricante (OPPO/ColorOS, vivo, Xiaomi, Honor/Huawei, Samsung) y aviso de prueba.
- Lo mismo aplica a las notificaciones de invitaciones: si la comprobación periódica no corrió, la siguiente ejecución notifica todo lo pendiente.

### RF-09 Búsqueda
Por título, lugar, descripción y asistentes, en todos los calendarios visibles.

### RF-10 Ajustes
Tema, primer día de la semana, vista inicial, calendario por defecto, duración por defecto, avisos por defecto (con hora y todo el día), intervalo de comprobación de invitaciones, alias de correo propios, notificar cambios/cancelaciones, ventana de recuperación, modo alarma, modo robusto, asistente, versión.

### RF-11 Copia de seguridad cifrada
Copiar de UltimateTasks: exporta los ajustes (y, en la fase CalDAV, la sesión opcional con contraseña) cifrados con AES-GCM, restaurables desde Ajustes y desde el primer arranque. Así se lleva la misma configuración a todos los teléfonos. `allowBackup="false"`.

### RF-12 CalDAV propio (fase posterior)
Login Flow v2 de Nextcloud, descubrimiento, `sync-collection`, eventos en Room como fuente de verdad, cola de operaciones, conflictos, motor de recurrencia propio con `RRULE`/`EXDATE`/`RDATE`/`RECURRENCE-ID`. Las invitaciones y respuestas usan la planificación en el servidor (RFC 6638). Mismo comportamiento de RF-03 a RF-11.

## 4. Fuera de alcance (v1)
- Widget de pantalla de inicio (siguiente ronda, ver PLAN "Después del MVP").
- Leer invitaciones del correo (IMAP / adjuntos `.ics`).
- Suscripciones `webcal`/ICS de solo lectura (posible fase posterior).
- Tareas (`VTODO`): son de UltimateTasks.
- Buscar disponibilidad de otros asistentes (free/busy).
- Cualquier SDK de Google, Play Services, Firebase, analíticas o telemetría.

## 5. Sincronización y conflictos
- **Proveedor de Android**: la sincronización con el servidor es cosa del adaptador de cada cuenta (Google, DAVx5). La app escribe como un cliente normal (sin `CALLER_IS_SYNCADAPTER`), marca los cambios como sucios y deja que el adaptador los suba. Nunca borra ni modifica columnas de sincronización (`_SYNC_ID`, `SYNC_DATA*`) ni propiedades extendidas ajenas.
- **CalDAV propio**: mismas reglas que UltimateTasks (Room como fuente de verdad, ETags, conflictos por campo con fusión a tres bandas, `.ics` original conservado y propiedades desconocidas intactas).

## 6. Requisitos no funcionales
- Vista de semana con 5.000 eventos: primer dibujo < 300 ms en un móvil de gama media; desplazamiento sin saltos (perfil de referencia en T-medidas).
- Batería: la comprobación periódica consulta solo el proveedor local; no abre conexiones de red propias (salvo en la fase CalDAV).
- Accesibilidad: `contentDescription`, 48 dp, fuente al 200 %, TalkBack en la vista de agenda y en la bandeja.
- Sin datos personales en logs.

## 7. Calidad y CI
- `./gradlew check` en verde para cerrar cualquier tarea; la CI lo exige antes de fusionar (rama `master` protegida).
- Kover: ≥ 85 % sobre `domain`, `data` y `sync`; **100 %** en el detector de invitaciones, el planificador de avisos, la recuperación de avisos perdidos, la división de repeticiones ("este y los siguientes") y, en la fase CalDAV, la cola, el resolutor y la expansión de recurrencias.
- **Arnés de pruebas del proveedor**: una suite de contrato `CalendarSourceContract` que corre contra el `FakeCalendarSource` (tests unitarios) y contra el proveedor real del emulador con una cuenta local de pruebas (tests instrumentados). Fixtures que reproducen las filas tal como las escriben **Google** y **DAVx5** (cuentas, columnas de sincronización, asistentes, excepciones de repeticiones, propiedades extendidas). En la fase CalDAV, la misma suite corre contra la fuente CalDAV con MockWebServer.
- Releases automáticas con release-please, a partir de Conventional Commits (ver `RELEASING.md`).

## 8. Riesgos conocidos
- **Responder desde el proveedor**: hay que verificar que Google y DAVx5 suben el cambio de estado del asistente y que el organizador recibe la respuesta (Nextcloud lo hace en el servidor). Prototipo en T02.
- **Invitar desde el proveedor**: verificar que Google y Nextcloud (vía DAVx5) envían las invitaciones al crear un evento con asistentes. T02.
- **Pedir sincronización a otras cuentas** (`ContentResolver.requestSync`): puede estar limitado por el sistema o por el adaptador. Si no es posible, la frecuencia real depende del adaptador. T02.
- **Avisos duplicados** con el calendario del sistema u otras apps. RF-07.
- **Fabricantes chinos** que matan la app: mitigado por RF-08, medido en el teléfono del autor.
- **Rendimiento** de la vista de semana/mes con muchas recurrencias: usar la tabla `Instances` del proveedor, nunca expandir en la UI.

## 9. Decisiones abiertas
- ¿El estado "aviso ya mostrado / invitación ya notificada" debe compartirse entre teléfonos (para no recibirlo en todos) o basta con la copia de ajustes? De momento: local a cada teléfono, como en UltimateTasks.
- ¿Recordar de nuevo una invitación sin responder (p. ej. 24 h antes del evento)? De momento: no.
- Nombre y color locales de calendarios de solo lectura: ¿se incluyen en la copia de seguridad?

### Decisiones tomadas (entrevista 2026-10-06)
- Fuentes detrás de una abstracción: **proveedor de Android primero** (Google + DAVx5 + cualquier cuenta), **CalDAV propio después**. Google sin Play Services.
- Invitaciones estilo Apple: estado del asistente en todos los calendarios, comprobación cada X.
- Notificar invitaciones sin responder siempre; cambios y cancelaciones como opción.
- Recuperar avisos perdidos si la app se durmió o la mataron; enfoque **solo local**, sin ntfy ni servidor.
- Vistas del MVP: Agenda, Día, 3 días, Semana, Mes. Widget en otra ronda.
- MVP de edición: eventos con avisos, repeticiones e invitar a gente.
- Arnés: proveedor falso + suite de contrato contra el proveedor real del emulador.
- Releases con **release-please**; F-Droid preparado desde el día 1 (reproducible, sin dependencias prohibidas, fastlane), alta en fdroiddata tras la 1.0, solo versiones finales.
- Al final del plan: icono, capturas generadas por test, gráfico de cabecera y textos de tienda.
- Forzar CI (el autor no tenía preferencia; elegido): rama `master` protegida con ruleset, título de PR validado, hooks de Claude Code y tests de UI en emulador cada noche y bajo demanda.
- 2026-10-06 (T00): `gradle/libs.versions.toml` arranca con las versiones de UltimateTasks, sin retrofit, okhttp ni kotlinx-serialization (fase 6). detekt y ktlint se cablean ya en T00 porque los hooks de Claude Code los ejecutan; el resto de la calidad (Kover, licensee, dependencias prohibidas) queda en T01. `Random` no se copia de `TimeModule`: aquí no se usa.
- 2026-10-06 (T01): los paquetes al 100 % son `domain.invitations`, `domain.reminders` (planificador y recuperación) y `domain.recurrence` (división); los de la fase 6 se añaden con su código. `pr-title` admite asunto con mayúscula inicial (Dependabot no lo permite cambiar).
- 2026-10-06 (T03): `Event.rrule` guarda el RRULE crudo para que las reglas no entendidas sobrevivan a una edición; `RecurrenceRule.until` es solo fecha (heredado de UltimateTasks) y habrá que ampliarlo a fecha-hora cuando se haga la división "este y los siguientes". Los eventos de todo el día son `LocalDate` con fin exclusivo, no instantes. Ids como `value class` (`CalendarId`, `EventId`) sobre `Long`.
- 2026-10-06 (T04): contrato de `CalendarSource`: las instancias de un evento todo-el-día se fechan en UTC; toda instancia de una serie, editada o no, lleva `isRecurring = true`; los asistentes pueden volver en otro orden y con el organizador añadido; responder sin ser asistente es `Invalid`. El fake solo expande reglas DAILY/WEEKLY con COUNT. Pendiente para T05: compartir `FakeCalendarSource` y `CalendarSourceContract` entre `test` y `androidTest` (registrar un directorio de fuentes Kotlin extra en `app/build.gradle.kts`, que en T04 no se hizo) o copiarlos.
- 2026-10-06 (T07): `InvitationDetector(clock).scan(events, calendars, aliases)` y `diff(previous, scan)`. "Yo" = `ownerEmail` del calendario + alias (solo alias si el calendario no se conoce). "Futuro" = empieza después de ahora en la zona del reloj; un evento de todo el día empieza a medianoche de esa zona; una serie que empezó en el pasado cuenta como futura salvo que su UNTIL haya pasado. "Cambiada" = hora, zona o lugar (vacío = ninguno). Deja de estar pendiente: cancelada si el evento desaparece o me quitan; respondida en otro sitio si mi estado ya no es `NEEDS_ACTION`; los eventos pasados se ignoran. El modelo no tiene estado de evento, así que "cancelada" se infiere de que desaparece. T08 persiste `List<Invitation>`.
- 2026-10-06 (T02b, código): `ReminderEventSource` es una interfaz nueva, enlazada a una fuente vacía hasta que T04/T05 la conecten. `ReminderSettingsSource` también es nueva y usa SharedPreferences: de momento solo guarda el modo robusto; la hora de los avisos de todo el día (09:00), la ventana de recuperación (24 h) y el modo alarma usan valores por defecto hasta la tarea de Ajustes. Los avisos ya mostrados se registran en SharedPreferences, no en Room. Un aviso de evento de todo el día salta a la hora configurada, `ceil(minutos/1440)` días antes. Se planifica 30 días hacia delante con un máximo de 200 alarmas. Quedan fuera: posponer/descartar, el aviso de prueba y el asistente por fabricante. **Pendiente del autor:** medir en ColorOS con la app cerrada y el móvil en reposo, y decidir aquí qué ajustes hacen falta.
- 2026-10-06 (T09): `RecurrenceRule.until` es ahora `Until.Day(fecha)` (todo el día) o `Until.Moment(instante)` (con hora); se lee y escribe como `UNTIL=yyyyMMdd` o `UNTIL=yyyyMMddTHHmmssZ`, y una hora flotante cuenta como su día. `RecurrenceSplitter.edit` y `.delete` reciben el ámbito (solo este / este y los siguientes / todos) y devuelven `CalendarResult<SeriesChange>` con `Update`, `Delete`, `ReplaceOccurrence`, `CancelOccurrence` y `Split(truncated, newSeries?)`. El corte termina la serie vieja un segundo antes de la ocurrencia (UTC) si tiene hora, o el día anterior si es de todo el día, y descarta `COUNT`. `occurrencesBefore` lo aporta quien llama (no hay motor de expansión hasta T35) y arrastra el `COUNT` si la regla no cambia. "Este y los siguientes" sobre la primera ocurrencia se convierte en "todos". Una serie nueva lleva el id `RecurrenceSplitter.UNSAVED` (0). `CustomRepeat.toRule` recibe una zona opcional y `from` una obligatoria, para que `UNTIL` sea correcto en eventos con hora.
- 2026-10-06 (T06): `CalendarRepository` (`data/calendar`) ofrece `calendars()`, `visibleCalendars()`, `instances(range)` y `defaultCalendar()` como flujos; se vuelven a leer cuando `CalendarSource.changes` emite o cambian los ajustes locales. No hay caché de instancias: se leen del proveedor cada vez y nunca se expanden en la app. Room v1 (`UltimateCalendarDatabase`, sin migraciones aún, esquema en `app/schemas`): `calendar_settings` guarda nombre, color y visibilidad opcionales por calendario, y `default_calendar` es una tabla de una fila. Si no hay ningún calendario visible, `instances` devuelve lista vacía (un filtro nulo significaría "todos" para la fuente). Calendario por defecto: el elegido si aún puede crear eventos; si no, el primero visible y escribible; si no, el primero escribible; si no, `NotFound`. T05 debe enlazar `CalendarSource` en Hilt.
- 2026-10-06 (T12): lógica pura en `domain/firstrun`, pegamento Android en `ui/firstrun`; fuera de `domain/reminders`, así que no aplica el 100 % crítico. El indicador de primer arranque está tras `FirstRunFlag` (`isDone`/`markDone`) con `PreferencesFirstRunFlag` provisional hasta que se mueva a los ajustes; cerrar el asistente con Hecho o Atrás cuenta como visto. `LocalOpenWizard` lo reabre desde cualquier pantalla. `CalendarPresence` tiene una implementación `ProviderCalendarPresence` en `data/source` y un `ContentResolver` provisto por Hilt que puede duplicar el de T05/T06: sustituir por `CalendarSource.calendars()`. Otras apps de calendario: 12 paquetes conocidos, emparejados exactos con `<queries>` del manifiesto (un test mantiene sincronizadas ambas listas). El aviso de prueba usa `id = Long.MIN_VALUE` y `ReminderScheduler.scheduleOne` queda fuera del plan. Fuera: el paso "apps sin usar" y los interruptores de alarma/modo robusto.
- 2026-10-06 (T35): `RecurrenceEngine.expand(EventSeries, TimeRange, zone)` devuelve `Expansion.Complete`, `LimitReached` o `Unsupported(reason)` y nunca lanza. EXDATE, RDATE y RECURRENCE-ID son `OccurrenceKey.Day` (todo el día) o `.Moment` (con hora); una clave del otro tipo nunca coincide. DTSTART solo se genera si la regla lo selecciona; una serie sin RRULE es solo su DTSTART. COUNT incluye las ocurrencias con EXDATE y los RDATE quedan fuera de COUNT. Horario de verano: se conserva la hora local, un hueco avanza, un solape toma el desfase anterior salvo la primera ocurrencia de la serie, y la duración es tiempo transcurrido exacto. No soportado: BYDAY con ordinal (`2MO`) en DAILY/WEEKLY, o regla ilegible. Tope `MAX_PERIODS` = 100.000 (`LimitReached` devuelve lo parcial). Las series de todo el día se sitúan con la zona del dispositivo. `selfStatus` queda nulo: lo rellena quien llama.
- 2026-10-06 (T13): navegación con `NavState` guardado, como UltimateTasks, sin librería; `viewModel()` en lugar de `hiltViewModel`. Marcadores de posición para Búsqueda (T22), Nuevo evento (T20) e Invitaciones (T21). `TemporaryCalendarSourceModule` enlaza un `CalendarSource` vacío y T05 debe borrarlo (un binding duplicado rompe la build). `PendingInvitations` vacío hasta T21. Vista por defecto: Semana; Agenda abarca un mes desde la fecha y avanza por meses; la selección va en `SavedStateHandle`. "Hoy" se calcula con `Clock` + `SystemZone`.
- 2026-10-06 (T08): `InvitationChecker` hace el escaneo, notifica y solo entonces reemplaza `notified_invitations` en una transacción; el notificador debe ser idempotente y no lanzar. La ventana de instancias empieza en la invitación notificada más antigua, para que un evento ya empezado no se dé por cancelado. Solo se leen uno a uno los eventos con `selfStatus`, salvo que haya alias. Sin T05: `@BindsOptionalOf` + `UnavailableCalendarSource` de reserva (T05 puede quitarlo). Provisionales: `NoOpInvitationNotifier` (hasta T21) y `DefaultInvitationCheckSettings` (15 min, sin alias; debe pasar a leer los ajustes de T23). WorkManager arranca bajo demanda; "al abrir la app" es un callback del ciclo de vida de la actividad, no `ProcessLifecycleOwner`. Las comprobaciones por cambio del proveedor nunca piden sincronización. **`requestSync` con cuentas reales de Google y DAVx5 sigue sin verificar hasta T02.**
- 2026-10-06 (T10): Android muestra como mucho 3 botones, así que cada aviso lleva [Unirse, si no Mapa], Posponer y Descartar; Posponer los cambia por 5 min / 15 min / 1 h. Si hay enlace y lugar, gana Unirse. Se detectan enlaces de Meet, Zoom, Teams, Jitsi y Whereby en lugar o descripción (`EventReminders.description`, `PlannedReminder.joinUrl`); Mapa es un `geo:` solo para lugares que no son webs. Un aplazamiento por ocurrencia, en SharedPreferences (`SnoozedReminders`), y `Snoozes.merge` lo mete en el plan de alarmas, así que sobrevive al reinicio. Nunca dos veces: alarma y recuperación usan un `take(id, at)` atómico. Un aplazamiento se empareja con su ocurrencia por evento e inicio, y se descarta al pasar su hora si la ocurrencia ya no existe. `NotificationChannels` crea los canales de recordatorios, invitaciones y cambios (los dos últimos, para T21). `ReminderTime` une reloj y zona.
- 2026-10-06 (T23): la copia de seguridad es un sobre JSON (`format`, `iterations`, `salt`, `iv`, `data`) con todo el contenido cifrado: AES-256-GCM con PBKDF2-SHA256 a 210.000 iteraciones (se aceptan de 100.000 a 2.000.000) y la cabecera como AAD. El contenido lleva su propia versión; los campos y valores de enumeración desconocidos se ignoran; un formato o contenido más nuevo devuelve `NewerVersion`. Una contraseña errónea y un archivo manipulado no se distinguen. `defaultCalendar` no se exporta, porque los ids de calendario son de cada teléfono. **Dependencia nueva: kotlinx-serialization-json 1.11.0 (Apache-2.0).** Los ajustes viven en SharedPreferences, saneados al escribir y al leer; el indicador antiguo del modo robusto se migra una vez y gana un valor ya presente. El marcador `SettingsScreen` de T13 queda conectado. No hecho: selector de idioma; la fila de calendario por defecto sigue oculta hasta pasar la lista de calendarios a `SettingsScreen`. El asistente de T12 puede reutilizar `RestoreBackupButton`. Pendiente: que `DefaultInvitationCheckSettings` (T08) y `FirstRunFlag` (T12) lean de estos ajustes.
