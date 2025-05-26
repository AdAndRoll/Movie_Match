plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)

    id("com.google.gms.google-services") // Добавляем Google Services Plugin
    alias(libs.plugins.kotlin.serialization) // Только через alias
}

android {
    namespace = "com.example.try2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.try2"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

    dependencies {

        implementation (libs.androidx.work.runtime.ktx)

        implementation (libs.androidx.lifecycle.process)
        implementation (libs.postgrest.kt)
        implementation (libs.realtime.kt)
        implementation (libs.gotrue.kt)

        implementation (libs.kotlinx.serialization.json)

        implementation(libs.kotlin.stdlib)

        implementation (libs.ktor.client.okhttp)

        implementation(libs.serialization.converter)
        implementation(libs.converter.gson)
        implementation(libs.retrofit)
        implementation(libs.picasso)
        implementation(libs.logging.interceptor)
        implementation(libs.okhttp)
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.material)
        implementation(libs.androidx.activity)
        implementation(libs.androidx.constraintlayout)
        implementation(libs.androidx.recyclerview)
        implementation(libs.androidx.room.common.jvm)
        implementation(libs.androidx.lifecycle.runtime.android)

        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
}
