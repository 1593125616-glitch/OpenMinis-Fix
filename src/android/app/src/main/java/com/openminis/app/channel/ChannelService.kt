package com.openminis.app.channel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openminis.app.R
import com.openminis.app.channel.telegram.OkHttpTelegramHttp
import com.openminis.app.channel.telegram.TelegramBotClient
import com.openminis.app.channel.telegram.TelegramUpdateParser
import com.openminis.app.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Long-polls Telegram getUpdates while the user has Channels enabled.
 *
 * Uses the same mediaPlayback FGS type as [com.openminis.app.service.AgentForegroundService]
 * so Android 14+ will not apply the dataSync 6h cap. Sideload-only; Play
 * policy would reject this type without actual media.
 */
class ChannelService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification("Starting…"))
        acquireWakeLock()
        scope.launch { pollLoop() }
        startLocalHttpIfEnabled()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = ChannelPrefs(this)
        val tg = prefs.isTelegramEnabled() && prefs.hasBotToken()
        val http = prefs.isLocalHttpEnabled()
        if (!tg && !http) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NOTIF_ID,
            buildNotification(if (tg) "Listening for Telegram" else "Local agent HTTP"),
        )
        return START_STICKY
    }

    override fun onDestroy() {
        localServer?.stop()
        localServer = null
        scope.cancel()
        runCatching { wakeLock?.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "openminis:channels").also {
            it.setReferenceCounted(false)
            it.acquire()
        }
    }

    private var localServer: LocalAgentServer? = null

    private fun startLocalHttpIfEnabled() {
        val prefs = ChannelPrefs(this)
        if (!prefs.isLocalHttpEnabled()) return
        val server = LocalAgentServer(prefs) { text ->
            val id = com.openminis.app.debug.HeadlessChatRunner.ensureBoundSession(
                applicationContext, prefs, "local-http", "local-http",
            )
            val result = com.openminis.app.debug.HeadlessChatRunner.prompt(
                context = applicationContext,
                sessionId = id,
                text = text,
                wait = true,
                timeoutMs = ChannelGateway.PROMPT_TIMEOUT_MS,
            )
            result.responseText ?: result.status
        }
        localServer = server
        server.start(scope, bindLan = prefs.isLanBind())
    }

    private suspend fun pollLoop() {
        val prefs = ChannelPrefs(this)
        val client = TelegramBotClient(
            tokenProvider = { prefs.botToken() },
            http = OkHttpTelegramHttp(),
        )
        val gateway = ChannelGateway.live(applicationContext, prefs, client)
        var backoffMs = 1_000L
        while (scope.isActive) {
            if (!prefs.isTelegramEnabled() || !prefs.hasBotToken()) {
                if (prefs.isLocalHttpEnabled()) {
                    delay(30_000L)
                    continue
                }
                stopSelf()
                return
            }
            if (!ChannelPrefs.isValidBotToken(prefs.botToken())) {
                prefs.setLastError("Invalid bot token")
                delay(15_000L)
                continue
            }
            try {
                val body = client.getUpdates(prefs.updateOffset(), TIMEOUT_SEC)
                val parsed = TelegramUpdateParser.parseGetUpdates(body)
                if (!parsed.ok) {
                    prefs.setLastError(parsed.description)
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    continue
                }
                prefs.markOk()
                backoffMs = 1_000L
                for (update in parsed.updates) {
                    prefs.setUpdateOffset(update.updateId + 1)
                    val inbound = update.inbound ?: continue
                    runCatching { gateway.handle(inbound) }
                        .onFailure { t ->
                            if (t is CancellationException) throw t
                            Log.w(TAG, "handle failed: ${t.javaClass.simpleName}")
                            AppLogger.info(TAG, "channel handle failed: ${t.javaClass.simpleName}")
                        }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                prefs.setLastError(t.javaClass.simpleName)
                Log.w(TAG, "poll failed: ${t.javaClass.simpleName}")
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                description = getString(R.string.channel_notification_channel_desc)
            },
        )
    }

    private fun buildNotification(status: String): Notification {
        val deepLink = Uri.parse("minis://settings/channels")
        val launch = Intent(Intent.ACTION_VIEW, deepLink).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.channel_notification_title))
            .setContentText(status)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "ChannelService"
        private const val CHANNEL_ID = "minis_channels"
        private const val NOTIF_ID = 4711
        private const val TIMEOUT_SEC = 50
        private const val MAX_BACKOFF_MS = 30_000L

        fun startIfEnabled(context: Context) {
            val prefs = ChannelPrefs(context)
            if ((!prefs.isTelegramEnabled() || !prefs.hasBotToken()) && !prefs.isLocalHttpEnabled()) {
                stop(context)
                return
            }
            val intent = Intent(context, ChannelService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChannelService::class.java))
        }

        fun restart(context: Context) {
            stop(context)
            startIfEnabled(context)
        }
    }
}
