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

## Fase 1 — Datos y arnés
- [x] **T03 Modelo de dominio**: cuenta, calendario (color, acceso, visible, propietario), evento, instancia, asistente (rol, estado), aviso, repetición. Tipos sellados para errores (`CalendarResult`). `Clock` inyectable.
  - *Resultado:* hecho. Modelo en `domain/model` (ids tipados, cuenta, calendario con `CalendarAccess`, `EventTime` con eventos de todo el día como fechas, evento, instancia, asistente, aviso), `CalendarResult`/`CalendarError` en `domain/result`, y `RecurrenceRule`/`RecurrenceRules` (RRULE) copiados de UltimateTasks con sus tests. El `Clock` inyectable ya está en `TimeModule` (T00). La expansión de repeticiones no se copia: es de la fase 6.
- [ ] **T04 Abstracción `CalendarSource`** + **`FakeCalendarSource`** en memoria + **suite de contrato** `CalendarSourceContract` (leer rangos, crear, editar, borrar, responder, repeticiones, excepciones, asistentes).
  - *Verificación:* la suite pasa contra el fake.
- [ ] **T05 `ProviderCalendarSource`**: lectura con `Instances` por rango, escritura como cliente normal (sin `CALLER_IS_SYNCADAPTER`), asistentes, avisos, excepciones (`CONTENT_EXCEPTION_URI`), `ContentObserver` como `Flow`. Respeta columnas de sincronización y propiedades extendidas ajenas.
  - *Verificación:* la suite de contrato pasa en el emulador contra el proveedor real con una cuenta local de pruebas; fixtures de Google y DAVx5 de T02 se leen igual que en el teléfono.
- [ ] **T06 Repositorio y caché de lectura**: flujos por rango de fechas para las vistas, calendarios visibles, calendario por defecto, ajustes locales de calendario (nombre/color) en Room.

## Fase 2 — Lógica
- [ ] **T07 Detector de invitaciones**: "yo" por calendario + alias; pendientes = asistente propio `NEEDS-ACTION` en eventos futuros de cualquier calendario; diferencias entre ejecuciones (nueva, cambiada, cancelada, respondida en otro sitio). **100 % de cobertura.**
- [ ] **T08 Comprobación periódica**: WorkManager con intervalo de Ajustes, `requestSync` (según T02), ejecución al abrir/refrescar/`ContentObserver`; registro de lo ya notificado en Room.
- [x] **T09 Lógica de repeticiones**: editar/borrar "solo este / este y los siguientes / todos" (corte de `RRULE` con `UNTIL`, excepciones, nueva serie). Presets y personalizado copiados del editor de UltimateTasks. **100 % de cobertura en la división.**
  *Resultado:* `RecurrenceRule.until` pasa a `Until` (`Day` para todo el día, `Moment` UTC para eventos con hora); `RecurrenceSplitter` devuelve `SeriesChange` (sin expandir repeticiones: el llamador da `occurrencesBefore` para `COUNT`); presets y `CustomRepeat` copiados de UltimateTasks.
- [ ] **T10 Planificador de avisos de eventos**: copiar y adaptar `ReminderPlanner` (avisos por instancia, todo el día, posponer), recuperación de perdidos, latido y modo robusto. **100 % de cobertura** en planificador y recuperación.
- [ ] **T11 Tests de fiabilidad**: app matada a mitad de comprobación, cambio de zona horaria, reinicio, actualización, horario de verano.

## Fase 3 — Interfaz (experiencia Google Calendar)
- [ ] **T12 Primer arranque + asistente de fiabilidad** (RF-01), copiado de UltimateTasks, con detección de otras apps de calendario que avisan.
- [ ] **T13 Esqueleto de navegación**: barra superior con mes/selector de fecha, botón Hoy, búsqueda, bandeja con contador; cajón de calendarios (RF-02); FAB de nuevo evento.
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
- [ ] **T28 Icono**: icono adaptativo (primer plano, fondo y **monocromo** para iconos temáticos de Android 13+), en la línea visual de UltimateDeck/UltimateTasks; `fastlane/.../en-US/images/icon.png` 512×512.
  - *Verificación:* se ve bien en launcher redondo, cuadrado, temático y en F-Droid.
- [ ] **T29 Capturas generadas por test**: test instrumentado `Screenshots` con calendarios y eventos **inventados** (nunca datos reales) cargados en una cuenta local de pruebas: Agenda, Semana, Mes, detalle con asistentes, bandeja de invitaciones, notificación de invitación, modo oscuro. En inglés y español, teléfono y tablet (`phoneScreenshots`, `tenInchScreenshots`).
- [ ] **T30 Material de tienda**: `title.txt`, `short_description.txt` (≤ 80), `full_description.txt` en en-US y es-ES; `featureGraphic.png` 1024×500; README con capturas e insignias (CI, release, licencia, F-Droid cuando exista); `PRIVACY.md` revisado con cada permiso.
- [ ] **T31 Primera release candidata**: `Release-As: 1.0.0-rc.1`, fusionar la PR de release-please con los textos de tienda de su versionCode; probar instalación limpia y actualización desde el APK de GitHub.
- [ ] **T32 1.0 y F-Droid**: release 1.0.0; rellenar `fdroid/com.qtekfun.ultimatecalendar.yml` (`commit`, `AllowedAPKSigningKeys`) y enviar la receta a fdroiddata; comprobar que el build de F-Droid coincide con el APK publicado (reproducible).

## Fase 6 — CalDAV propio (RF-12)
- [ ] **T33 Copiar de UltimateTasks** el cliente CalDAV, lector/escritor iCalendar (con su corpus de ida y vuelta, ampliado con `VEVENT` de Google, Nextcloud, Outlook y Apple) y Login Flow v2 + Keystore.
- [ ] **T34 Room como fuente de verdad, cola y resolutor** (copiados). **100 % de cobertura.**
- [ ] **T35 Motor de recurrencia de eventos** (`RRULE`, `EXDATE`, `RDATE`, `RECURRENCE-ID`, zonas horarias). **100 % de cobertura.**
- [ ] **T36 `CalDavCalendarSource`**: la suite de contrato pasa con MockWebServer; planificación en el servidor para invitar y responder.
- [ ] **T37 Login y gestión de la cuenta CalDAV** en la UI; la copia de seguridad incluye la sesión opcional.

## Después del MVP (backlog, no implementar aún)
- Widget de pantalla de inicio (agenda y mes).
- Suscripciones `webcal`/ICS.
- Invitaciones desde el correo (IMAP).
- Recordar de nuevo invitaciones sin responder.
