plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ayushpant.habitquest"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ayushpant.habitquest"
        minSdk = 29 // Android 10 (spec section 4)
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0-step3"
    }

    // Release key for APKs shared with friends. The key and passwords live outside the project,
    // in ~/.gradle/gradle.properties (HABITQUEST_*), so they never go into git.
    val storeFilePath = providers.gradleProperty("HABITQUEST_STORE_FILE").orNull
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("HABITQUEST_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("HABITQUEST_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("HABITQUEST_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true // needed to render screens in tests
    }
    buildFeatures {
        compose = true
        buildConfig = true // gives BuildConfig.VERSION_NAME for the Settings screen
    }
}

ksp {
    // Room writes the database schema here. Commit it: it is how migrations are checked.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service) // hosts the popup drawn over other apps
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric) // runs Android code (here: Room) on the Mac
    testImplementation(libs.androidx.test.core)
    // Renders screens to PNG on the Mac (SummaryRenderTest), to check the look without a phone
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
