// Plan splitting shared with the pipeline (pipeline/src/pipeline/plans/splitting.py is the reference).
// Pure Kotlin, no Android APIs: an Android library only so it builds like the other modules.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "aarnav100.developer.biblescroll.core.plans"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
