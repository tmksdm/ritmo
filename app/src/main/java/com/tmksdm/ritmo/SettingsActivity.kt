package com.tmksdm.ritmo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
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
    /** Пока true, изменения переключателя темы сделаны программой и не требуют реакции. */
    private var applyingTheme = false
    /** Защита от повторного пересоздания экрана. */
    private var recreateRequested = false
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
            val mode = modeFor(checkedId)
            // Восстановление состояния RadioGroup и программная установка темы не должны
            // запускать пересоздание экрана: иначе получается бесконечный цикл.
            if (applyingTheme || mode == ThemePrefs.mode(this)) return@setOnCheckedChangeListener
            ThemePrefs.setMode(this, mode)
            if (!recreateRequested) {
                recreateRequested = true
                recreate()
            }
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
        findViewById<LinearLayout>(R.id.card_backup).setOnClickListener { createBackup() }
        findViewById<LinearLayout>(R.id.card_restore).setOnClickListener { chooseBackupToRestore() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQUEST_CREATE_BACKUP -> writeBackup(data?.data)
            REQUEST_OPEN_BACKUP -> readBackup(data?.data)
        }
    }

    /** Экспорт: место сохранения выбирает системный интерфейс Android. */
    private fun createBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, Backup.fileName())
        }
        runCatching { startActivityForResult(intent, REQUEST_CREATE_BACKUP) }
            .onFailure { error ->
                message(
                    R.string.settings_backup,
                    getString(R.string.backup_export_error, error.message ?: getString(R.string.backup_error_write))
                )
            }
    }

    private fun writeBackup(uri: Uri?) {
        if (uri == null) return
        val reminders = ReminderStore.load(this)
        val written = runCatching {
            contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(Backup.toJson(reminders).toByteArray())
            } ?: throw IllegalStateException(getString(R.string.backup_error_write))
        }.isSuccess
        if (written) {
            message(R.string.settings_backup, getString(R.string.backup_created, reminders.size))
        } else {
            message(
                R.string.settings_backup,
                getString(R.string.backup_export_error, getString(R.string.backup_error_write))
            )
        }
    }

    /** Импорт: файл проверяется до изменений, замена выполняется только после подтверждения. */
    private fun chooseBackupToRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
            )
        }
        runCatching { startActivityForResult(intent, REQUEST_OPEN_BACKUP) }
            .onFailure { error ->
                message(
                    R.string.settings_backup,
                    getString(R.string.backup_import_error, error.message ?: getString(R.string.backup_error_read))
                )
            }
    }

    private fun readBackup(uri: Uri?) {
        if (uri == null) return
        val parsed = runCatching {
            Backup.parse(Backup.readText(contentResolver, uri))
        }
        val imported = parsed.getOrElse { error ->
            val reason = (error as? Backup.BackupException)?.reason
            message(R.string.settings_backup, getString(R.string.backup_import_error, backupErrorText(reason)))
            return
        }
        val current = ReminderStore.load(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_import_title)
            .setMessage(getString(R.string.backup_import_message, imported.size, current.size))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.backup_import_confirm) { _, _ ->
                if (Backup.restore(this, imported)) {
                    message(R.string.settings_backup, getString(R.string.backup_imported, imported.size))
                } else {
                    message(R.string.settings_backup, getString(R.string.save_error))
                }
            }
            .show()
    }

    private fun backupErrorText(reason: Backup.Error?): String = when (reason) {
        Backup.Error.TOO_BIG -> getString(R.string.backup_error_too_big)
        Backup.Error.READ_FAILED -> getString(R.string.backup_error_read)
        Backup.Error.WRITE_FAILED -> getString(R.string.backup_error_write)
        else -> getString(R.string.backup_error_not_json)
    }

    override fun onResume() {
        super.onResume()
        recreateRequested = false
        updateStatuses()
        handleExactAlarmState()
        showPendingInstallError()
    }

    private fun modeFor(checkedId: Int): ThemeMode = when (checkedId) {
        R.id.theme_light -> ThemeMode.LIGHT
        R.id.theme_dark -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    /** Сообщение установщика могло не отобразиться тостом, пока приложение было в фоне. */
    private fun showPendingInstallError() {
        val message = InstallResultReceiver.consumeLastError(this) ?: return
        message(R.string.settings_updates, message)
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

    private companion object {
        const val REQUEST_CREATE_BACKUP = 200
        const val REQUEST_OPEN_BACKUP = 201
    }
}