<!--
SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# UltimateCalendar — Plan de tareas

Reglas: una tarea cada vez, en su rama `feat/<tarea>`, con `./gradlew check` en verde antes de abrir la PR. Se fusiona solo por PR con la CI en verde. Marca `[x]` al completar. Cada tarea debe poder verificarse (test o prueba manual descrita). Las tareas que copian de UltimateTasks copian también sus tests.

## Fase 0 — Cimientos, CI y prototipos de riesgo
- [x] **T00 Proyecto base**: copiar de UltimateTasks la estructura Gradle (KTS, `libs.versions.toml`, Hilt, Compose, Material 3, Room, WorkManager), tema (claro/oscuro/AMOLED/Material You), `strings.xml` en/es, cabeceras SPDX, `.gitignore` (con `__pycache__/`). Paquete `com.qtekfun.ultimatecalendar`, `appVersion` ya viene en `gradle.properties` (lo gestiona release-please), `HiltTestRunner`.
  - *Verificación:* `./gradlew assembleDebug` compila y la app arranca con pantalla vacía.
  - *Resultado:* hecho. Se adelantó de T01 el cableado de detekt y ktlint (el hook `quick-check.sh` los ejecuta), con la config de `config/detekt/`; Kover, licensee, `checkForbiddenDependencies` y Dependabot siguen en T01. Room y WorkManager van solo como dependencias (sin entidades ni workers hasta T06/T08). La app muestra el nombre en una pantalla vacía; `StartupTest` pasa en un Pixel 8 con `installDebug installDebugAndroidTest` + `adb instrument`.
- [x] **T01 Calidad y CI**: detekt, ktlint, Lint (warnings como errores), Kover (85 % + variante `critical` al 100 %), verificación de dependencias, licensee, `checkForbiddenDependencies` (Play Services/Firebase), workflows de `.github/` ya incluidos, Dependabot. Aplicar el ruleset de `master` (`tools/apply-ruleset.sh`) y crear los secretos `UC_*` y `RELEASE_PLEASE_TOKEN`.
  - *Verificación:* PR de prueba en verde; una dependencia de Play Services añadida a propósito la hace fallar; un push directo a `master` es rechazado; una PR con título no convencional falla.
  - *Resultado:* hecho en código: Kover (85 % global + variante `critical` al 100 % en `domain.invitations`, `domain.reminders` y `domain.recurrence`), licensee (solo Apache-2.0) y `checkForbiddenDependencies`, todo enganchado a `check`; comprobado en local que una dependencia de Play Services hace fallar `check`. Workflows y Dependabot ya venían del andamiaje; `pr-title` aceptó mayúsculas en #3 porque Dependabot titula `Bump`. **Pendiente del autor** (los gestiona él): volver a habilitar el ruleset con `tools/apply-ruleset.sh` (y comprobar que un push directo a `master` se rechaza), crear `RELEASE_PLEASE_TOKEN` y confirmar los secretos `UC_*`.
- [ ] **T02 Prototipo del proveedor (riesgos de `SPEC.md` §8)**: en el teléfono del autor, con una cuenta Google y una DAVx5→Nextcloud y calendarios de prueba creados para ello: leer calendarios/eventos/instancias/asistentes; detectar "sin responder"; responder y comprobar que el organizador recibe la respuesta; crear un evento con un asistente y comprobar que le llega la invitación; `requestSync` a cada cuenta; excepciones de repeticiones. Volcar filas reales (anonimizadas) como fixtures del arnés.
  - *Verificación:* informe con resultados por cuenta en `SPEC.md` §9; fixtures en `app/src/test/resources/provider-fixtures/{google,davx5}/`.
- [ ] **T02b Fiabilidad de avisos**: copiar la solución de UltimateTasks (planificador, receptor, `BootReceiver`, latido, modo robusto, recuperación) como esqueleto y medir en ColorOS con la app cerrada y el móvil en reposo.
  - *Verificación:* informe con retrasos medidos; decisión en `SPEC.md` §9.
  - *Resultado:* esqueleto de código hecho (planificador, recuperación, latido, receptores, `BootReceiver`, modo robusto; `domain.reminders` al 100 % de líneas y ramas). Pendiente para el autor: las mediciones en ColorOS y la decisión en `SPEC.md` §9.

## Fase 1 — Datos y arnés
- [x] **T03 Modelo de dominio**: cuenta, calendario (color, acceso, visible, propietario), evento, instancia, asistente (rol, estado), aviso, repetición. Tipos sellados para errores (`CalendarResult`). `Clock` inyectable.
  - *Resultado:* hecho. Modelo en `domain/model` (ids tipados, cuenta, calendario con `CalendarAccess`, `EventTime` con eventos de todo el día como fechas, evento, instancia, asistente, aviso), `CalendarResult`/`CalendarError` en `domain/result`, y `RecurrenceRule`/`RecurrenceRules` (RRULE) copiados de UltimateTasks con sus tests. El `Clock` inyectable ya está en `TimeModule` (T00). La expansión de repeticiones no se copia: es de la fase 6.
- [x] **T04 Abstracción `CalendarSource`** + **`FakeCalendarSource`** en memoria + **suite de contrato** `CalendarSourceContract` (leer rangos, crear, editar, borrar, responder, repeticiones, excepciones, asistentes).
  - *Verificación:* la suite pasa contra el fake.
  - *Resultado:* hecho. `CalendarSource` en `data/source` (calendarios, instancias, crear/editar/borrar, editar o cancelar una instancia, responder, `changes`); `FakeCalendarSource` y `CalendarSourceContract` (16 escenarios) en `src/test`, de momento; T05 los reutilizará en `androidTest` (hay que compartir el directorio o copiarlos, ver SPEC §9). "Este y los siguientes" no es una operación de la fuente: la compone el dominio (T09) con `update`, `editInstance`, `cancelInstance` y `create`.
- [ ] **T05 `ProviderCalendarSource`**: lectura con `Instances` por rango, escritura como cliente normal (sin `CALLER_IS_SYNCADAPTER`), asistentes, avisos, excepciones (`CONTENT_EXCEPTION_URI`), `ContentObserver` como `Flow`. Respeta columnas de sincronización y propiedades extendidas ajenas.
  - *Verificación:* la suite de contrato pasa en el emulador contra el proveedor real con una cuenta local de pruebas; fixtures de Google y DAVx5 de T02 se leen igual que en el teléfono.
- [x] **T06 Repositorio y caché de lectura**: flujos por rango de fechas para las vistas, calendarios visibles, calendario por defecto, ajustes locales de calendario (nombre/color) en Room.
  - *Resultado:* hecho. `CalendarRepository` (`data/calendar`) sobre `CalendarSource`: `calendars`, `visibleCalendars`, `instances(range)` y `defaultCalendar` son flujos que se releen con `changes` y con los ajustes locales. Room 3 versión 1 (`UltimateCalendarDatabase`: `calendar_settings` con nombre/color/visible anulables y `default_calendar` de una fila), `DatabaseModule` en `di/`. Sin caché de instancias (se relee del proveedor); el `CalendarSource` lo enlazará T05.

## Fase 2 — Lógica
- [x] **T07 Detector de invitaciones**: "yo" por calendario + alias; pendientes = asistente propio `NEEDS-ACTION` en eventos futuros de cualquier calendario; diferencias entre ejecuciones (nueva, cambiada, cancelada, respondida en otro sitio). **100 % de cobertura.**
  *Resultado:* `InvitationDetector(clock)` en `domain/invitations`: `scan` (pendientes + estado de cada evento) y `diff` (nueva, cambiada, cancelada, respondida en otro sitio); 100 % líneas y ramas.
- [x] **T08 Comprobación periódica**: WorkManager con intervalo de Ajustes, `requestSync` (según T02), ejecución al abrir/refrescar/`ContentObserver`; registro de lo ya notificado en Room.
  *Resultado:* `InvitationChecker` (`sync/`) pide la sincronización, escanea todos los calendarios con `InvitationDetector`, notifica la diferencia a `InvitationNotifier` y solo entonces reemplaza en una transacción la tabla Room `notified_invitations` (v2 con migración 1→2 y test): si la comprobación muere a medias no se guarda nada y la siguiente notifica lo pendiente. `InvitationCheckCoordinator` ofrece `checkNow()`, `onAppOpened()` y observa `CalendarSource.changes` con debounce; el trabajo periódico (WorkManager a demanda, con fábrica propia sin dependencias nuevas) sale de `InvitationCheckSettings` (15 min por defecto, T23 lo enlaza) y el notificador es un no-op hasta T21. **`requestSync` sobre cuentas reales (Google, DAVx5) no está verificado hasta T02**: es best-effort y tolera el fallo. `CalendarSource` sigue sin enlace Hilt hasta T05: se resuelve con `@BindsOptionalOf`, con `UnavailableCalendarSource` mientras tanto.
- [x] **T09 Lógica de repeticiones**: editar/borrar "solo este / este y los siguientes / todos" (corte de `RRULE` con `UNTIL`, excepciones, nueva serie). Presets y personalizado copiados del editor de UltimateTasks. **100 % de cobertura en la división.**
  *Resultado:* `RecurrenceRule.until` pasa a `Until` (`Day` para todo el día, `Moment` UTC para eventos con hora); `RecurrenceSplitter` devuelve `SeriesChange` (sin expandir repeticiones: el llamador da `occurrencesBefore` para `COUNT`); presets y `CustomRepeat` copiados de UltimateTasks.
- [x] **T10 Planificador de avisos de eventos**: copiar y adaptar `ReminderPlanner` (avisos por instancia, todo el día, posponer), recuperación de perdidos, latido y modo robusto. **100 % de cobertura** en planificador y recuperación.
  - *Resultado:* posponer (5 min / 15 min / 1 h) y descartar desde la notificación, con "Unirse" (enlaces de Meet, Zoom, Teams, Jitsi, Whereby en lugar o descripción) o "Mapa" (`geo:`) según el evento; "Posponer" cambia los botones por las tres duraciones (Android muestra tres como máximo). Lo pospuesto se guarda en SharedPreferences, entra en el plan de alarmas (sobrevive a reinicio) y se concilia con la recuperación (`take` atómico: nunca sale dos veces); si el evento se edita o cancela deja de sonar. Tres canales en `NotificationChannels` (avisos, invitaciones, cambios). Lógica pura en `domain/reminders` al 100 %. Sin probar en teléfono real.
- [ ] **T11 Tests de fiabilidad**: app matada a mitad de comprobación, cambio de zona horaria, reinicio, actualización, horario de verano.

## Fase 3 — Interfaz (experiencia Google Calendar)
- [x] **T12 Primer arranque + asistente de fiabilidad** (RF-01), copiado de UltimateTasks, con detección de otras apps de calendario que avisan.
  - *Resultado:* hecho en código, sin probar en teléfono real (queda para el autor: diálogos de permisos, pantallas de OPPO/vivo/Xiaomi/Honor/Samsung, aviso de prueba y detección de otras apps). Lógica pura en `domain/firstrun` (fabricante, pantallas del fabricante, plan de pasos según permisos, entrega del aviso de prueba, otras apps de calendario) con tests; UI fina en `ui/firstrun` (`FirstRunHost` en `MainActivity`, `LocalOpenWizard` para abrirlo desde otras pantallas). La marca de "ya mostrado" va tras la interfaz `FirstRunFlag` (T23 puede moverla). `<queries>` lista solo las 12 apps de `OtherCalendarApps.KNOWN` (un test las mantiene sincronizadas). Sin paso de "apps sin uso" ni interruptores de modo alarma/robusto: llegan con Ajustes (T23).
- [x] **T13 Esqueleto de navegación**: barra superior con mes/selector de fecha, botón Hoy, búsqueda, bandeja con contador; cajón de calendarios (RF-02); FAB de nuevo evento.
  - *Resultado:* `AppNavigation` (estado `NavState` guardado, como en UltimateTasks, sin librería de navegación) con `ShellScreen`: cabecera con mes y selector de fecha, Hoy, búsqueda y bandeja con contador; cajón con las vistas, calendarios por cuenta (color y casilla que escribe el ajuste local de visibilidad) y Ajustes; FAB. Vistas, búsqueda, nuevo evento, bandeja y Ajustes son marcadores para T14–T17, T22, T20, T21 y T23. Lógica de fechas pura en `domain.navigation` (`ViewPeriods`, `DateRange`, `AccountCalendars`) y `ShellViewModel` con un único `StateFlow`. `TemporaryCalendarSourceModule` enlaza una fuente vacía hasta T05 (T05 debe borrarlo); `PendingInvitations` vacío hasta T21.
- [ ] **T14 Vista Agenda** (RF-03).
- [ ] **T15 Vistas Día y 3 días**: línea de horas, todo el día arriba, solapes en columnas, hora actual, invitaciones con contorno, tocar hueco = creación rápida.
- [ ] **T16 Vista Semana**.
- [ ] **T17 Vista Mes**.
- [ ] **T18 Arrastrar para mover y cambiar duración** en Día/3 días/Semana (con deshacer; pregunta "este/siguientes/todos" en repeticiones).
- [ ] **T19 Detalle de evento** (RF-04) con respuesta a invitaciones.
- [ ] **T20 Editor de eventos** (RF-05): campos, avisos múltiples, zona horaria, editor de repetición, asistentes.
- [ ] **T21 Bandeja de invitaciones** (RF-06) + **notificaciones** con acciones (RF-07): invitaciones, cambios/cancelaciones opcionales, recordatorios con Posponer, canales separados.
- [ ] **T22 Búsqueda** (RF-09).
- [ ] **T23 Ajustes** (RF-10) y **copia de seguridad cifrada** (RF-11), copiada de UltimateTasks.
- [ ] **T24 Tablet**: diseño adaptativo (cajón fijo, agenda + detalle).

## Fase 4 — Pulido
- [ ] **T25 Medidas**: rendimiento de Semana/Mes con 5.000 y 20.000 eventos (Macrobenchmark o medidas manuales anotadas en `SPEC.md` §6), batería de la comprobación periódica.
- [ ] **T26 Accesibilidad**: TalkBack en agenda, bandeja y editor; fuente al 200 %; contraste.
- [ ] **T27 Tests de UI de flujos clave**: crear evento, responder invitación desde la notificación y desde la bandeja, editar "este y los siguientes". Corren cada noche en el emulador (`ui-tests.yml`).

## Fase 5 — Identidad y publicación
- [x] **T28 Icono**: icono adaptativo (primer plano, fondo y **monocromo** para iconos temáticos de Android 13+), en la línea visual de UltimateDeck/UltimateTasks; `fastlane/.../en-US/images/icon.png` 512×512.
  - *Verificación:* se ve bien en launcher redondo, cuadrado, temático y en F-Droid.
  - *Resultado:* hecho. Calendario blanco con divisor y marca de verificación (huecos reales, así la misma capa sirve de monocromo) sobre fondo verde azulado `#0F7B6C`, para distinguirla de los azules de Tasks y Deck. `icon.png` 512×512 generado desde el mismo diseño. Comprobado en vista previa cuadrada, redonda y monocroma; **pendiente del autor** verla en su launcher con iconos temáticos.
- [ ] **T29 Capturas generadas por test**: test instrumentado `Screenshots` con calendarios y eventos **inventados** (nunca datos reales) cargados en una cuenta local de pruebas: Agenda, Semana, Mes, detalle con asistentes, bandeja de invitaciones, notificación de invitación, modo oscuro. En inglés y español, teléfono y tablet (`phoneScreenshots`, `tenInchScreenshots`).
- [ ] **T30 Material de tienda**: `title.txt`, `short_description.txt` (≤ 80), `full_description.txt` en en-US y es-ES; `featureGraphic.png` 1024×500; README con capturas e insignias (CI, release, licencia, F-Droid cuando exista); `PRIVACY.md` revisado con cada permiso.
  - *Resultado:* hecho en parte (la casilla sigue sin marcar): `full_description.txt` y `featureGraphic.png` (1024×500, con eslogan) en en-US y es-ES, los textos pasan `tools/check-store-texts.sh`, e insignias de CI, release y licencia en el README. **Pendiente:** capturas en el README (llegan con T29), la insignia de F-Droid (T32) y revisar `PRIVACY.md` permiso por permiso contra el manifiesto final, cuando T05/T12/T21 hayan añadido los suyos.
- [ ] **T31 Primera release candidata**: `Release-As: 1.0.0-rc.1`, fusionar la PR de release-please con los textos de tienda de su versionCode; probar instalación limpia y actualización desde el APK de GitHub.
- [ ] **T32 1.0 y F-Droid**: release 1.0.0; rellenar `fdroid/com.qtekfun.ultimatecalendar.yml` (`commit`, `AllowedAPKSigningKeys`) y enviar la receta a fdroiddata; comprobar que el build de F-Droid coincide con el APK publicado (reproducible).

## Fase 6 — CalDAV propio (RF-12)
- [ ] **T33 Copiar de UltimateTasks** el cliente CalDAV, lector/escritor iCalendar (con su corpus de ida y vuelta, ampliado con `VEVENT` de Google, Nextcloud, Outlook y Apple) y Login Flow v2 + Keystore.
- [ ] **T34 Room como fuente de verdad, cola y resolutor** (copiados). **100 % de cobertura.**
- [x] **T35 Motor de recurrencia de eventos** (`RRULE`, `EXDATE`, `RDATE`, `RECURRENCE-ID`, zonas horarias). **100 % de cobertura.**
  *Resultado:* `RecurrenceEngine.expand(EventSeries, TimeRange, zone)` devuelve `Expansion` (`Complete`, `LimitReached`, `Unsupported`); sin dependencias nuevas y con `RecurrenceRule` intacta. Cobertura 100 % de líneas y ramas en `domain.recurrence`.
- [ ] **T36 `CalDavCalendarSource`**: la suite de contrato pasa con MockWebServer; planificación en el servidor para invitar y responder.
- [ ] **T37 Login y gestión de la cuenta CalDAV** en la UI; la copia de seguridad incluye la sesión opcional.

## Después del MVP (backlog, no implementar aún)
- Widget de pantalla de inicio (agenda y mes).
- Suscripciones `webcal`/ICS.
- Invitaciones desde el correo (IMAP).
- Recordar de nuevo invitaciones sin responder.
