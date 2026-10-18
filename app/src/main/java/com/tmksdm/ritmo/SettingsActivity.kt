package com.tmksdm.ritmo

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Настройки: тема, разрешения и обновление приложения. */
class SettingsActivity : Activity() {

    private lateinit var notificationsStatus: TextView
    private lateinit var exactAlarmStatus: TextView
    private lateinit var updatesStatus: TextView
    private lateinit var themeGroup: RadioGroup
    private var lastExactAlarmState: Boolean? = null
    private var applyingTheme = false
    private var checkingUpdates = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemePrefs.themeResId(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.root).applySystemBarInsets()

        notificationsStatus = findViewById(R.id.notifications_status)
        exactAlarmStatus = findViewById(R.id.exact_alarm_status)
        updatesStatus = findViewById(R.id.updates_status)
        themeGroup = findViewById(R.id.theme_group)
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }

        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (applyingTheme) return@setOnCheckedChangeListener
            ThemePrefs.setMode(
                this,
                when (checkedId) {
                    R.id.theme_light -> ThemeMode.LIGHT
                    R.id.theme_dark -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                }
            )
            // Пересоздаём экран, чтобы новая тема применилась сразу.
            recreate()
        }

        findViewById<LinearLayout>(R.id.card_notifications).setOnClickListener {
            AlarmScheduler.openAppNotificationSettings(this)
        }
        findViewById<LinearLayout>(R.id.card_alarms).setOnClickListener {
            if (!AlarmScheduler.requestExactAlarmAccess(this)) {
                Toast.makeText(this, R.string.exact_alarm_not_needed, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<LinearLayout>(R.id.card_updates).setOnClickListener { checkForUpdates() }
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
        if (!checkingUpdates) {
            updatesStatus.text = getString(R.string.settings_installed_version, BuildConfig.VERSION_NAME)
        }
    }

    private fun handleExactAlarmState() {
        val exact = AlarmScheduler.canScheduleExact(this)
        if (lastExactAlarmState == false && exact) {
            // Доступ выдан: перепланируем активные напоминания в точном режиме.
            AlarmScheduler.rescheduleAll(this)
        }
        lastExactAlarmState = exact
    }

    private fun checkForUpdates() {
        if (checkingUpdates) return
        checkingUpdates = true
        val progress = AlertDialog.Builder(this)
            .setTitle(R.string.settings_updates)
            .setMessage(R.string.update_checking)
            .setCancelable(false)
            .create()
        progress.show()
        updatesStatus.text = getString(R.string.update_checking)

        Thread {
            try {
                val release = UpdateChecker.fetchLatest()
                if (release.versionName == BuildConfig.VERSION_NAME) {
                    onUi {
                        progress.dismiss()
                        checkingUpdates = false
                        updatesStatus.text = getString(R.string.settings_installed_version, BuildConfig.VERSION_NAME)
                        message(R.string.settings_updates, getString(R.string.update_none, BuildConfig.VERSION_NAME))
                    }
                    return@Thread
                }
                if (!UpdateChecker.canRequestPackageInstalls(this)) {
                    onUi {
                        progress.dismiss()
                        checkingUpdates = false
                        askInstallPermission()
                    }
                    return@Thread
                }

                val file = File(cacheDir, "ritmo-update.apk")
                if (file.exists()) file.delete()
                UpdateChecker.downloadApk(release.assetUrl, file) { percent ->
                    onUi { progress.setMessage(getString(R.string.update_downloading, percent)) }
                }

                val (problem, versionCode) = UpdateChecker.validateApk(this, file)
                onUi {
                    progress.dismiss()
                    checkingUpdates = false
                    when {
                        problem != UpdateChecker.Problem.NONE -> {
                            file.delete()
                            message(
                                R.string.settings_updates,
                                getString(R.string.update_error, problemMessage(problem))
                            )
                        }

                        versionCode <= BuildConfig.VERSION_CODE.toLong() -> {
                            file.delete()
                            message(
                                R.string.settings_updates,
                                getString(R.string.update_error, getString(R.string.update_error_older))
                            )
                        }

                        else -> confirmInstall(release.versionName, file)
                    }
                }
            } catch (e: Exception) {
                onUi {
                    progress.dismiss()
                    checkingUpdates = false
                    message(
                        R.string.settings_updates,
                        getString(R.string.update_error, e.message ?: getString(R.string.update_error_network))
                    )
                }
            }
        }.start()
    }

    private fun confirmInstall(versionName: String, file: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_updates)
            .setMessage(getString(R.string.update_available, versionName, BuildConfig.VERSION_NAME))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.update_install) { _, _ ->
                runCatching { UpdateChecker.install(this, file) }.onFailure { error ->
                    message(
                        R.string.settings_updates,
                        getString(R.string.update_error, error.message ?: getString(R.string.update_error_network))
                    )
                }
            }
            .show()
    }

    private fun askInstallPermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_sources_title)
            .setMessage(R.string.update_sources_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.update_sources_open) { _, _ ->
                UpdateChecker.openInstallPermissionSettings(this)
            }
            .show()
    }

    private fun problemMessage(problem: UpdateChecker.Problem): String = getString(
        when (problem) {
            UpdateChecker.Problem.WRONG_PACKAGE -> R.string.update_error_package
            UpdateChecker.Problem.SIGNATURE_MISMATCH -> R.string.update_error_signature
            else -> R.string.update_error_not_apk
        }
    )

    private fun message(titleRes: Int, text: String) {
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun onUi(action: () -> Unit) = runOnUiThread(action)
}
