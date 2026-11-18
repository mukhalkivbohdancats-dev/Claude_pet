package com.bohdan.claudepet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object PetImageStore {

    private const val PREFS_NAME = "claude_pet_settings"
    private const val SOURCE_IMAGE_URL =
        "https://cdn2852.ftp.center/secure/t/MFjYhVjWZTdo2BuROBg5Nw/1795026425/2026-11-18/Xz9iD33/%D0%B7%D0%B0%D0%B2%D0%B0%D0%BD%D1%82%D0%B0%D0%B6%D0%B5%D0%BD%D0%BD%D1%8F%20%2815%29.jpeg"
    private const val MAX_DOWNLOAD_BYTES = 12L * 1024L * 1024L

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun imageFile(context: Context) =
        File(context.filesDir, "pet-character-source.jpg")

    fun hasImage(context: Context): Boolean {
        val uri = prefs(context).getString("pet_uri", null)
        return (!uri.isNullOrBlank()) || imageFile(context).let { it.exists() && it.length() > 0L }
    }

    fun saveUri(context: Context, uri: Uri) {
        prefs(context).edit().putString("pet_uri", uri.toString()).apply()
    }

    fun downloadSourceImage(context: Context): Boolean {
        val target = imageFile(context)
        if (target.exists() && target.length() > 0L) return true

        var connection: HttpURLConnection? = null
        val temporary = File(context.filesDir, "pet-character-download.tmp")
        try {
            connection = (URL(SOURCE_IMAGE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ClaudePet-Android/1.0")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return false
            val type = connection.contentType ?: ""
            if (!type.startsWith("image/")) return false
            val length = connection.contentLengthLong
            if (length > MAX_DOWNLOAD_BYTES) return false

            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_DOWNLOAD_BYTES) {
                            temporary.delete()
                            return false
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                temporary.delete()
                return false
            }

            if (target.exists()) target.delete()
            return temporary.renameTo(target)
        } catch (_: Exception) {
            temporary.delete()
            return false
        } finally {
            connection?.disconnect()
        }
    }

    fun loadPixelBitmap(context: Context): Bitmap? {
        val original = decodeOriginal(context) ?: return null
        return try {
            val maxSide = 96
            val factor = min(1f, maxSide.toFloat() / max(original.width, original.height))
            val scaledWidth = max(1, (original.width * factor).toInt())
            val scaledHeight = max(1, (original.height * factor).toInt())
            val smallSmooth = Bitmap.createScaledBitmap(
                original,
                scaledWidth,
                scaledHeight,
                true
            )
            if (smallSmooth !== original) original.recycle()

            val working = smallSmooth.copy(Bitmap.Config.ARGB_8888, true)
            if (smallSmooth !== working) smallSmooth.recycle()

            removeFlatEdgeBackground(working)
            val cropped = cropTransparentMargin(working)
            if (cropped !== working) working.recycle()

            val pixelSide = 48
            val pixelFactor = min(
                1f,
                pixelSide.toFloat() / max(cropped.width, cropped.height)
            )
            val pixelWidth = max(1, (cropped.width * pixelFactor).toInt())
            val pixelHeight = max(1, (cropped.height * pixelFactor).toInt())
            val pixel = Bitmap.createScaledBitmap(
                cropped,
                pixelWidth,
                pixelHeight,
                false
            )
            if (cropped !== pixel) cropped.recycle()
            pixel
        } catch (_: Exception) {
            if (!original.isRecycled) original.recycle()
            null
        }
    }

    private fun decodeOriginal(context: Context): Bitmap? {
        val uriString = prefs(context).getString("pet_uri", null)
        if (!uriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(uriString)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    val sample = sampleSize(bounds.outWidth, bounds.outHeight)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    context.contentResolver.openInputStream(uri)?.use {
                        val decoded = BitmapFactory.decodeStream(it, null, options)
                        if (decoded != null) return decoded
                    }
                }
            } catch (_: Exception) {
            }
        }

        val file = imageFile(context)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width / sample, height / sample) > 320) sample *= 2
        return sample
    }

    private fun removeFlatEdgeBackground(bitmap: Bitmap) {
        if (bitmap.width < 4 || bitmap.height < 4) return

        val width = bitmap.width
        val height = bitmap.height
        val corners = intArrayOf(
            bitmap.getPixel(0, 0),
            bitmap.getPixel(width - 1, 0),
            bitmap.getPixel(0, height - 1),
            bitmap.getPixel(width - 1, height - 1)
        )
        // PNGs with an existing transparent edge should keep their alpha untouched.
        if (corners.any { Color.alpha(it) < 245 }) return

        val averageRed = corners.map { Color.red(it) }.average().toInt()
        val averageGreen = corners.map { Color.green(it) }.average().toInt()
        val averageBlue = corners.map { Color.blue(it) }.average().toInt()
        val background = Color.rgb(averageRed, averageGreen, averageBlue)

        val cornersSimilar = corners.all { colorDistance(it, background) < 48 }
        if (!cornersSimilar) return

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val visited = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var head = 0
        var tail = 0

        fun addIfBackground(index: Int) {
            if (index < 0 || index >= pixels.size || visited[index]) return
            if (Color.alpha(pixels[index]) > 0 && colorDistance(pixels[index], background) <= 62) {
                visited[index] = true
                queue[tail++] = index
            }
        }

        for (x in 0 until width) {
            addIfBackground(x)
            addIfBackground((height - 1) * width + x)
        }
        for (y in 0 until height) {
            addIfBackground(y * width)
            addIfBackground(y * width + width - 1)
        }

        while (head < tail) {
            val index = queue[head++]
            pixels[index] = Color.TRANSPARENT
            val x = index % width
            val y = index / width
            if (x > 0) addIfBackground(index - 1)
            if (x < width - 1) addIfBackground(index + 1)
            if (y > 0) addIfBackground(index - width)
            if (y < height - 1) addIfBackground(index + width)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun colorDistance(first: Int, second: Int): Int {
        return abs(Color.red(first) - Color.red(second)) +
            abs(Color.green(first) - Color.green(second)) +
            abs(Color.blue(first) - Color.blue(second))
    }

    private fun cropTransparentMargin(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (Color.alpha(pixels[y * width + x]) > 20) {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }
        if (maxX < minX || maxY < minY) return source

        val padding = 2
        minX = max(0, minX - padding)
        minY = max(0, minY - padding)
        maxX = min(width - 1, maxX + padding)
        maxY = min(height - 1, maxY + padding)

        if (minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1) {
            return source
        }
        return Bitmap.createBitmap(
            source,
            minX,
            minY,
            maxX - minX + 1,
            maxY - minY + 1
        )
    }
}
