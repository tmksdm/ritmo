package com.tmksdm.ritmo

import android.content.Context
import java.io.File

/**
 * Локальное хранилище напоминаний: один JSON-файл во внутреннем каталоге приложения.
 * Запись выполняется через временный файл, чтобы не потерять данные при сбое.
 */
object ReminderStore {

    private const val FILE_NAME = "reminders.json"

    fun load(context: Context): MutableList<Reminder> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return mutableListOf()
        return try {
            Reminder.listFromJson(file.readText())
        } catch (e: Exception) {
            // Повреждённый файл сохраняем отдельно, чтобы данные не пропали молча.
            runCatching { file.renameTo(File(context.filesDir, "$FILE_NAME.corrupt")) }
            mutableListOf()
        }
    }

    fun save(context: Context, reminders: List<Reminder>) {
        val file = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        val data = Reminder.listToJson(reminders)
        temp.writeText(data)
        if (!temp.renameTo(file)) {
            file.writeText(data)
            temp.delete()
        }
    }
}
