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
        versionCode = 11
        versionName = "1.1"
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
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    constraints {
        implementation("androidx.fragment:fragment:1.9.1") {
            because("Activity Result APIs require Fragment 1.3.0 or newer; align with the wear app")
        }
    }
}
