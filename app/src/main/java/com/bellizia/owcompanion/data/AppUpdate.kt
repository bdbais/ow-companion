package com.bellizia.owcompanion.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Fetches a new build and installs it, without sending anyone to a browser.
 *
 * Before this, the update banner opened the release page: the reader downloaded the APK in
 * the browser, found it in the notifications, tapped through two system screens and came
 * back. Most did not. Now the file arrives in the background while the app is in use, and
 * once it is here and checked the app says so - installing means the app closes, so that is
 * the one thing that has to be asked.
 *
 * The state lives in the process rather than in a screen, so turning the phone does not
 * cancel a download halfway through, and a download finished while the reader was elsewhere
 * in the app is still finished when they come back.
 *
 * Every failure ends in [State.Failed], whose banner falls back to the release page: an
 * update that cannot be installed here can always still be installed by hand.
 */
object AppUpdate {

    sealed interface State {
        data object Idle : State
        data class Downloading(val version: String, val percent: Int) : State
        data class Ready(val version: String) : State
        data class Installing(val version: String) : State
        data class Failed(val version: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> get() = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /**
     * Whether downloading now would cost the reader money.
     *
     * Thirty-odd megabytes on a metered connection is not something to start unasked, so on
     * mobile data the banner offers the download with its size instead of starting it.
     */
    fun isMetered(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        return manager.isActiveNetworkMetered
    }

    /**
     * Brings [release] to [State.Ready], downloading it unless it is already here.
     *
     * Safe to call on every launch: a file that was finished before is re-checked and reused
     * rather than fetched again, and a download already running is left alone. With
     * [download] false only a file already here is used, which is how a metered connection
     * still gets to install something fetched earlier on Wi-Fi.
     */
    @Synchronized
    fun prepare(context: Context, release: ReleaseChecker.Release, download: Boolean = true) {
        val apk = release.apk ?: return
        if (job?.isActive == true) return
        when (_state.value) {
            is State.Ready, is State.Installing -> return
            else -> Unit
        }
        val app = context.applicationContext
        job = scope.launch {
            val target = fileFor(app, release.version)
            runCatching {
                clearOthers(app, keep = target)
                if (!isIntact(target, apk)) {
                    if (!download) return@launch
                    // Straight away, not once the server answers: otherwise the banner shows
                    // the metered-network offer to download for the second that takes.
                    _state.value = State.Downloading(release.version, 0)
                    download(apk, target, release.version)
                }
                checkArchive(app, target)
                _state.value = State.Ready(release.version)
            }.onFailure {
                target.delete()
                _state.value = State.Failed(release.version)
            }
        }
    }

    /** Hands the verified file to Android. Android closes the app to install it. */
    fun install(context: Context, version: String) {
        val app = context.applicationContext
        val file = fileFor(app, version)
        if (!file.isFile) {
            _state.value = State.Failed(version)
            return
        }
        _state.value = State.Installing(version)
        scope.launch {
            runCatching { commit(app, file) }
                .onFailure { _state.value = State.Failed(version) }
        }
    }

    /** The reader said no to this version: stop, and give the space back. */
    fun forget(context: Context) {
        job?.cancel()
        clearOthers(context.applicationContext, keep = null)
        _state.value = State.Idle
    }

    /**
     * Removes whatever an earlier update left behind.
     *
     * Called when there is nothing newer to install, which is also the state right after an
     * update has gone in: the file that installed it is no longer any use.
     */
    fun tidy(context: Context) {
        if (job?.isActive == true) return
        clearOthers(context.applicationContext, keep = null)
    }

    internal fun installCancelled(version: String?) {
        _state.value = State.Ready(version ?: return)
    }

    internal fun installFailed(version: String?) {
        _state.value = State.Failed(version ?: return)
    }

    private fun dir(context: Context) = File(context.cacheDir, "updates")

    private fun fileFor(context: Context, version: String) =
        File(dir(context), "ow-companion-${version.filter { it.isLetterOrDigit() || it == '.' }}.apk")

    private fun clearOthers(context: Context, keep: File?) {
        dir(context).listFiles()?.filter { it != keep }?.forEach { it.delete() }
    }

    private fun isIntact(file: File, apk: ReleaseChecker.Apk): Boolean =
        file.isFile && file.length() == apk.size && sha256(file) == apk.sha256

    /**
     * Streams the APK to disk, hashing as it goes, and keeps it only if both size and hash
     * are exactly what GitHub said.
     *
     * Written to a `.part` file and renamed at the end, so a download cut off by a closed
     * app or a lost signal never looks like a finished one on the next launch.
     */
    private suspend fun download(apk: ReleaseChecker.Apk, target: File, version: String) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = (URL(apk.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            // GitHub answers with a redirect to its file storage, on https like the request.
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ow-companion")
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            var written = 0L
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        // A body larger than announced is not the file that was announced.
                        if (written > apk.size) throw IOException("larger than announced")
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        val percent = (written * 100 / apk.size).toInt()
                        if ((_state.value as? State.Downloading)?.percent != percent) {
                            _state.value = State.Downloading(version, percent)
                        }
                    }
                }
            }
            if (written != apk.size) throw IOException("truncated")
            if (hex(digest.digest()) != apk.sha256) throw IOException("hash mismatch")
            if (!part.renameTo(target)) throw IOException("rename failed")
        } finally {
            connection.disconnect()
            part.delete()
        }
    }

    /**
     * One more check that does not trust the release notes: the file must be this app, and
     * newer than what is installed. A wrong file attached to a release by mistake would
     * otherwise reach the system installer and fail there with a message nobody understands.
     */
    private fun checkArchive(context: Context, file: File) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, 0) ?: throw IOException("not an APK")
        if (archive.packageName != context.packageName) throw IOException("another app")
        val installed = PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(context.packageName, 0))
        if (PackageInfoCompat.getLongVersionCode(archive) <= installed) throw IOException("not newer")
    }

    private fun commit(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            // Once this app is the installer of record, later updates need no confirmation
            // screen at all. The first one, over a copy installed from the browser, still does.
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
        }
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out, 64 * 1024) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setAction(InstallResultReceiver.ACTION)
                .setPackage(context.packageName)
            // Mutable because the installer adds the outcome to it; explicit, so only the
            // receiver below can get it.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
            session.close()
        } catch (e: Exception) {
            runCatching { session.abandon() }
            throw e
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Where Android reports how the installation went.
 *
 * On the first update it reports that it needs the reader's confirmation, and hands over the
 * screen that asks for it - the one that also offers to allow installs from this app. On
 * success nothing arrives here at all, because by then this process has been replaced.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val version = (AppUpdate.state.value as? AppUpdate.State.Installing)?.version
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmation(intent)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm == null || runCatching { context.startActivity(confirm) }.isFailure) {
                    AppUpdate.installFailed(version)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            // "Cancel" on the confirmation screen: the file is still good, so it stays on offer.
            PackageInstaller.STATUS_FAILURE_ABORTED -> AppUpdate.installCancelled(version)
            else -> AppUpdate.installFailed(version)
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmation(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    companion object {
        const val ACTION = "com.bellizia.owcompanion.action.INSTALL_RESULT"
    }
}
