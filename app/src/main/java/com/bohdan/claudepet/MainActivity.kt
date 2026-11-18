package com.bohdan.claudepet

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private lateinit var addButton: Button
    private var addAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 21, 29)
        window.navigationBarColor = Color.rgb(16, 21, 29)
        buildScreen()
        refreshStatus()

        if (!PetImageStore.hasImage(this)) {
            statusText.text = "Завантажую фото персонажа…"
            Thread {
                val loaded = PetImageStore.downloadSourceImage(this)
                runOnUiThread {
                    statusText.text = if (loaded) {
                        "Фото завантажено. Можна додавати персонажа."
                    } else {
                        "Не вдалося завантажити фото за посиланням. Натисни «Вибрати фото»."
                    }
                    refreshStatus()
                }
            }.start()
        }
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(16, 21, 29))
            isFillViewport = true
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }
        scroll.addView(page)
        setContentView(scroll)

        val eyebrow = text("DESKTOP COMPANION  /  ANDROID", 11, 0xFF85E0B0.toInt(), true)
        eyebrow.letterSpacing = 0.12f
        page.addView(eyebrow, margin(0, 0, 0, 14))

        val title = text("Claude Pet", 34, Color.WHITE, true)
        page.addView(title, margin(0, 0, 0, 7))

        val subtitle = text(
            "Твій маленький персонаж живе поверх інших програм.",
            15,
            0xFFADB8C7.toInt(),
            false
        )
        page.addView(subtitle, margin(0, 0, 0, 24))

        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF192330.toInt(), 22)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        page.addView(previewCard, margin(0, 0, 0, 18))

        previewCard.addView(text("СТАН ПЕРСОНАЖА", 11, 0xFF8C9AAC.toInt(), true))
        statusText = text("Перевіряю фото…", 15, Color.WHITE, false)
        statusText.setLineSpacing(dp(3).toFloat(), 1f)
        previewCard.addView(statusText, margin(0, 10, 0, 12))

        countText = text("На екрані: 0", 13, 0xFF85E0B0.toInt(), true)
        previewCard.addView(countText)

        val add = makeButton("＋  Додати персонажа", 0xFF85E0B0.toInt(), 0xFF102019.toInt())
        addButton = add
        page.addView(add, margin(0, 4, 0, 10))
        add.setOnClickListener { addPet() }

        val remove = makeButton("−  Прибрати останнього", 0xFF263343.toInt(), Color.WHITE)
        page.addView(remove, margin(0, 0, 0, 10))
        remove.setOnClickListener { removePet() }

        val photo = makeButton("Вибрати / змінити фото", 0xFF263343.toInt(), Color.WHITE)
        page.addView(photo, margin(0, 0, 0, 18))
        photo.setOnClickListener { choosePhoto() }

        val helpCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF141D27.toInt(), 18)
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
        page.addView(helpCard, margin(0, 0, 0, 16))
        helpCard.addView(text("ЯК ЦЕ ПРАЦЮЄ", 11, 0xFF85E0B0.toInt(), true))
        helpCard.addView(
            text(
                "• Дозволь показ поверх інших програм.\n" +
                    "• Перетягни персонажа пальцем у будь-яке місце.\n" +
                    "• Відпусти вище нижнього краю — він плавно впаде вниз.\n" +
                    "• Далі персонаж повзатиме вздовж нижнього краю екрана.\n" +
                    "• Кнопка «Прибрати» забирає останнього доданого.",
                13,
                0xFFB7C2D0.toInt(),
                false
            ).apply { setLineSpacing(dp(5).toFloat(), 1f) },
            margin(0, 10, 0, 0)
        )

        page.addView(
            text(
                "Дозвіл Android потрібен лише для показу рухомого персонажа поверх вікон. " +
                    "Його можна вимкнути в налаштуваннях системи.",
                12,
                0xFF778698.toInt(),
                false
            )
        )
    }

    private fun addPet() {
        if (!PetImageStore.hasImage(this)) {
            statusText.text = "Спочатку вибери фото персонажа."
            choosePhoto()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            addAfterPermission = true
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
            )
            startActivity(intent)
            return
        }
        startPetService(PetOverlayService.ACTION_ADD)
        statusText.text = "Персонаж доданий і вже повзає на екрані."
    }

    private fun removePet() {
        val current = PetImageStore.prefs(this).getInt("active_count", 0)
        if (current <= 0) {
            statusText.text = "Зараз на екрані немає персонажів."
            refreshStatus()
            return
        }
        startPetService(PetOverlayService.ACTION_REMOVE)
        statusText.text = "Прибираю останнього персонажа…"
    }

    private fun choosePhoto() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMAGE)
    }

    @Deprecated("Uses the platform document picker for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            PetImageStore.saveUri(this, uri)
            statusText.text = "Фото збережено. Тепер можна додати персонажа."
            val count = PetImageStore.prefs(this).getInt("active_count", 0)
            if (count > 0) startPetService(PetOverlayService.ACTION_REFRESH)
            refreshStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        if (addAfterPermission && Settings.canDrawOverlays(this)) {
            addAfterPermission = false
            addPet()
        }
        refreshStatus()
    }

    private fun startPetService(action: String) {
        val intent = Intent(this, PetOverlayService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun refreshStatus() {
        if (!::statusText.isInitialized) return
        val count = PetImageStore.prefs(this).getInt("active_count", 0)
        val photoReady = PetImageStore.hasImage(this)
        countText.text = "На екрані: " + count
        if (!photoReady) {
            addButton.alpha = 0.72f
            if (statusText.text.isNullOrBlank()) {
                statusText.text = "Потрібне фото персонажа."
            }
        } else {
            addButton.alpha = 1f
        }
        if (!Settings.canDrawOverlays(this)) {
            statusText.text = if (photoReady) {
                "Фото готове. Для показу персонажа потрібен дозвіл Android."
            } else {
                "Спочатку вибери фото персонажа."
            }
        }
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD)
                else Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    private fun makeButton(label: String, backgroundColor: Int, textColor: Int): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(textColor)
            background = rounded(backgroundColor, 16)
            minHeight = dp(54)
            elevation = dp(1).toFloat()
            stateListAnimator = null
        }
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun margin(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val REQUEST_IMAGE = 401
    }
}
