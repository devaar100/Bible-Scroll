plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

/**
 * Crashlytics is release-only and opt-in per checkout: it switches on once the Firebase config is
 * placed at app/src/release/google-services.json. Without it, release builds carry no Firebase code.
 */
val crashlyticsEnabled = file("src/release/google-services.json").exists()
if (crashlyticsEnabled) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

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
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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

    // Coil
    implementation(libs.coil.compose)

    // Study content (bundled + downloaded studies, CDN, weekly refresh)
    implementation(project(":core:content"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    // Installs the Baseline Profile on sideloaded/non-Play installs too.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.leakcanary.android)

    if (crashlyticsEnabled) {
        releaseImplementation(platform(libs.firebase.bom))
        releaseImplementation(libs.firebase.crashlytics)
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
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
