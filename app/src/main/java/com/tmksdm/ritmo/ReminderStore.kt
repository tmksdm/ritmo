package com.tmksdm.ritmo

import android.content.Context
import android.util.AtomicFile
import java.io.File

/**
 * Локальное хранилище напоминаний: один JSON-файл во внутреннем каталоге приложения.
 * Запись идёт через AtomicFile, поэтому основной файл не может быть повреждён при сбое.
 */
object ReminderStore {

    private const val FILE_NAME = "reminders.json"

    fun load(context: Context): MutableList<Reminder> {
        val file = atomicFile(context)
        if (!file.baseFile.exists()) return mutableListOf()
        return try {
            file.openRead().use { stream -> Reminder.listFromJson(stream.reader().readText()) }
        } catch (e: Exception) {
            // Повреждённый файл сохраняем отдельно, чтобы данные не пропали молча.
            runCatching {
                file.baseFile.copyTo(File(context.filesDir, "$FILE_NAME.corrupt"), overwrite = true)
            }
            mutableListOf()
        }
    }

    /**
     * Сохраняет напоминания. Возвращает false, если запись не удалась: вызывающий код
     * обязан сообщить об ошибке и не показывать успешный результат.
     */
    fun save(context: Context, reminders: List<Reminder>): Boolean {
        val file = atomicFile(context)
        val data = Reminder.listToJson(reminders).toByteArray()
        var stream: java.io.FileOutputStream? = null
        return try {
            stream = file.startWrite()
            stream.write(data)
            file.finishWrite(stream)
            true
        } catch (e: Exception) {
            if (stream != null) runCatching { file.failWrite(stream) }
            false
        }
    }

    private fun atomicFile(context: Context) = AtomicFile(File(context.filesDir, FILE_NAME))
}
