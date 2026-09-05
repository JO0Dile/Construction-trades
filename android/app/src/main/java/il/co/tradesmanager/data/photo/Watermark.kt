package il.co.tradesmanager.data.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import java.io.File

/**
 * Burns the date, place and photographer into the corner of a site photograph.
 *
 * Into the pixels rather than over the top at display time, because the point
 * is what happens after the photograph leaves the app: it gets emailed to a
 * loss adjuster, printed for a meeting, attached to a claim. An overlay drawn
 * by the app is gone by then and the picture is just a wall.
 *
 * Three things this has to get right, all of which are easy to get wrong:
 *
 * **Orientation.** Phones store portrait photographs as landscape pixels plus
 * an EXIF flag saying which way up. Decoding and re-encoding without reading
 * that flag is the classic way to turn everybody's photographs sideways — with
 * the stamp sideways too.
 *
 * **Memory.** A twelve-megapixel photograph is about forty-eight megabytes once
 * decoded. Doing that on a cheap phone with three other apps open is how the
 * camera screen dies silently, so it is sampled down on the way in.
 *
 * **Failing safely.** If any of this goes wrong the original file is left
 * exactly as it was. An unstamped photograph is worth much more than no
 * photograph, and a stamp is not worth losing a picture of a defect over.
 */
object Watermark {

    /**
     * Two thousand pixels on the long edge. Far more than enough to read a
     * crack or a serial number off, small enough that a phone can hold two of
     * them at once, and it keeps a site's worth of photographs from filling
     * the device.
     */
    private const val MAX_EDGE = 2048

    private const val QUALITY = 90

    /** Returns true when the file now carries a stamp. */
    fun burn(file: File, lines: List<String>): Boolean {
        if (lines.isEmpty() || !file.exists() || file.length() == 0L) return false
        return runCatching { stamp(file, lines) }.getOrDefault(false)
    }

    private fun stamp(file: File, lines: List<String>): Boolean {
        val rotation = rotationOf(file)
        val decoded = decodeScaled(file) ?: return false
        val upright = rotate(decoded, rotation)

        val canvas = Canvas(upright)
        draw(canvas, upright.width, upright.height, lines)

        // Written beside the original and moved into place, so a phone that
        // dies mid-write leaves the original photograph rather than half a file.
        val temporary = File(file.parentFile, "${file.name}.stamping")
        val written = runCatching {
            temporary.outputStream().use { out ->
                upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
        }.getOrDefault(false)
        upright.recycle()
        if (decoded !== upright) decoded.recycle()

        if (!written || temporary.length() == 0L) {
            temporary.delete()
            return false
        }
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
        return true
    }

    private fun rotationOf(file: File): Int = runCatching {
        when (
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun decodeScaled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > MAX_EDGE) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            // Mutable, because the stamp is drawn straight onto it.
            inMutable = true
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    /**
     * A band across the bottom rather than floating text.
     *
     * Site photographs are of concrete, sky, and hi-vis jackets, and white
     * text on any of those is unreadable somewhere in the frame. A dark band
     * gives the text something to sit on whatever is behind it.
     */
    private fun draw(canvas: Canvas, width: Int, height: Int, lines: List<String>) {
        val textSize = (width / 34f).coerceIn(22f, 64f)
        val padding = textSize * 0.6f
        val lineHeight = textSize * 1.35f
        val bandHeight = lineHeight * lines.size + padding * 2f

        val band = Paint().apply {
            color = Color.argb(160, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawRect(0f, height - bandHeight, width.toFloat(), height.toFloat(), band)

        val text = Paint().apply {
            color = Color.WHITE
            this.textSize = textSize
            isAntiAlias = true
        }
        var baseline = height - bandHeight + padding + textSize
        lines.forEach { line ->
            canvas.drawText(line, padding, baseline, text)
            baseline += lineHeight
        }
    }
}
