import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

/**
 * Увеличивает версию проекта по схеме ггммдд.номер.
 *
 * Правила:
 * - если в version.properties другая дата (или файла нет) — начинается новый день, номер 1;
 * - если дата совпадает с сегодняшней — номер увеличивается (261018 -> 261018.2 -> 261018.3);
 * - номер 1 в имя версии не попадает: первая версия дня называется просто 261018.
 */
tasks.register("newVersion") {
    group = "versioning"
    description = "Увеличивает версию в version.properties по схеме ггммдд.номер"

    val versionFile = rootProject.file("version.properties")
    val today = SimpleDateFormat("yyMMdd", Locale.US).format(Date())

    doLast {
        val properties = Properties()
        if (versionFile.exists()) versionFile.inputStream().use { properties.load(it) }

        val storedDate = properties.getProperty("versionDate", "")
        val storedNumber = properties.getProperty("versionNumber", "1").toIntOrNull() ?: 1

        val newDate: String
        val newNumber: Int
        if (storedDate == today) {
            newDate = today
            newNumber = (storedNumber + 1).coerceAtMost(99)
        } else {
            newDate = today
            newNumber = 1
        }

        properties.setProperty("versionDate", newDate)
        properties.setProperty("versionNumber", newNumber.toString())
        versionFile.outputStream().use {
            properties.store(it, "Ritmo version scheme: yyMMdd.number (first version of a day has no number)")
        }

        val name = if (newNumber <= 1) newDate else "$newDate.$newNumber"
        logger.lifecycle("Версия: $name")
    }
}
