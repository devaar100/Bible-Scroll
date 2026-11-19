plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.licensee)
}

/** S7 support address. Replace the placeholder before a release; release builds stop while it's unset. */
val supportEmail = "<YOUR ADDRESS>"
val supportEmailUnset = !supportEmail.contains('@')

android {
    namespace = "aarnav100.developer.biblescroll"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "aarnav100.developer.biblescroll"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // S7 "Report a problem" / "Send feedback": the address reports are emailed to. Defined here only.
        buildConfigField("String", "SUPPORT_EMAIL", "\"$supportEmail\"")
    }

    buildTypes {
        debug {
            // en-XA (accented, ~30-40% longer) and ar-XB (right-to-left) for layout checks: pick them in system
            // Settings → Languages with developer options on, or see PseudoLocaleScreenshotTest. Debug only.
            isPseudoLocalesEnabled = true
        }
        release {
            // R8 + resource shrinking. The bundle carries the R8 mapping (BUNDLE-METADATA/.../proguard.map),
            // so Play Console deobfuscates crash stack traces. No crash-reporting SDK (docs/decisions.md).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Release code (R8, shrinking, no StrictMode, no debug tools) for end-to-end checks on a phone:
        // debug-signed, installs beside the store app (separate data). Community shows test- ministries
        // too; vote only on those, since real counters are shared. Not gated on supportEmail. See docs/qa.md.
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("boolean", "QA", "true")
        }
        all { if (name != "qa") buildConfigField("boolean", "QA", "false") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates StrictMode. Not a source set, because the baseline-profile build
        // types (benchmarkRelease, nonMinifiedRelease) don't see src/release.
        buildConfig = true
    }
}

// S8 Legal → Open-source libraries. Licensee runs at build time only: it reads every runtime dependency's POM,
// fails the build on a licence not allowed below, and bundles the list as an asset (app/cash/licensee/artifacts.json).
// No runtime library: the app parses that JSON itself.
licensee {
    allow("Apache-2.0")
    allow("BSD-3-Clause")
    bundleAndroidAsset.set(true)
}

baselineProfile {
    // Profiles are generated on demand (./gradlew :app:generateBaselineProfile), not on every release build.
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
}

composeCompiler {
    // java.time values and the read-only Bible models are immutable; tell the compiler so.
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
}

room {
    // Exported schemas let us write proper migrations for the user DB later.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation (type-safe routes via kotlinx.serialization)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Glance widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Custom Tabs (Community: ministry websites and "Give directly")
    implementation(libs.androidx.browser)

    // Coil
    implementation(libs.coil.compose)

    // Study content (bundled + downloaded studies, CDN, weekly refresh)
    implementation(project(":core:content"))
    implementation(project(":core:plans"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler) // @HiltWorker in the app (CommunityVoteWorker)

    // Installs the Baseline Profile on sideloaded/non-Play installs too.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.retrofit) // fakes for :core:content's FirestoreApi (Retrofit Response)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// Compose compiler stability reports: ./gradlew assembleRelease -PcomposeReports=true
// Output in app/build/compose_compiler/ (*-classes.txt, *-composables.txt, *-module.json).
if (providers.gradleProperty("composeReports").orNull == "true") {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

// Don't ship a release whose "Report a problem" emails go nowhere.
// Only the shipping build type: benchmark and baseline-profile builds keep working.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (supportEmailUnset) {
            throw GradleException("Set supportEmail in app/build.gradle.kts (S7 support address) before building a release.")
        }
    }
}
