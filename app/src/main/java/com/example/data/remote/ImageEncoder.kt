package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/**
 * Single place where a picked image becomes JPEG bytes.
 *
 * Both the Gemini analysis path and the X upload path go through here so they are guaranteed to see
 * the *same* picture — same orientation, same cap, same quality. They used to hold two independent
 * copies of this logic, which is exactly how the rotation bug below survived in one of them.
 *
 * ### Why orientation needs explicit handling
 *
 * Phone cameras usually write the sensor buffer unrotated and record how the phone was held in the
 * EXIF `Orientation` tag. `BitmapFactory.decodeStream` **ignores that tag** and hands back the raw
 * pixels, so a photo taken in portrait decodes sideways.
 *
 * Nothing on-device reveals this: the gallery honours EXIF, and so does Coil, so the in-app preview
 * looks upright while the bytes being uploaded are rotated 90°. The first evidence was a published
 * post with a sideways newspaper in it.
 *
 * Re-encoding is what makes this mandatory rather than cosmetic: compressing a decoded bitmap
 * writes a fresh JPEG with **no EXIF at all**, so a downstream viewer has nothing left to correct
 * with. The rotation has to be baked into the pixels here or it is lost for good.
 */
object ImageEncoder {

    /** Longest edge of the encoded image, after rotation. */
    const val MAX_IMAGE_DIMENSION_PX = 1600

    /** JPEG quality for the re-encode. 85 keeps newspaper body text legible to the model. */
    const val JPEG_QUALITY = 85

    /**
     * Decodes [uri], applies its EXIF orientation, caps the longest edge at [maxDimension] and
     * re-encodes as JPEG. Returns `null` if the image cannot be read.
     */
    fun readUriAsJpegBytes(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_IMAGE_DIMENSION_PX,
        quality: Int = JPEG_QUALITY,
        logTag: String = "ImageEncoder"
    ): ByteArray? {
        return try {
            val decoded = context.contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null

            // A second stream: decodeStream has consumed the first one.
            val orientation = readExifOrientation(context, uri, logTag)
            val upright = applyOrientation(decoded, orientation)
            val scaled = scaleToFit(upright, maxDimension)

            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(logTag, "Error reading image for upload: $uri", e)
            null
        }
    }

    /** Reads the EXIF orientation tag, falling back to [ExifInterface.ORIENTATION_NORMAL]. */
    fun readExifOrientation(context: Context, uri: Uri, logTag: String = "ImageEncoder"): Int {
        return try {
            context.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) ExifInterface.ORIENTATION_NORMAL
                else ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (e: Exception) {
            // A missing or malformed EXIF block is not a failure — it just means no rotation.
            Log.w(logTag, "Could not read EXIF orientation for $uri: ${e.message}")
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * The transform an EXIF orientation implies, as (clockwise degrees, mirrored).
     *
     * Pure and exhaustive so it can be unit tested without a device. The mirrored cases are rare in
     * the wild — front cameras and some editors produce them — but they are cheap to handle and
     * getting them wrong silently flips text, which on a newspaper photo is worse than a rotation.
     */
    fun transformFor(orientation: Int): Pair<Float, Boolean> = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f to false
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f to false
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f to false
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f to true
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f to true
        ExifInterface.ORIENTATION_TRANSPOSE -> 90f to true
        ExifInterface.ORIENTATION_TRANSVERSE -> 270f to true
        else -> 0f to false // ORIENTATION_NORMAL, ORIENTATION_UNDEFINED, anything unexpected
    }

    /** Returns [bitmap] rotated and/or mirrored per [orientation]; the same instance if it is upright. */
    fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val (degrees, mirrored) = transformFor(orientation)
        if (degrees == 0f && !mirrored) return bitmap

        val matrix = Matrix().apply {
            if (degrees != 0f) postRotate(degrees)
            if (mirrored) postScale(-1f, 1f)
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            // Better a sideways post than a crash.
            Log.e("ImageEncoder", "Out of memory rotating bitmap; using it as-is", e)
            bitmap
        }
    }

    /** Scales [bitmap] down so neither edge exceeds [maxDimension]. Never scales up. */
    fun scaleToFit(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
