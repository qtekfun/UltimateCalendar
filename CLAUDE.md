# UltimateCalendar — instrucciones para Claude Code

<!-- SPDX-FileCopyrightText: 2026 UltimateCalendar contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

Calendario Android con la interfaz de **Google Calendar** y las invitaciones de **Apple Calendar**, sobre el proveedor de calendario de Android (Google, DAVx5…) y, más adelante, CalDAV propio. Offline, software libre (GPLv3), destino final F-Droid. Hermana de [UltimateTasks](https://github.com/qtekfun/UltimateTasks) y [UltimateDeck](https://github.com/qtekfun/UltimateDeck): se copian y adaptan sus piezas comunes.

Lee siempre `SPEC.md` (qué construir) y `PLAN.md` (en qué orden) antes de empezar. Si algo de este archivo contradice a la spec, para y pregunta.

## Identidad del proyecto
- Nombre: **UltimateCalendar**
- `applicationId`: `com.qtekfun.ultimatecalendar`
- Repositorio: `github.com/qtekfun/UltimateCalendar`, rama principal **`master`** (protegida: solo se entra por PR con la CI en verde)
- Licencia: **GPL-3.0-or-later** (cabecera SPDX en cada archivo fuente, también yml, toml, kts, manifest y md)
- Idiomas de la UI: inglés (por defecto) y español. **Ninguna cadena visible va hardcodeada**: todo en `strings.xml` (`values/` y `values-es/`).

## Stack (no cambiar sin preguntar)
- Kotlin, Jetpack Compose, Material 3 (colores dinámicos + modo oscuro + AMOLED)
- `minSdk` 26, `targetSdk` el último estable. Subir `minSdk` solo si algo lo bloquea, y dejarlo anotado en `SPEC.md`.
- Arquitectura: MVVM + capas `ui` / `domain` / `data` / `sync`, flujo de datos unidireccional (StateFlow)
- Fuentes de calendario detrás de `CalendarSource` (`data/source/`): `ProviderCalendarSource` (CalendarContract) y, en la fase 6, `CalDavCalendarSource`. La UI y `domain` nunca usan `ContentResolver` ni la red directamente.
- Inyección: Hilt · Persistencia propia: Room · Segundo plano: WorkManager · Avisos: AlarmManager
- Gradle con Kotlin DSL y catálogo de versiones (`gradle/libs.versions.toml`), mismas versiones que UltimateTasks al arrancar (JDK 21 para Gradle, bytecode 17).

## Reutilización de UltimateTasks
- Se **copian y adaptan** (no se comparten como librería): configuración Gradle y de calidad, tema, asistente de fiabilidad, planificador de avisos, recuperación de avisos perdidos, latido, modo robusto, `BootReceiver`, editor de repeticiones, ajustes, copia de seguridad cifrada, Screenshots, release y receta F-Droid; en la fase 6, cliente CalDAV, iCalendar, Login Flow v2, cola y resolutor.
- Al copiar, se copian también sus tests. Se adaptan paquete, nombres y cadenas; nada de código muerto específico de tareas.

## Reglas de software libre (F-Droid) — innegociables
- **Prohibido**: Firebase, Google Play Services (también para leer Google Calendar: se lee del proveedor de Android), Crashlytics, analíticas, SDKs propietarios, cualquier dependencia no libre.
- **Prohibido** telemetría de ningún tipo.
- Antes de añadir una dependencia: comprueba su licencia (compatible con GPLv3) y **pregunta al usuario**.
- Metadatos de publicación en formato fastlane: `fastlane/metadata/android/{en-US,es-ES}/`.
- Builds reproducibles: sin timestamps ni valores no deterministas en el build.

## Proveedor de calendario: reglas
- Escribe como cliente normal, **nunca** con `CALLER_IS_SYNCADAPTER` fuera de los tests.
- No toques columnas de sincronización (`_SYNC_ID`, `SYNC_DATA*`, `CAL_SYNC*`) ni propiedades extendidas de otras apps.
- Lee rangos con `Instances`; nunca expandas repeticiones en la UI.
- En el teléfono del autor, prueba solo con **calendarios de prueba creados para ello**; no crees, edites ni respondas eventos reales.

## Comandos
- Build debug: `./gradlew assembleDebug`
- Tests unitarios: `./gradlew testDebugUnitTest`
- Tests de UI en el móvil (sin desinstalar la app): `./gradlew installDebug installDebugAndroidTest` y `adb shell am instrument -w com.qtekfun.ultimatecalendar.test/com.qtekfun.ultimatecalendar.HiltTestRunner`. **Nunca** `connectedDebugAndroidTest` en el móvil del autor: desinstala la app y borra sus datos.
- Lint y estilo: `./gradlew detekt ktlintCheck lintDebug`
- Cobertura: `./gradlew koverVerify koverHtmlReport`
- Todo lo anterior (lo que corre la CI): `./gradlew check`

## Calidad y tests
- Cada tarea termina con `./gradlew check` en verde. No marques una tarea como hecha si falla.
- Stack de tests: JUnit5 + MockK, Turbine (flows), Room en memoria, MockWebServer (fase 6), tests de UI con Compose solo en flujos clave.
- **Arnés del proveedor**: toda funcionalidad de `CalendarSource` se prueba en la suite de contrato `CalendarSourceContract`, que corre contra `FakeCalendarSource` (unitarios) y contra el proveedor real del emulador (instrumentados, cuenta local de pruebas). Si el fake y el proveedor real no se comportan igual, el fake está mal: arréglalo. Fixtures de filas reales de Google y DAVx5 en `app/src/test/resources/provider-fixtures/`, anonimizadas.
- **Cobertura (Kover):**
  - Umbral global mínimo **85 %** sobre `domain`, `data` y `sync`.
  - **100 % obligatorio** en: detector de invitaciones, planificador de avisos, recuperación de avisos perdidos, división de repeticiones y, en la fase 6, cola, resolutor y expansión de recurrencias.
  - Excluido de la medición: código generado (Hilt, Room), `@Preview`, UI Compose pura.
  - **Nunca escribas tests vacíos o tautológicos** para subir el número. Un test debe poder fallar por una razón real.
- Fechas con `java.time` y un `Clock` inyectable; nunca `System.currentTimeMillis()` directo en lógica testeable. Tests de zonas horarias y cambio de horario de verano en todo lo que planifica.
- Warnings de Kotlin y Lint tratados como errores.

## Flujo de trabajo
- Trabaja **una tarea de `PLAN.md` cada vez**, en una rama `feat/<tarea>` (o `fix/…`) desde `master`.
- Empieza en modo plan: propón el enfoque y espera confirmación antes de tocar código.
- Commits siguiendo **Conventional Commits** (`feat:`, `fix:`, `perf:`, `test:`, `refactor:`, `docs:`, `build:`, `ci:`, `chore:`), pequeños y atómicos. **El título de la PR también**, porque se fusiona con squash y ese es el commit que queda en `master`.
- No hagas `git push --force`, no reescribas historia compartida, no hagas commit ni push a `master`. Los hooks de `.claude/` lo bloquean; no intentes saltártelos.
- Abre la PR con `gh pr create` rellenando la plantilla; espera la CI y arregla lo que falle. Cuando todos los checks pasen, fusiona tú con `gh pr merge --squash` (decisión del usuario, 2026-10-06); si algo falla, no fusiones.
- Al terminar cada tarea: resume en 2-3 líneas qué se hizo y qué queda; marca la tarea en `PLAN.md` (con `*Resultado:*` si algo cambió respecto al plan) y anota decisiones en `SPEC.md` §9 con fecha y tarea.
- Si la spec es ambigua o falta información: **pregunta**, no inventes.

## Versiones y releases
- Las releases son manuales, como en UltimateDeck (ver `RELEASING.md`): `appVersion` y `CHANGELOG.md` solo cambian en una PR de release (`chore: release X.Y.Z`, rama `release/X.Y.Z`), que además lleva los textos de tienda `fastlane/metadata/android/{en-US,es-ES}/changelogs/<versionCode>.txt` (≤ 500 caracteres; la CI los exige en esas ramas). En el resto de PRs no se toca `appVersion`.
- El tag `vX.Y.Z` lo crea y sube el autor; el workflow `Release` construye y publica el APK firmado.

## Convenciones de código
- Un archivo por clase pública relevante; paquetes por feature dentro de cada capa.
- Sin lógica de negocio en composables ni en ViewModels pesados: va en `domain` (invitaciones, avisos, repeticiones, disposición de eventos solapados).
- Inmutabilidad por defecto (`val`, `data class`, colecciones inmutables).
- Errores de IO modelados con tipos sellados (`CalendarResult`), no con excepciones sueltas hacia la UI.
- Todo el acceso al proveedor, Room y red fuera del hilo principal (Dispatchers inyectables).
- Los secretos (fase 6) se guardan cifrados con Android Keystore; nunca en logs ni en texto plano. Ni títulos de eventos ni correos en logs.
- Accesibilidad: `contentDescription`, tamaños táctiles mínimos de 48 dp, soporte de fuente grande.
- Room: cambio de esquema = nueva versión + migración + test de migración.

## Qué NO hacer
- No implementes nada marcado como "Fuera de alcance" en `SPEC.md`.
- No cambies versiones de dependencias manualmente: lo gestiona Dependabot.
- No desactives ni relajes detekt, ktlint, Lint, Kover, la verificación de dependencias, los workflows ni el ruleset para que pase la CI.
- No uses capturas con datos reales para la tienda ni el README: salen del test `Screenshots` con datos inventados.
