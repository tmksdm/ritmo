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
    EVERY_N_DAYS;

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
    /** Для ONCE — точное время срабатывания. */
    var dateTimeMillis: Long = 0L,
    var intervalMinutes: Int = 60,
    var hour: Int = 9,
    var minute: Int = 0,
    var daysOfWeek: MutableSet<Int> = mutableSetOf(),
    var dayOfMonth: Int = 1,
    var everyNDays: Int = 3,
    var windowEnabled: Boolean = false,
    var windowStartMinutes: Int = 9 * 60,
    var windowEndMinutes: Int = 21 * 60,
    var lastTriggeredAt: Long = 0L
) {
    /** Ближайшее срабатывание или null, если его не будет. */
    fun nextTriggerAt(now: Long = System.currentTimeMillis()): Long? = when (repeatType) {
        RepeatType.ONCE -> if (dateTimeMillis > now) dateTimeMillis else null
        // Остальные типы повторений реализуются в Этапе 2.
        else -> null
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
        put("everyNDays", everyNDays)
        put("windowEnabled", windowEnabled)
        put("windowStartMinutes", windowStartMinutes)
        put("windowEndMinutes", windowEndMinutes)
        put("lastTriggeredAt", lastTriggeredAt)
    }

    companion object {
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
            everyNDays = json.optInt("everyNDays", 3),
            windowEnabled = json.optBoolean("windowEnabled", false),
            windowStartMinutes = json.optInt("windowStartMinutes", 9 * 60),
            windowEndMinutes = json.optInt("windowEndMinutes", 21 * 60),
            lastTriggeredAt = json.optLong("lastTriggeredAt", 0L)
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

        private fun JSONArray?.toDaySet(): MutableSet<Int> {
            if (this == null) return mutableSetOf()
            val days = mutableSetOf<Int>()
            for (i in 0 until length()) days.add(optInt(i))
            return days
        }
    }
}
