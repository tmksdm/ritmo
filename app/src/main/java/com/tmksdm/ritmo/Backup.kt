package com.tmksdm.ritmo

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ручные резервные копии: экспорт в JSON и восстановление из файла. */
object Backup {

    private const val TAG = "RitmoBackup"
    private const val MAX_BYTES = 5 * 1024 * 1024

    enum class Error { NOT_JSON, TOO_BIG, READ_FAILED, WRITE_FAILED }

    class BackupException(val reason: Error) : Exception(reason.name)

    /** Понятное имя файла, например ritmo-backup-2026-10-18.json. */
    fun fileName(now: Long = System.currentTimeMillis()): String =
        "ritmo-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))}.json"

    fun toJson(reminders: List<Reminder>): String = Reminder.listToJson(reminders)

    /** @throws BackupException если файл не является корректной резервной копией. */
    fun parse(text: String): MutableList<Reminder> = try {
        Reminder.listFromBackup(text)
    } catch (e: Exception) {
        Log.w(TAG, "Резервная копия не разобрана", e)
        throw BackupException(Error.NOT_JSON)
    }

    /** @throws BackupException если файл не удалось прочитать или он слишком большой. */
    fun readText(resolver: ContentResolver, uri: Uri): String {
        val stream = try {
            resolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось открыть файл копии", e)
            null
        } ?: throw BackupException(Error.READ_FAILED)

        return stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_BYTES) throw BackupException(Error.TOO_BIG)
                output.write(buffer, 0, read)
            }
            output.toString("UTF-8")
        }
    }

    /**
     * Заменяет набор напоминаний восстановленным: старые будильники отменяются,
     * новые планируются только на будущее. При ошибке записи данные не меняются.
     */
    fun restore(context: Context, imported: List<Reminder>): Boolean {
        val previous = ReminderStore.load(context)
        if (!ReminderStore.save(context, imported)) return false
        // Определения тегов хранятся отдельно; принадлежность — в напоминаниях.
        // После импорта добавляем недостающие названия, не удаляя существующие.
        val tags = Tags.load(context)
        var tagsChanged = false
        for (reminder in imported) {
            for (tag in reminder.tags) {
                if (TagNames.add(tags, tag)) tagsChanged = true
            }
        }
        if (tagsChanged) Tags.save(context, tags)
        (previous + imported).distinctBy { it.id }.forEach { AlarmScheduler.cancel(context, it) }
        imported.filter { it.enabled }.forEach { AlarmScheduler.schedule(context, it) }
        return true
    }
}
