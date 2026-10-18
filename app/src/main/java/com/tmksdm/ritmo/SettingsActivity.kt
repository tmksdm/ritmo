package com.tmksdm.ritmo

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView

/** Настройки: состояние разрешений и доступ к системным экранам. */
class SettingsActivity : Activity() {

    private lateinit var notificationsStatus: TextView
    private lateinit var exactAlarmStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        notificationsStatus = findViewById(R.id.notifications_status)
        exactAlarmStatus = findViewById(R.id.exact_alarm_status)
        findViewById<TextView>(R.id.version_value).text =
            getString(R.string.settings_version, BuildConfig.VERSION_NAME)

        findViewById<Button>(R.id.open_notification_settings).setOnClickListener {
            AlarmScheduler.openAppNotificationSettings(this)
        }
        findViewById<Button>(R.id.open_exact_alarm_settings).setOnClickListener {
            if (!AlarmScheduler.requestExactAlarmAccess(this)) {
                android.widget.Toast.makeText(this, R.string.exact_alarm_not_needed, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatuses()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateStatuses() {
        notificationsStatus.text = getString(
            if (NotificationHelper.canPostNotifications(this)) R.string.status_granted else R.string.status_denied
        )
        exactAlarmStatus.text = getString(
            if (AlarmScheduler.canScheduleExact(this)) R.string.status_granted else R.string.status_denied
        )
    }
}
