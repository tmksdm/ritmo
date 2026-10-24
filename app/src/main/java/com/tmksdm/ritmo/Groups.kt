package com.tmksdm.ritmo

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File

/**
 * Именованные группы: список названий в отдельном JSON-файле и состояние
 * свёрнутости в настройках. Отсутствие файла означает, что групп ещё нет,
 * поэтому старые данные читаются как раньше — плоским списком.
 */
object Groups {

    private const val FILE_NAME = "groups.json"
    private const val PREFS = "ritmo_groups"
    private const val KEY_EXPANDED = "expanded:"
    private const val KEY_UNGROUPED_NAME = "ungrouped_name"

    fun load(context: Context): MutableList<String> {
        val file = atomicFile(context)
        if (!file.baseFile.exists()) return mutableListOf()
        return try {
            file.openRead().use { stream -> namesFromJson(stream.reader().readText()) }
        } catch (e: Exception) {
            runCatching {
                file.baseFile.copyTo(File(context.filesDir, "$FILE_NAME.corrupt"), overwrite = true)
            }
            mutableListOf()
        }
    }

    fun save(context: Context, groups: List<String>): Boolean {
        val file = atomicFile(context)
        val data = JSONArray(groups).toString().toByteArray()
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

    /** Названия без пустых значений и дубликатов (без учёта регистра), в порядке появления. */
    fun namesFromJson(text: String): MutableList<String> {
        val array = JSONArray(text)
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            addName(result, array.optString(i))
        }
        return result
    }

    /** Добавляет название, если такого ещё нет; возвращает true при добавлении. */
    fun addName(names: MutableList<String>, raw: String): Boolean {
        val name = raw.trim()
        if (name.isEmpty()) return false
        if (names.any { it.equals(name, ignoreCase = true) }) return false
        names.add(name)
        return true
    }

    /**
     * Отображаемое название секции без группы. Внутреннее назначение остаётся null,
     * поэтому сортировка и данные напоминаний не зависят от этого названия.
     */
    fun ungroupedName(context: Context): String =
        prefs(context).getString(KEY_UNGROUPED_NAME, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.group_ungrouped)

    /**
     * Возвращает отображаемое название секции без группы, разрешая конфликт с реальными
     * группами из старых данных или импорта. Найденный свободный вариант сохраняется,
     * поэтому при следующих загрузках имя не меняется.
     */
    fun resolveUngroupedName(context: Context, groups: Collection<String>): String {
        val current = ungroupedName(context)
        val resolved = ReminderGroups.freeUngroupedName(current, groups)
        if (resolved != current) setUngroupedName(context, resolved)
        return resolved
    }

    fun setUngroupedName(context: Context, name: String) {
        val value = name.trim()
        val editor = prefs(context).edit()
        if (value == context.getString(R.string.group_ungrouped)) {
            editor.remove(KEY_UNGROUPED_NAME)
        } else {
            editor.putString(KEY_UNGROUPED_NAME, value)
        }
        editor.apply()
    }

    fun isExpanded(context: Context, group: String?): Boolean =
        prefs(context).getBoolean(KEY_EXPANDED + (group ?: ""), true)

    fun setExpanded(context: Context, group: String?, expanded: Boolean) {
        prefs(context).edit().putBoolean(KEY_EXPANDED + (group ?: ""), expanded).apply()
    }

    /** После переименования сохраняем состояние свёрнутости у новой группы. */
    fun renameExpanded(context: Context, old: String, new: String) {
        if (old == new) return
        val expanded = isExpanded(context, old)
        prefs(context).edit()
            .putBoolean(KEY_EXPANDED + new, expanded)
            .remove(KEY_EXPANDED + old)
            .apply()
    }

    fun forgetExpanded(context: Context, group: String?) {
        prefs(context).edit().remove(KEY_EXPANDED + (group ?: "")).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun atomicFile(context: Context) = AtomicFile(File(context.filesDir, FILE_NAME))
}
