plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.csa.xreadpdf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.csa.xreadpdf"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Clé de signature fixe (secrets GitHub XREADPDF_KEYSTORE / XREADPDF_KEY_PASSWORD) :
    // indispensable pour que chaque nouvelle version s'installe par-dessus la précédente.
    val fixedKeystore = System.getenv("XREADPDF_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (fixedKeystore != null) {
            create("fixe") {
                storeFile = fixedKeystore
                storePassword = System.getenv("XREADPDF_KEY_PASSWORD")
                keyAlias = "xreadpdf"
                keyPassword = System.getenv("XREADPDF_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (fixedKeystore != null) signingConfig = signingConfigs.getByName("fixe")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Scanner de documents Google (caméra, détection des bords, recadrage, filtres, export PDF)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // Écriture PDF (annotations, signatures, pages) — portage Android d'Apache PDFBox
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // OCR sur l'appareil (texte des scans cherchable) — modèle fourni par les services Google Play
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

    // Verrouillage de l'app par empreinte / code de l'appareil
    implementation("androidx.biometric:biometric:1.1.0")

    // Tests sur émulateur (CI)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
