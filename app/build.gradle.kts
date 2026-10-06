// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import io.gitlab.arturbosch.detekt.Detekt
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverReportFilter
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.licensee)
    alias(libs.plugins.room)
}

/**
 * The app version lives in one place, `appVersion` in gradle.properties (SemVer, optionally
 * `-rc.N`). The version code is derived from it, so it never depends on dates or the machine:
 * MAJOR.MINOR.PATCH-rc.N -> (MAJOR*10000 + MINOR*100 + PATCH) * 100 + N, and 99 for a final
 * release, which therefore sorts after its release candidates.
 */
val appVersion = providers.gradleProperty("appVersion").get()

fun versionCodeOf(version: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?""").matchEntire(version)
        ?: error("appVersion must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rc.N: $version")
    val (major, minor, patch, rc) = match.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100 && (rc.isEmpty() || rc.toInt() in 1..98))
    val base = major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
    return base * 100 + (rc.toIntOrNull() ?: 99)
}

/** Release signing from the environment (CI secrets); without it the release APK is unsigned. */
val releaseKeystore: String? = System.getenv("UC_KEYSTORE_FILE")

android {
    namespace = "com.qtekfun.ultimatecalendar"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.qtekfun.ultimatecalendar"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        testInstrumentationRunner = "com.qtekfun.ultimatecalendar.HiltTestRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("UC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UC_KEY_ALIAS")
                keyPassword = System.getenv("UC_KEY_PASSWORD")
            }
        }
    }

    // Reproducible builds (F-Droid): no Google-encrypted dependency blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // The git commit is not part of the APK: a build from a source tarball must match.
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // The version shown at the foot of Settings.
        buildConfig = true
    }

    testOptions {
        // The key-flow UI tests wait on idling, not on timers: no animation may be running.
        animationsDisabled = true
        unitTests.all {
            // The T25 micro-benchmarks carry the "benchmark" tag; -PskipBenchmarks leaves them out
            // should they ever flake on a loaded machine.
            if (project.hasProperty("skipBenchmarks")) {
                it.useJUnitPlatform { excludeTags("benchmark") }
            } else {
                it.useJUnitPlatform()
            }
        }
    }

    // FakeCalendarSource and the CalendarSourceContract suite run in the unit tests (against the
    // fake) and in the instrumented tests (against the real provider): one copy, two source sets.
    sourceSets {
        listOf("test", "androidTest").forEach { name ->
            getByName(name).kotlin.directories.add("src/sharedTest/java")
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
    }

    androidResources {
        generateLocaleConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(
        "src/main/java",
        "src/debug/java",
        "src/test/java",
        "src/sharedTest/java",
        "src/androidTest/java"
    )
}

tasks.withType<Detekt>().configureEach {
    // Match the project bytecode level; detekt defaults to the JDK running Gradle.
    jvmTarget = "17"
}

ktlint {
    version.set(libs.versions.ktlint)
}

// Coverage policy (CLAUDE.md): >= 85% over domain/data/sync, 100% on the invitation detector,
// the reminder planner, lost-reminder recovery and recurrence splitting. Generated code and pure
// Compose UI are excluded. Phase 6 adds the CalDAV queue, resolver and recurrence expansion.
val coveredPackages = listOf(
    "com.qtekfun.ultimatecalendar.domain",
    "com.qtekfun.ultimatecalendar.data",
    "com.qtekfun.ultimatecalendar.sync"
)
val criticalPackages = listOf(
    "com.qtekfun.ultimatecalendar.domain.invitations",
    "com.qtekfun.ultimatecalendar.domain.reminders",
    "com.qtekfun.ultimatecalendar.domain.recurrence"
)

/**
 * Generated code and pure Compose UI, excluded from coverage (CLAUDE.md). Applied to each report
 * variant: variant filters replace the global ones instead of adding to them.
 */
fun KoverReportFilter.generatedAndUiCode() {
    packages("com.qtekfun.ultimatecalendar.ui", "dagger.hilt.internal", "hilt_aggregated_deps")
    classes(
        "*.R",
        "*.R$*",
        "*.BuildConfig",
        "*Hilt_*",
        "*_HiltModules*",
        "*_Factory",
        "*_Factory$*",
        "*_MembersInjector",
        // Room
        "*_Impl",
        "*_Impl$*",
        // Kotlin compatibility bridges for interface default methods
        "*\$DefaultImpls",
        "*ComposableSingletons*"
    )
    annotatedBy(
        "androidx.compose.ui.tooling.preview.Preview",
        "dagger.Module",
        "dagger.hilt.android.HiltAndroidApp",
        "*Generated*"
    )
}

kover {
    currentProject {
        createVariant("critical") {
            add("debug")
        }
    }

    reports {
        total {
            filters {
                excludes { generatedAndUiCode() }
                includes {
                    packages(coveredPackages)
                }
            }
            verify {
                rule("domain, data and sync") {
                    minBound(85)
                }
            }
        }

        variant("critical") {
            filters {
                excludes { generatedAndUiCode() }
                includes {
                    packages(criticalPackages)
                }
            }
            verify {
                rule("invitations, reminders and recurrence") {
                    minBound(100, CoverageUnit.LINE)
                    minBound(100, CoverageUnit.BRANCH)
                }
            }
        }
    }
}

tasks.named("koverVerify") {
    dependsOn("koverVerifyCritical")
}

tasks.named("check") {
    dependsOn("koverVerify")
}

// Only GPL-3.0-compatible free licenses may ship in the APK. Anything else,
// including dependencies without a declared license, fails the build.
// Add other GPL-3.0-compatible SPDX ids (MIT, BSD-2-Clause, BSD-3-Clause,
// ISC...) only when a dependency needs them; licensee warns about unused ones.
licensee {
    allow("Apache-2.0")
}

// Google Play Services, Firebase and Crashlytics are banned outright (F-Droid
// rules in CLAUDE.md), regardless of what license they declare.
val checkForbiddenDependencies = tasks.register("checkForbiddenDependencies") {
    group = "verification"
    description =
        "Fails if a runtime classpath contains Google Play Services, Firebase or Crashlytics."
    val forbiddenGroupPrefixes = listOf(
        "com.google.android.gms",
        "com.google.firebase",
        "com.crashlytics",
        "io.fabric"
    )
    val runtimeModules = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").map { name ->
        configurations.named(name).flatMap { it.incoming.resolutionResult.rootComponent }
    }
    doLast {
        val modules = mutableSetOf<String>()
        val seen = mutableSetOf<ResolvedComponentResult>()
        val pending = ArrayDeque(runtimeModules.map { it.get() })
        while (pending.isNotEmpty()) {
            val component = pending.removeFirst()
            if (seen.add(component)) {
                (component.id as? ModuleComponentIdentifier)?.let {
                    modules.add(it.moduleIdentifier.toString())
                }
                component.dependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .forEach { pending.add(it.selected) }
            }
        }
        val offenders = modules
            .filter { module -> forbiddenGroupPrefixes.any { module.startsWith(it) } }
            .sorted()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Forbidden non-free dependencies found: ${offenders.joinToString()}"
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkForbiddenDependencies)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // UI tests, run on a device with installDebug installDebugAndroidTest + adb instrument.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // Compose's test rule brings Espresso 3.5, which calls an API Android 17 removed.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    // Host JVM build of the bundled SQLite, so Room runs in local unit tests.
    testImplementation(libs.sqlite.bundled.jvm)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.mockwebserver.junit5)
}
