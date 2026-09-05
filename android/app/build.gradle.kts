plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "il.co.tradesmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "il.co.tradesmanager"
        // Android 8.0 — the floor the tender documents ask for.
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"

        // Where the in-app update check looks. Read through BuildConfig so a
        // fork points at its own repository without touching Kotlin.
        buildConfigField(
            "String",
            "RELEASES_API",
            "\"https://api.github.com/repos/JO0Dile/Construction-trades/releases/latest\"",
        )
        buildConfigField(
            "String",
            "RELEASES_PAGE",
            "\"https://github.com/JO0Dile/Construction-trades/releases/latest\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The locales the app actually ships translations for. Adding a
        // language is: drop in values-<code>/strings.xml, add the code here
        // and to res/xml/locales_config.xml. No Kotlin changes.
        resourceConfigurations += listOf("en", "he", "ar")

        vectorDrawables.useSupportLibrary = true
    }

    // Gradle's own debug key is generated per machine, so an APK built by CI
    // could not install over one built by the last CI run — Android refuses a
    // signature change. This keystore is committed on purpose: its password is
    // the well-known Android debug one, it protects nothing, and a shared key
    // is the only way direct-download updates can work at all. It is NOT the
    // key a Play or App Store release is signed with.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            // The sideloaded build — the one handed round as a download —
            // checks for its own updates. See docs/UPDATES.md.
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        release {
            // Never in a store build. Google Play treats an app that
            // installs its own APK as Device and Network Abuse, and iOS does
            // not permit it at all; the permission that would make it work is
            // not even declared here (see src/debug/AndroidManifest.xml).
            buildConfigField("boolean", "SELF_UPDATE", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time on API 26+ needs no desugaring, but keep the switch
        // documented for anyone who lowers minSdk.
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // Keeps the APK under the 50 MB budget in the brief by splitting the
    // native SQLCipher payload per ABI.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    androidResources {
        // Hebrew/Arabic pseudolocale checks during QA.
        generateLocaleConfig = false
    }

    sourceSets {
        getByName("main") {
            // The trade catalogues live in shared/assets so the iOS target
            // bundles exactly the same files. Nothing is copied or generated:
            // Gradle merges the directory straight into the APK, so
            // shared/assets/catalog/... is read at runtime as "catalog/...".
            // Build-time-only shared sources (shared/i18n) stay outside it.
            assets.srcDirs("src/main/assets", "../../shared/assets")
        }
    }
}

// Room's exported schemas are committed so migrations can be diffed in review.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.sqlcipher.android)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
