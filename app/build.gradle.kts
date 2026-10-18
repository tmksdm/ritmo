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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Первый устанавливаемый APK подписывается debug-ключом.
            // Для публикации в магазинах потребуется отдельный release-ключ.
            signingConfig = signingConfigs.getByName("debug")
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
