package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.remote.ImageEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Proves on a real device that [ImageEncoder] bakes the EXIF orientation into the pixels it hands
 * to the uploader.
 *
 * This has to be an instrumented test, not a JVM one. The bug lives in the interaction between
 * three real Android components — `BitmapFactory` (ignores EXIF), `ExifInterface` (reads it) and
 * `Bitmap.compress` (writes a JPEG with no EXIF at all) — and Robolectric shadows do not reproduce
 * that interaction faithfully enough to be worth trusting here.
 *
 * It is also the only *non-destructive* way to verify the fix. The in-app preview cannot show the
 * defect because Coil honours EXIF and renders the picture upright regardless, so the alternative
 * was publishing a real post to X and looking at it.
 *
 * Each case paints an asymmetric marker in one corner and checks where it lands, so a test failure
 * distinguishes "rotated the wrong way" from "did not rotate" — swapped dimensions alone would not.
 */
@RunWith(AndroidJUnit4::class)
class ImageEncoderInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Source is deliberately landscape so a 90° turn changes the dimensions observably. */
    private val sourceWidth = 400
    private val sourceHeight = 200
    private val markerSize = 80

    /**
     * Writes a white JPEG with a red square in its top-left corner and stamps [orientation] into
     * the EXIF block, mimicking what a phone camera produces.
     */
    private fun writeTaggedJpeg(orientation: Int, name: String): Uri {
        val bitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(
                0f, 0f, markerSize.toFloat(), markerSize.toFloat(),
                Paint().apply { color = Color.RED }
            )
        }

        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    private fun encode(uri: Uri): Bitmap {
        val bytes = ImageEncoder.readUriAsJpegBytes(context, uri)
            ?: error("Encoder returned null for $uri")
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Encoded bytes did not decode as an image")
    }

    /** JPEG is lossy, so sample a generous inset and test the channel balance, not an exact value. */
    private fun isReddish(bitmap: Bitmap, x: Int, y: Int): Boolean {
        val pixel = bitmap.getPixel(x, y)
        return Color.red(pixel) > 150 && Color.green(pixel) < 110 && Color.blue(pixel) < 110
    }

    private fun describe(bitmap: Bitmap): String {
        val inset = markerSize / 2
        val corners = mapOf(
            "top-left" to (inset to inset),
            "top-right" to (bitmap.width - inset to inset),
            "bottom-left" to (inset to bitmap.height - inset),
            "bottom-right" to (bitmap.width - inset to bitmap.height - inset)
        )
        val found = corners.filter { (_, p) -> isReddish(bitmap, p.first, p.second) }.keys
        return "${bitmap.width}x${bitmap.height}, marker in $found"
    }

    @Test
    fun orientationNormalIsLeftAlone() {
        val result = encode(writeTaggedJpeg(ExifInterface.ORIENTATION_NORMAL, "exif_normal.jpg"))
        val inset = markerSize / 2

        assertEquals("width unchanged", sourceWidth, result.width)
        assertEquals("height unchanged", sourceHeight, result.height)
        assertTrue(
            "An upright image must not be rotated. Got ${describe(result)}",
            isReddish(result, inset, inset)
        )
    }

    @Test
    fun orientationRotate90IsBakedIn() {
        val result = encode(writeTaggedJpeg(ExifInterface.ORIENTATION_ROTATE_90, "exif_90.jpg"))
        val inset = markerSize / 2

        // A 90° clockwise turn swaps the axes: the landscape source becomes portrait.
        assertEquals("width should become the source height", sourceHeight, result.width)
        assertEquals("height should become the source width", sourceWidth, result.height)

        // (0,0) in the stored pixels ends up top-right after turning clockwise.
        assertTrue(
            "Marker should have moved to the top-right. Got ${describe(result)}",
            isReddish(result, result.width - inset, inset)
        )
    }

    @Test
    fun orientationRotate270IsBakedIn() {
        val result = encode(writeTaggedJpeg(ExifInterface.ORIENTATION_ROTATE_270, "exif_270.jpg"))
        val inset = markerSize / 2

        assertEquals("width should become the source height", sourceHeight, result.width)
        assertEquals("height should become the source width", sourceWidth, result.height)

        // Turning the other way sends (0,0) to the bottom-left.
        assertTrue(
            "Marker should have moved to the bottom-left. Got ${describe(result)}",
            isReddish(result, inset, result.height - inset)
        )
    }

    @Test
    fun orientationRotate180IsBakedIn() {
        val result = encode(writeTaggedJpeg(ExifInterface.ORIENTATION_ROTATE_180, "exif_180.jpg"))
        val inset = markerSize / 2

        assertEquals("width unchanged by a half turn", sourceWidth, result.width)
        assertEquals("height unchanged by a half turn", sourceHeight, result.height)
        assertTrue(
            "Marker should have moved to the bottom-right. Got ${describe(result)}",
            isReddish(result, result.width - inset, result.height - inset)
        )
    }

    /**
     * The encoded bytes must not carry an orientation tag of their own.
     *
     * If they did, a viewer that honours EXIF would rotate the already-rotated pixels a second time
     * and the post would be sideways the other way — a regression that would look like the original
     * bug and be diagnosed as "the fix did nothing".
     */
    @Test
    fun encodedOutputCarriesNoResidualOrientation() {
        val uri = writeTaggedJpeg(ExifInterface.ORIENTATION_ROTATE_90, "exif_residual.jpg")
        val bytes = ImageEncoder.readUriAsJpegBytes(context, uri)
            ?: error("Encoder returned null")

        val orientation = bytes.inputStream().use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
        assertTrue(
            "Output must not ask a viewer to rotate again, but reported orientation $orientation",
            orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED
        )
    }

    /** The cap applies to the upright image, so a tall photo is capped on its true long edge. */
    @Test
    fun rotationHappensBeforeTheSizeCap() {
        val wide = Bitmap.createBitmap(3000, 1000, Bitmap.Config.ARGB_8888)
        Canvas(wide).drawColor(Color.WHITE)
        val file = File(context.cacheDir, "exif_large.jpg")
        FileOutputStream(file).use { wide.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.absolutePath).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString()
            )
            saveAttributes()
        }

        val result = encode(Uri.fromFile(file))

        assertEquals("long edge capped", ImageEncoder.MAX_IMAGE_DIMENSION_PX, result.height)
        assertTrue("still portrait after rotation", result.height > result.width)
    }
}
