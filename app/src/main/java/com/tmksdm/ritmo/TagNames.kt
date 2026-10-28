package com.tmksdm.ritmo

/**
 * Правила названий тегов без зависимостей от Android и хранилища.
 * Отдельный объект позволяет проверять нормализацию и уникальность на JVM.
 */
object TagNames {

    /** Совпадает с ограничением поля ввода в диалоге тега. */
    const val MAX_LENGTH = 60

    /** Обрезает пробелы, ограничивает длину; пустое название недопустимо. */
    fun normalize(raw: String?): String? =
        raw?.trim()?.take(MAX_LENGTH)?.trim()?.takeIf { it.isNotEmpty() }

    /** Убирает пустые значения и повторы без учёта регистра, сохраняя первый вариант. */
    fun normalizeAll(raw: Iterable<String>): MutableList<String> {
        val result = mutableListOf<String>()
        raw.forEach { add(result, it) }
        return result
    }

    /** Добавляет название, если его ещё нет; возвращает true при добавлении. */
    fun add(names: MutableList<String>, raw: String): Boolean {
        val name = normalize(raw) ?: return false
        if (names.any { same(it, name) }) return false
        names.add(name)
        return true
    }

    /**
     * Занято ли название. [except] позволяет переименовать тег с изменением регистра:
     * старое написание не считается конфликтом.
     */
    fun isTaken(names: Collection<String>, raw: String, except: String? = null): Boolean {
        val name = normalize(raw) ?: return true
        return names.any { same(it, name) && (except == null || !same(it, except)) }
    }

    /** Объединение объявленных названий и тегов из напоминаний без потери вариантов. */
    fun merge(defined: Iterable<String>, extra: Iterable<String>): MutableList<String> {
        val result = normalizeAll(defined)
        extra.forEach { add(result, it) }
        return result
    }

    /** Напоминание относится к тегу без учёта регистра. */
    fun contains(tags: Collection<String>, raw: String): Boolean {
        val name = normalize(raw) ?: return false
        return tags.any { same(it, name) }
    }

    fun same(first: String?, second: String?): Boolean =
        first != null && second != null && first.equals(second, ignoreCase = true)
}
