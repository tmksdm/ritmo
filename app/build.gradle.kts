import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Схема версий проекта: ггмм.дд.номер. Первая сборка дня — без номера (например 2610.18),
// последующие — 2610.18.2, 2610.18.3 и так далее. Инкремент: ./gradlew newVersion
val versionProperties = Properties().apply {
    val file = rootProject.file("version.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val versionDate = versionProperties.getProperty("versionDate", "260101") // ггммдд
val versionNumber = versionProperties.getProperty("versionNumber", "1").toIntOrNull() ?: 1
// 261018 -> 2610.18
val versionDay = if (versionDate.length == 6) {
    "${versionDate.substring(0, 4)}.${versionDate.substring(4, 6)}"
} else {
    versionDate
}
val ritmoVersionName = if (versionNumber <= 1) versionDay else "$versionDay.$versionNumber"
val ritmoVersionCode = (versionDate.toIntOrNull() ?: 260101) * 100 + versionNumber

// Постоянная подпись релизов. keystore.properties в git не попадает,
// сам ключ лежит вне репозитория. В CI ключ и пароли приходят только через
// переменные окружения, чтобы не создавать файл с секретами в рабочей копии.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
if (keystorePropertiesFile.exists()) {
    val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .filter { keystoreProperties.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        throw GradleException("В keystore.properties нет полей: ${missing.joinToString()}.")
    }
}
val envKeystoreFile = System.getenv("RITMO_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val envKeystore = envKeystoreFile?.let { file(it) }
val requireReleaseSigning = System.getenv("RITMO_REQUIRE_RELEASE_SIGNING")
    ?.equals("true", ignoreCase = true) == true
val hasReleaseSigning = envKeystore != null || keystorePropertiesFile.exists()

fun requiredEnv(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: throw GradleException("Не задано окружение $name для подписи release")

if (envKeystore != null && !envKeystore.isFile) {
    throw GradleException("RITMO_KEYSTORE_FILE указывает на отсутствующий файл ключа")
}

if (requireReleaseSigning && !hasReleaseSigning) {
    throw GradleException(
        "Release-подпись обязательна: задайте RITMO_KEYSTORE_FILE, RITMO_KEYSTORE_PASSWORD, " +
            "RITMO_KEY_ALIAS и RITMO_KEY_PASSWORD либо добавьте keystore.properties. " +
            "Публикация APK с debug-подписью запрещена."
    )
}

android {
    namespace = "com.tmksdm.ritmo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tmksdm.ritmo"
        minSdk = 26
        targetSdk = 35
        versionCode = ritmoVersionCode
        versionName = ritmoVersionName
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                if (envKeystore != null) {
                    storeFile = envKeystore
                    storePassword = requiredEnv("RITMO_KEYSTORE_PASSWORD")
                    keyAlias = requiredEnv("RITMO_KEY_ALIAS")
                    keyPassword = requiredEnv("RITMO_KEY_PASSWORD")
                } else {
                    storeFile = file(keystoreProperties.getProperty("storeFile"))
                    storePassword = keystoreProperties.getProperty("storePassword")
                    keyAlias = keystoreProperties.getProperty("keyAlias")
                    keyPassword = keystoreProperties.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Без keystore.properties (например, при клонировании репозитория)
            // используется debug-подпись, чтобы сборка не падала.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        buildConfig = true
    }
}

dependencies {
    // Только Kotlin stdlib, всё остальное — стандартный Android SDK.
}
