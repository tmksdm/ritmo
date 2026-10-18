package com.tmksdm.ritmo

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

/** Настройки: тема, состояние разрешений и доступ к системным экранам. */
class SettingsActivity : Activity() {

    private lateinit var notificationsStatus: TextView
    private lateinit var exactAlarmStatus: TextView
    private lateinit var themeGroup: RadioGroup
    private var lastExactAlarmState: Boolean? = null
    private var applyingTheme = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemePrefs.themeResId(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.root).applySystemBarInsets()

        notificationsStatus = findViewById(R.id.notifications_status)
        exactAlarmStatus = findViewById(R.id.exact_alarm_status)
        themeGroup = findViewById(R.id.theme_group)
        findViewById<TextView>(R.id.version_value).text =
            getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        findViewById<Button>(R.id.back_button).setOnClickListener { finish() }

        themeGroup.check(
            when (ThemePrefs.mode(this)) {
                ThemeMode.SYSTEM -> R.id.theme_system
                ThemeMode.LIGHT -> R.id.theme_light
                ThemeMode.DARK -> R.id.theme_dark
            }
        )
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (applyingTheme) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.theme_light -> ThemeMode.LIGHT
                R.id.theme_dark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            ThemePrefs.setMode(this, mode)
            // Пересоздаём экран, чтобы новая тема применилась сразу.
            recreate()
        }

        findViewById<Button>(R.id.open_notification_settings).setOnClickListener {
            AlarmScheduler.openAppNotificationSettings(this)
        }
        findViewById<Button>(R.id.open_exact_alarm_settings).setOnClickListener {
            if (!AlarmScheduler.requestExactAlarmAccess(this)) {
                Toast.makeText(this, R.string.exact_alarm_not_needed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatuses()
        handleExactAlarmState()
    }

    private fun updateStatuses() {
        applyingTheme = true
        themeGroup.check(
            when (ThemePrefs.mode(this)) {
                ThemeMode.SYSTEM -> R.id.theme_system
                ThemeMode.LIGHT -> R.id.theme_light
                ThemeMode.DARK -> R.id.theme_dark
            }
        )
        applyingTheme = false
        notificationsStatus.text = getString(
            if (NotificationHelper.canPostNotifications(this)) R.string.status_granted else R.string.status_denied
        )
        exactAlarmStatus.text = getString(
            if (AlarmScheduler.canScheduleExact(this)) R.string.status_granted else R.string.status_denied
        )
    }

    private fun handleExactAlarmState() {
        val exact = AlarmScheduler.canScheduleExact(this)
        if (lastExactAlarmState == false && exact) {
            // Доступ выдан: перепланируем активные напоминания в точном режиме.
            AlarmScheduler.rescheduleAll(this)
        }
        lastExactAlarmState = exact
    }
}
