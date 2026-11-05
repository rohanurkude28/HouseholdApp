package com.houseaffairs.app.core.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Photos of kept things, medicines, dishes and stock, kept on this phone (and backed up to
 * Drive › HouseAffairs › Photos). An entry stores only the photo ids; the file is
 * photos/<id>.jpg here, so the same id finds it again after a restore.
 */
object PhotoStore {
    /** Long side in pixels: sharp on a phone, a few hundred KB each. */
    private const val MAX_SIDE = 1600

    fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(context: Context, id: String): File = File(dir(context), "$id.jpg")

    fun has(context: Context, id: String): Boolean = file(context, id).exists()

    /** Copies a picked or taken picture in, upright and resized; returns its new id. */
    suspend fun import(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decode(context, uri)
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        file(context, id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        bitmap.recycle()
        id
    }

    fun delete(context: Context, id: String) {
        file(context, id).delete()
    }

    /** A place for the camera app to write a new picture, shared through the FileProvider. */
    fun cameraTarget(context: Context): Uri {
        val f = File(File(context.cacheDir, "camera").apply { mkdirs() }, "shot-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder turns camera pictures upright on its own.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("Couldn't read that picture")
    }
}
