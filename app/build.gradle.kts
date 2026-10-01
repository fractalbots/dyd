import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dyd.contable"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dyd.contable"
        minSdk = 26
        // Google Play exige apuntar a la versión de Android más reciente (API 36 desde agosto de 2026).
        targetSdk = 36
        // Sube versionCode en cada publicación (o pásalo con -PversionCode=N desde GitHub Actions).
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "2.0.0"

        // Credenciales públicas de Supabase, leídas de local.properties (no se suben a git).
        val props = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField("String", "SUPABASE_URL", "\"${props.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${props.getProperty("SUPABASE_ANON_KEY", "")}\"")
        // Dirección de tu web publicada (p. ej. https://dyd.netlify.app): la app enlaza a /privacidad y /eliminar-cuenta.
        buildConfigField("String", "WEB_URL", "\"${props.getProperty("WEB_URL", "").trimEnd('/')}\"")
    }

    // Firma de publicación: keystore.properties (no se sube a git) o variables de entorno en GitHub Actions.
    val keystore = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signing(key: String, env: String): String? = keystore.getProperty(key) ?: System.getenv(env)
    val storeFilePath = signing("storeFile", "DYD_KEYSTORE_FILE")
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = signing("storePassword", "DYD_KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "DYD_KEY_ALIAS")
                keyPassword = signing("keyPassword", "DYD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (storeFilePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
}
