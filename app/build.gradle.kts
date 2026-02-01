// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.dancetrainer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.dancetrainer"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

// AUTOMATION: Copy APK to specific folder after build
tasks.register<Copy>("publishApk") {
    group = "publishing"
    description = "Copies the release APK to the server distribution folder."

    // 1. Where the file is created by Android Studio
    from(layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk")) 
    
    // 2. Where you want it to go (Using forward slashes for stability)
    into("Y:/apps/DanceTrainer")
    
    // 3. Rename it
    rename { "dancetrainer.apk" }

    // Ensure it only runs after the build is actually done
    dependsOn("assembleRelease")
}

// AUTOMATION: Update the version.txt on the server automatically
tasks.register("publishVersion") {
    group = "publishing"
    description = "Updates the version.txt on the server."
    doLast {
        val versionFile = file("Y:/apps/DanceTrainer/version.txt")
        versionFile.writeText(android.defaultConfig.versionCode.toString())
        println("Successfully updated server version to: ${android.defaultConfig.versionCode}")
    }
    // This task runs AFTER the APK is copied
    dependsOn("publishApk")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.youtube.player.core)
    implementation(libs.glide)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}