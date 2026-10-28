package com.tmksdm.ritmo

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException

/**
 * Теги: список названий в отдельном JSON-файле. Принадлежность хранится
 * в самих напоминаниях, поэтому отсутствие файла означает только отсутствие
 * объявлений. Старые группы (groups.json и поле group) переносятся один раз.
 */
object Tags {

    private const val FILE_NAME = "tags.json"
    private const val LEGACY_GROUPS_FILE = "groups.json"
    private const val PREFS = "ritmo_tags"
    private const val KEY_GROUPS_IMPORTED = "groups_imported_v1"
    private const val KEY_MIGRATED = "groups_migrated_v1"

    fun load(context: Context): MutableList<String> = readNames(context, FILE_NAME)

    fun save(context: Context, tags: List<String>): Boolean =
        writeNames(context, FILE_NAME, tags)

    /**
     * Сохраняет изменение определений и назначений. Сначала пишется tags.json:
     * при его ошибке напоминания не трогаются. Если не удалось сохранить
     * напоминания, прежние определения возвращаются, а результат — false.
     */
    fun saveEdit(
        context: Context,
        reminders: List<Reminder>,
        previousTags: List<String>,
        tags: List<String>
    ): Boolean {
        if (!save(context, tags)) return false
        if (ReminderStore.save(context, reminders)) return true
        save(context, previousTags)
        return false
    }

    /**
     * Одноразовый перенос пользовательских групп в теги. Не создаёт тег для
     * технической секции «Без группы»: у таких напоминаний group == null.
     */
    fun migrateLegacyGroups(context: Context, reminders: List<Reminder>) {
        val preferences = prefs(context)
        if (preferences.getBoolean(KEY_MIGRATED, false)) return
        val groupsImported = preferences.getBoolean(KEY_GROUPS_IMPORTED, false)
        val merged = load(context)
        if (!groupsImported) legacyGroupNames(context).forEach { TagNames.add(merged, it) }
        reminders.forEach { reminder -> reminder.tags.forEach { TagNames.add(merged, it) } }
        // Перезаписываем напоминания только при наличии старого поля group и только
        // если файл читается: повреждённый reminders.json не затираем молча.
        // При ошибке миграция не завершена, теги не сохраняются.
        if (hasLegacyGroupFields(context)) {
            // Если .bak восстановлен, а ReminderStore.load ещё вернул пустой список,
            // не затираем восстановленные данные; миграция повторится при загрузке.
            if (reminders.isEmpty()) return
            if (!ReminderStore.save(context, reminders)) return
            // Принадлежность уже перенесена в напоминания: повторно читать
            // groups.json нельзя, иначе удалённые теги вернутся.
            if (!groupsImported) {
                preferences.edit().putBoolean(KEY_GROUPS_IMPORTED, true).apply()
            }
        }
        if (!save(context, merged)) return
        // Завершение фиксируется только когда сохранены и определения, и напоминания.
        preferences.edit()
            .putBoolean(KEY_GROUPS_IMPORTED, true)
            .putBoolean(KEY_MIGRATED, true)
            .apply()
    }

    /** Объявленные теги вместе с тегами из напоминаний (старые данные, импорт). */
    fun all(context: Context, reminders: List<Reminder>): MutableList<String> =
        TagNames.merge(
            load(context),
            reminders.asSequence().flatMap { it.tags.asSequence() }.toList()
        )

    private fun readNames(context: Context, fileName: String): MutableList<String> {
        val file = AtomicFile(File(context.filesDir, fileName))
        return try {
            // openRead сам восстанавливает файл из .bak после сбоя записи.
            file.openRead().use { stream -> namesFromJson(stream.reader().readText()) }
        } catch (e: FileNotFoundException) {
            mutableListOf()
        } catch (e: Exception) {
            runCatching {
                file.baseFile.copyTo(File(context.filesDir, "$fileName.corrupt"), overwrite = true)
            }
            mutableListOf()
        }
    }

    private fun writeNames(context: Context, fileName: String, tags: List<String>): Boolean {
        val file = AtomicFile(File(context.filesDir, fileName))
        val data = JSONArray(TagNames.normalizeAll(tags)).toString().toByteArray()
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

    private fun namesFromJson(text: String): MutableList<String> {
        val array = JSONArray(text)
        val raw = mutableListOf<String>()
        for (i in 0 until array.length()) raw.add(array.optString(i))
        return TagNames.normalizeAll(raw)
    }

    /** Есть ли в текущем reminders.json старое поле group у хотя бы одной записи. */
    private fun hasLegacyGroupFields(context: Context): Boolean {
        val file = AtomicFile(File(context.filesDir, "reminders.json"))
        return runCatching {
            // openRead сам восстанавливает файл из .bak после сбоя записи.
            file.openRead().use { stream ->
                val array = JSONArray(stream.reader().readText())
                (0 until array.length()).any { array.getJSONObject(it).has("group") }
            }
        }.getOrDefault(false)
    }

    /**
     * Названия старых групп. Файл только читается: после успешной миграции
     * повторный запуск не возвращает удалённые пользователем теги.
     */
    private fun legacyGroupNames(context: Context): List<String> =
        readNames(context, LEGACY_GROUPS_FILE)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
