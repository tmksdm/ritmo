package com.tmksdm.ritmo

import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * Отступы под системные панели. Начиная с Android 15 (targetSdk 35) приложение по умолчанию
 * рисуется «от края до края», поэтому содержимое нужно сдвигать от статусбара и навигации.
 * На более старых версиях система сама подгоняет размер окна, и отступы приходят нулевыми.
 */
fun View.applySystemBarInsets() {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom

    setOnApplyWindowInsetsListener { view, insets ->
        val top: Int
        val bottom: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            top = bars.top
            bottom = bars.bottom
        } else {
            @Suppress("DEPRECATION")
            top = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            bottom = insets.systemWindowInsetBottom
        }
        view.setPadding(initialLeft, initialTop + top, initialRight, initialBottom + bottom)
        insets
    }
    requestApplyInsets()
}
