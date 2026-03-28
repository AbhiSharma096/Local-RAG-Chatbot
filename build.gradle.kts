plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.legacy.kapt) apply false
}

buildscript {
    val objectboxVersion by extra("5.4.1")
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath("io.objectbox:objectbox-gradle-plugin:$objectboxVersion")
    }
}
