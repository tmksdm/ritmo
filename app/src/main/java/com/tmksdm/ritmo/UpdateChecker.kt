package com.tmksdm.ritmo

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Проверка и загрузка обновлений из GitHub Releases репозитория tmksdm/ritmo.
 * Никаких фоновых служб: всё выполняется только по нажатию пользователя.
 */
object UpdateChecker {

    private const val RELEASES_API = "https://api.github.com/repos/tmksdm/ritmo/releases/latest"
    private const val RELEASES_PATH_PREFIX = "/tmksdm/ritmo/releases/download/"
    private const val ALLOWED_HOSTS = "github.com,objects.githubusercontent.com"
    private const val MAX_APK_BYTES = 30L * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    data class Release(val versionName: String, val assetUrl: String, val assetSize: Long)

    enum class Problem { NONE, NOT_AN_APK, WRONG_PACKAGE, SIGNATURE_MISMATCH }

    /** Последний релиз с APK. При проблемах бросает IOException с понятным текстом. */
    fun fetchLatest(): Release {
        val release = JSONObject(httpGetText(RELEASES_API))
        val tag = release.optString("tag_name").removePrefix("v")
        val assets = release.optJSONArray("assets") ?: throw IOException("в релизе нет файлов")
        var asset: JSONObject? = null
        for (i in 0 until assets.length()) {
            val candidate = assets.getJSONObject(i)
            val name = candidate.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            // Отладочные сборки для обновления не подходят.
            if (name.contains("debug", ignoreCase = true)) continue
            asset = candidate
            break
        }
        val found = asset ?: throw IOException("в релизе нет APK")
        val url = found.optString("browser_download_url")
        validateAssetUrl(url)
        return Release(tag, url, found.optLong("size", 0L))
    }

    /** Скачивает APK релиза, сообщая прогресс в процентах. */
    fun downloadApk(url: String, target: File, onProgress: (Int) -> Unit) {
        validateAssetUrl(url)
        val connection = openConnection(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub ответил кодом ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 }
            if (total != null && total > MAX_APK_BYTES) throw IOException("файл обновления слишком большой")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var read = input.read(buffer)
                    while (read > 0) {
                        written += read
                        if (written > MAX_APK_BYTES) throw IOException("файл обновления слишком большой")
                        output.write(buffer, 0, read)
                        onProgress(
                            if (total != null) ((written * 100) / total).toInt().coerceIn(0, 100)
                            else 0
                        )
                        read = input.read(buffer)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Проверяет скачанный APK: идентификатор приложения, подпись и версию.
     * Возвращает проблему и versionCode файла (0, если его не удалось прочитать).
     */
    fun validateApk(context: Context, file: File): Pair<Problem, Long> {
        val archive = archiveInfo(context, file) ?: return Problem.NOT_AN_APK to 0L
        if (archive.packageName != context.packageName) return Problem.WRONG_PACKAGE to archive.longVersionCode()

        val installed = installedInfo(context) ?: return Problem.NOT_AN_APK to archive.longVersionCode()
        if (signatureDigest(archive) == null || signatureDigest(archive) != signatureDigest(installed)) {
            return Problem.SIGNATURE_MISMATCH to archive.longVersionCode()
        }
        return Problem.NONE to archive.longVersionCode()
    }

    /** Запускает стандартный системный установщик с подтверждением пользователя. */
    fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("ritmo-update", 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val resultIntent = InstallResultReceiver.createIntent(context)
            session.commit(resultIntent.intentSender)
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun openInstallPermissionSettings(context: Context) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(android.net.Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }
    }

    private fun validateAssetUrl(url: String) {
        val parsed = try {
            URL(url)
        } catch (e: Exception) {
            throw IOException("некорректная ссылка на APK")
        }
        if (parsed.protocol != "https") throw IOException("ссылка на APK не по HTTPS")
        val host = parsed.host ?: throw IOException("некорректная ссылка на APK")
        if (ALLOWED_HOSTS.split(",").none { host.equals(it, ignoreCase = true) }) {
            throw IOException("ссылка ведёт не на GitHub")
        }
        if (!parsed.path.startsWith(RELEASES_PATH_PREFIX)) {
            throw IOException("ссылка не является файлом релиза")
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Ritmo-Android")
        }

    private fun httpGetText(url: String): String {
        val connection = openConnection(url)
        return try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub ответил кодом $code")
            }
            connection.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                if (text.length > 1_000_000) throw IOException("слишком большой ответ GitHub")
                text
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun archiveInfo(context: Context, file: File): PackageInfo? = runCatching {
        context.packageManager.getPackageArchiveInfo(file.absolutePath, signatureFlags)
    }.getOrNull()

    private fun installedInfo(context: Context): PackageInfo? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, signatureFlags)
    }.getOrNull()

    private val signatureFlags: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun signatureDigest(info: PackageInfo): String? {
        val certificate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()
        } ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(certificate.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCode(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
}
