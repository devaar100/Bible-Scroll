// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 has built-in Kotlin; declaring KGP here (not applied) pins the Kotlin compiler version
    // to the catalog's `kotlin` version instead of the older one AGP bundles.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
