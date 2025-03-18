plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)

    id("com.google.gms.google-services") // Добавляем Google Services Plugin
}

android {
    namespace = "com.example.try2"
    compileSdk = 34

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
        // Используем BoM, чтобы автоматически подтягивались совместимые версии
        implementation(platform(libs.firebase.bom))
        implementation (libs.firebase.auth.ktx)
        // Добавляем Firebase Realtime Database
        implementation(libs.google.firebase.database.ktx)

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

        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
}
