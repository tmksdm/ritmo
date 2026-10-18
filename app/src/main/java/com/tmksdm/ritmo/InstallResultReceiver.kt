package com.tmksdm.ritmo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/**
 * Результат установки обновления. PackageInstaller дописывает результат в extras,
 * поэтому PendingIntent должен быть изменяемым (FLAG_MUTABLE), а Intent — явным:
 * только наш приёмник, без неявной адресации.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> startConfirmation(context, intent)

            PackageInstaller.STATUS_SUCCESS -> Unit

            PackageInstaller.STATUS_FAILURE_ABORTED ->
                report(context, context.getString(R.string.install_cancelled))

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                report(context, context.getString(R.string.install_failed, message ?: status.toString()))
            }
        }
    }

    /**
     * Система передаёт сюда своё окно подтверждения установки. Пользователь только что нажал
     * «Установить» в Ritmo, поэтому приложение на переднем плане и запуск окна разрешён
     * даже при ограничениях Android на старт активности из фона.
     */
    private fun startConfirmation(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(confirmation) }
            .onFailure { report(context, context.getString(R.string.install_failed, it.message ?: "")) }
    }

    /**
     * Ошибку показываем тостом и запоминаем: системный тост из фона может не отобразиться,
     * поэтому настройки покажут сообщение при следующем открытии.
     */
    private fun report(context: Context, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ERROR, message)
            .apply()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val PREFS = "ritmo_settings"
        const val KEY_LAST_ERROR = "install_error"

        private const val REQUEST_CODE = 1
        private const val ACTION_INSTALL_RESULT = "com.tmksdm.ritmo.action.INSTALL_RESULT"

        fun createIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, InstallResultReceiver::class.java).setAction(ACTION_INSTALL_RESULT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        fun consumeLastError(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val message = prefs.getString(KEY_LAST_ERROR, null) ?: return null
            prefs.edit().remove(KEY_LAST_ERROR).apply()
            return message
        }
    }
}
