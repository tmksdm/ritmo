package com.tmksdm.ritmo

import org.json.JSONArray
import org.json.JSONObject

/** Тип повторения напоминания. В Этапе 1 интерфейс создаёт только ONCE. */
enum class RepeatType {
    ONCE,
    INTERVAL,
    DAILY,
    WEEKLY,
    MONTHLY,
    EVERY_N_DAYS,
    YEARLY;

    companion object {
        fun fromName(name: String?): RepeatType = entries.firstOrNull { it.name == name } ?: ONCE
    }
}

/**
 * Напоминание. Поля повторений уже присутствуют в модели и в JSON-формате,
 * чтобы формат хранения не менялся между этапами.
 */
data class Reminder(
    val id: Long,
    var title: String,
    var enabled: Boolean = true,
    var repeatType: RepeatType = RepeatType.ONCE,
    /** Для ONCE — момент срабатывания, для EVERY_N_DAYS — дата начала отсчёта. */
    var dateTimeMillis: Long = 0L,
    var intervalMinutes: Int = 60,
    var hour: Int = 9,
    var minute: Int = 0,
    /** Дни недели в значениях [java.util.Calendar.DAY_OF_WEEK]. */
    var daysOfWeek: MutableSet<Int> = mutableSetOf(),
    var dayOfMonth: Int = 1,
    /** Месяц для YEARLY: 1 — январь, 12 — декабрь. */
    var monthOfYear: Int = 1,
    var everyNDays: Int = 3,
    var windowEnabled: Boolean = false,
    var windowStartMinutes: Int = 9 * 60,
    var windowEndMinutes: Int = 21 * 60,
    var lastTriggeredAt: Long = 0L,
    /** Теги напоминания; пустой список — без тегов. */
    var tags: MutableList<String> = mutableListOf()
) {
    /** Ближайшее срабатывание или null, если его не будет. */
    fun nextTriggerAt(now: Long = System.currentTimeMillis()): Long? = Schedules.nextTrigger(this, now)

    /** Хватает ли данных, чтобы расписание имело смысл. */
    fun hasValidSchedule(): Boolean = when (repeatType) {
        RepeatType.ONCE, RepeatType.EVERY_N_DAYS -> dateTimeMillis > 0
        RepeatType.INTERVAL -> intervalMinutes >= 1 &&
            (!windowEnabled || windowEndMinutes > windowStartMinutes)

        RepeatType.DAILY -> true
        RepeatType.WEEKLY -> daysOfWeek.isNotEmpty()
        RepeatType.MONTHLY, RepeatType.YEARLY -> dayOfMonth in 1..31
    }

    /**
     * Переназначение срабатывания на будущее снимает устаревший признак завершения.
     * Однократное напоминание, которое уже сработало, снова становится активным.
     * Возвращает true, если признак завершения был снят.
     */
    fun reactivateIfRescheduled(nextTrigger: Long): Boolean {
        if (lastTriggeredAt <= 0 || nextTrigger <= lastTriggeredAt) return false
        lastTriggeredAt = 0
        if (repeatType == RepeatType.ONCE) enabled = true
        return true
    }

    /**
     * Приводит значения к допустимым. Нужно для данных из резервной копии:
     * файл мог быть создан другой версией или отредактирован вручную.
     */
    fun sanitized(): Reminder = apply {
        if (title.isBlank()) title = DEFAULT_TITLE
        intervalMinutes = intervalMinutes.coerceIn(1, MAX_INTERVAL_MINUTES)
        hour = hour.coerceIn(0, 23)
        minute = minute.coerceIn(0, 59)
        dayOfMonth = dayOfMonth.coerceIn(1, 31)
        monthOfYear = monthOfYear.coerceIn(1, 12)
        everyNDays = everyNDays.coerceIn(1, MAX_EVERY_N_DAYS)
        windowStartMinutes = windowStartMinutes.coerceIn(0, 24 * 60 - 1)
        windowEndMinutes = windowEndMinutes.coerceIn(0, 24 * 60 - 1)
        if (windowEndMinutes <= windowStartMinutes) windowEnabled = false
        daysOfWeek = daysOfWeek.filter { it in 1..7 }.toMutableSet()
        if (lastTriggeredAt < 0) lastTriggeredAt = 0
        if (dateTimeMillis < 0) dateTimeMillis = 0
        tags = TagNames.normalizeAll(tags)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("enabled", enabled)
        put("repeatType", repeatType.name)
        put("dateTimeMillis", dateTimeMillis)
        put("intervalMinutes", intervalMinutes)
        put("hour", hour)
        put("minute", minute)
        put("daysOfWeek", JSONArray(daysOfWeek.sorted()))
        put("dayOfMonth", dayOfMonth)
        put("monthOfYear", monthOfYear)
        put("everyNDays", everyNDays)
        put("windowEnabled", windowEnabled)
        put("windowStartMinutes", windowStartMinutes)
        put("windowEndMinutes", windowEndMinutes)
        put("lastTriggeredAt", lastTriggeredAt)
        put("tags", JSONArray(tags))
    }

    companion object {
        private const val DEFAULT_TITLE = "Напоминание"
        private const val MAX_INTERVAL_MINUTES = 24 * 60
        private const val MAX_EVERY_N_DAYS = 365

        /** @throws org.json.JSONException если данные повреждены. */
        fun fromJson(json: JSONObject): Reminder = Reminder(
            id = json.getLong("id"),
            title = json.getString("title"),
            enabled = json.optBoolean("enabled", true),
            repeatType = RepeatType.fromName(json.optString("repeatType")),
            dateTimeMillis = json.optLong("dateTimeMillis", 0L),
            intervalMinutes = json.optInt("intervalMinutes", 60),
            hour = json.optInt("hour", 9),
            minute = json.optInt("minute", 0),
            daysOfWeek = json.optJSONArray("daysOfWeek").toDaySet(),
            dayOfMonth = json.optInt("dayOfMonth", 1),
            monthOfYear = json.optInt("monthOfYear", 1),
            everyNDays = json.optInt("everyNDays", 3),
            windowEnabled = json.optBoolean("windowEnabled", false),
            windowStartMinutes = json.optInt("windowStartMinutes", 9 * 60),
            windowEndMinutes = json.optInt("windowEndMinutes", 21 * 60),
            lastTriggeredAt = json.optLong("lastTriggeredAt", 0L),
            tags = json.optJSONArray("tags")?.toTagList() ?: legacyGroupTag(json)
        )

        /** @throws org.json.JSONException если текст не является корректным списком напоминаний. */
        fun listFromJson(text: String): MutableList<Reminder> {
            val array = JSONArray(text)
            val result = mutableListOf<Reminder>()
            for (i in 0 until array.length()) {
                result.add(fromJson(array.getJSONObject(i)))
            }
            return result
        }

        fun listToJson(reminders: List<Reminder>): String {
            val array = JSONArray()
            for (reminder in reminders) array.put(reminder.toJson())
            return array.toString()
        }

        /**
         * Разбирает резервную копию. Значения приводятся к допустимым, дубликаты по id убираются.
         * @throws org.json.JSONException если данные не являются списком напоминаний.
         */
        fun listFromBackup(text: String): MutableList<Reminder> {
            val reminders = listFromJson(text)
            return reminders.map { it.sanitized() }.distinctBy { it.id }.toMutableList()
        }

        private fun JSONArray?.toDaySet(): MutableSet<Int> {
            if (this == null) return mutableSetOf()
            val days = mutableSetOf<Int>()
            for (i in 0 until length()) days.add(optInt(i))
            return days
        }

        /** Старый формат хранил одну группу; она становится первым тегом. */
        private fun legacyGroupTag(json: JSONObject): MutableList<String> {
            val tags = mutableListOf<String>()
            TagNames.add(tags, json.optString("group"))
            return tags
        }

        private fun JSONArray.toTagList(): MutableList<String> {
            val raw = mutableListOf<String>()
            for (i in 0 until length()) raw.add(optString(i))
            return TagNames.normalizeAll(raw)
        }
    }
}
