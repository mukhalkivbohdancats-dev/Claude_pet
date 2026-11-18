package com.bohdan.claudepet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PetOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val pets = mutableListOf<PetController>()
    private var tickerRunning = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForegroundService()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_ADD -> addPet()
            ACTION_REMOVE -> removeLastPet()
            ACTION_REFRESH -> refreshImages()
        }
        return START_NOT_STICKY
    }

    private fun startAsForegroundService() {
        val openApp = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            10,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Claude Pet")
            .setContentText("Персонаж рухається поверх екрана")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Claude Pet overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Показ персонажа поверх інших програм"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun addPet() {
        val bitmap = PetImageStore.loadPixelBitmap(this)
        if (bitmap == null) {
            Toast.makeText(
                this,
                "Не вдалося відкрити фото. Вибери інше в Claude Pet.",
                Toast.LENGTH_LONG
            ).show()
            saveCount()
            if (pets.isEmpty()) stopServiceNow()
            return
        }

        val size = dp(96)
        val view = PixelPetView(this).apply {
            setImage(bitmap)
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = min(pets.size * dp(72), max(0, screenWidth() - size))
            y = floorY(size)
            alpha = 1f
        }

        val controller = PetController(view, params)
        view.setOnTouchListener { _, event -> controller.handleTouch(event) }

        try {
            windowManager.addView(view, params)
            pets.add(controller)
            saveCount()
            startTicker()
        } catch (_: Exception) {
            Toast.makeText(this, "Не вдалося показати персонажа поверх екрана.", Toast.LENGTH_LONG).show()
            saveCount()
            if (pets.isEmpty()) stopServiceNow()
        }
    }

    private fun removeLastPet() {
        if (pets.isEmpty()) {
            saveCount()
            stopServiceNow()
            return
        }
        val pet = pets.removeAt(pets.lastIndex)
        try {
            windowManager.removeView(pet.view)
        } catch (_: Exception) {
        }
        saveCount()
        if (pets.isEmpty()) stopServiceNow()
    }

    private fun refreshImages() {
        val bitmap = PetImageStore.loadPixelBitmap(this) ?: return
        pets.forEach { it.view.setImage(bitmap) }
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        handler.post(tickRunnable)
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (pets.isEmpty()) {
                tickerRunning = false
                return
            }
            pets.toList().forEach { it.tick() }
            handler.postDelayed(this, 32L)
        }
    }

    private inner class PetController(
        val view: PixelPetView,
        private val params: WindowManager.LayoutParams
    ) {
        private var direction = if (pets.size % 2 == 0) 1f else -1f
        private var phase = 0f
        private var dragging = false
        private var falling = false
        private var velocityY = 0f
        private var touchStartX = 0f
        private var touchStartY = 0f
        private var windowStartX = 0
        private var windowStartY = 0

        fun handleTouch(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    falling = false
                    velocityY = 0f
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    windowStartX = params.x
                    windowStartY = params.y
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return true
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    params.x = (windowStartX + dx.roundToInt())
                        .coerceIn(0, max(0, screenWidth() - params.width))
                    params.y = (windowStartY + dy.roundToInt())
                        .coerceIn(0, floorY(params.height))
                    if (abs(dx) > dp(2)) direction = if (dx >= 0) 1f else -1f
                    view.setMotion(phase, direction, false)
                    updateView()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) return true
                    dragging = false
                    val bottom = floorY(params.height)
                    if (params.y < bottom - dp(2)) {
                        falling = true
                        velocityY = 0f
                    } else {
                        params.y = bottom
                        falling = false
                        updateView()
                    }
                    return true
                }
            }
            return true
        }

        fun tick() {
            if (dragging) return

            val bottom = floorY(params.height)
            if (falling) {
                velocityY += dp(0.78f)
                params.y = min(bottom, params.y + max(1, velocityY.roundToInt()))
                if (params.y >= bottom) {
                    params.y = bottom
                    falling = false
                    velocityY = 0f
                }
            } else {
                params.y = bottom
                val step = max(1, dp(1.45f))
                params.x += (direction * step).roundToInt()
                val rightEdge = max(0, screenWidth() - params.width)
                if (params.x <= 0) {
                    params.x = 0
                    direction = 1f
                } else if (params.x >= rightEdge) {
                    params.x = rightEdge
                    direction = -1f
                }
            }

            phase += if (falling) 0.12f else 0.24f
            view.setMotion(phase, direction, falling)
            updateView()
        }

        private fun updateView() {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {
            }
        }
    }

    private fun saveCount() {
        PetImageStore.prefs(this).edit().putInt("active_count", pets.size).apply()
    }

    private fun stopServiceNow() {
        handler.removeCallbacks(tickRunnable)
        tickerRunning = false
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        pets.toList().forEach {
            try {
                windowManager.removeView(it.view)
            } catch (_: Exception) {
            }
        }
        pets.clear()
        saveCount()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun floorY(viewHeight: Int): Int =
        max(0, resources.displayMetrics.heightPixels - viewHeight - dp(8))

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        const val ACTION_ADD = "com.bohdan.claudepet.ADD"
        const val ACTION_REMOVE = "com.bohdan.claudepet.REMOVE"
        const val ACTION_REFRESH = "com.bohdan.claudepet.REFRESH"
        private const val CHANNEL_ID = "claude_pet_overlay"
        private const val NOTIFICATION_ID = 801
    }
}
