package com.tmksdm.ritmo

import android.content.Context
import android.util.TypedValue

/** Цвет токена оформления из темы, применённой к активности. */
fun Context.themeColor(attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return value.data
}
