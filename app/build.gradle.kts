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
        versionName = "1.2"
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
    implementation("androidx.wear:wear-remote-interactions:1.2.0")
    implementation("androidx.core:core:1.19.1")
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
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.health:health-services-client:1.1.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.3.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    
    // Wear OS Tiles & Complications
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.wear.protolayout:protolayout-material:1.4.2")
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.3.0")
    implementation("androidx.wear.tiles:tiles-tooling-preview:1.6.2")

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)

    testImplementation("junit:junit:4.13.2")

    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)

    constraints {
        implementation("androidx.fragment:fragment:1.9.1") {
            because("Update outdated transitive Fragment dependency")
        }
    }
}