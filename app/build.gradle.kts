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
        versionCode = 1
        versionName = "1.0"
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
                enable = false
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
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)

    testImplementation("junit:junit:4.13.2")

    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}