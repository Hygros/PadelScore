plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val uploadStoreFile =
    providers.environmentVariable("PADELSCORE_UPLOAD_STORE_FILE").orNull

val uploadStorePassword =
    providers.environmentVariable("PADELSCORE_UPLOAD_STORE_PASSWORD").orNull

val uploadKeyAlias =
    providers.environmentVariable("PADELSCORE_UPLOAD_KEY_ALIAS").orNull

val uploadKeyPassword =
    providers.environmentVariable("PADELSCORE_UPLOAD_KEY_PASSWORD").orNull

val hasUploadSigningConfiguration =
    listOf(
        uploadStoreFile,
        uploadStorePassword,
        uploadKeyAlias,
        uploadKeyPassword
    ).all { value ->
        !value.isNullOrBlank()
    }

android {
    namespace = "ch.hygro.padelscore"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ch.hygro.padelscore"
        minSdk = 30
        targetSdk = 37
        versionCode = providers.gradleProperty("padelscoreWearVersionCode").orNull?.toInt() ?: 12
        versionName = "1.3"
    }

    signingConfigs {
        if (hasUploadSigningConfiguration) {
            create("upload") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
                storeType = "JKS"
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-release.pro")

            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }

            if (hasUploadSigningConfiguration) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    useLibrary("wear-sdk")

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.wear.ongoing)
    implementation(libs.wear.remote.interactions)
    implementation(libs.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.play.billing.ktx)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    implementation(libs.material.icons.core)
    implementation(libs.health.services.client)
    implementation(libs.concurrent.futures.ktx)
    implementation(libs.play.services.wearable)
    
    // Wear OS Tiles & Complications
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material)
    implementation(libs.wear.watchface.complications.data.source)
    implementation(libs.wear.tiles.tooling.preview)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)

    testImplementation(libs.junit)

    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)

    constraints {
        implementation(libs.fragment) {
            because("Update outdated transitive Fragment dependency")
        }
    }
}