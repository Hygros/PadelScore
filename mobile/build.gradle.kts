plugins {
    alias(libs.plugins.android.application)
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
    namespace = "ch.hygro.padelscore.sync"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        applicationId = "ch.hygro.padelscore"
        minSdk = 28
        targetSdk = 37
        versionCode = providers.gradleProperty("padelscoreMobileVersionCode").orNull?.toInt() ?: 13
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

            if (hasUploadSigningConfiguration) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
dependencies {
    implementation(libs.wear.remote.interactions)
    implementation(libs.concurrent.futures.ktx)
    testImplementation(libs.junit)
    implementation(libs.activity)
    implementation(libs.health.connect.client)
    implementation(libs.play.services.wearable)
    constraints {
        implementation(libs.fragment) {
            because("Activity Result APIs require Fragment 1.3.0 or newer; align with the wear app")
        }
    }
}
