// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

tasks.register("printVersionName") {
    doLast {
        println("1.0.0")
    }
}
