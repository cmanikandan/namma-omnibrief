package com.example

import androidx.exifinterface.media.ExifInterface
import com.example.data.remote.ImageEncoder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [ImageEncoder.transformFor].
 *
 * The bug these exist for: `BitmapFactory.decodeStream` ignores the EXIF `Orientation` tag, so a
 * newspaper photographed in portrait was uploaded — and published — on its side. Nothing on the
 * device showed it, because the gallery and Coil both honour EXIF; only the posted image was wrong.
 *
 * The mapping is pure so the 90/180/270 and mirrored cases can be pinned down without a device.
 */
class ImageOrientationTest {

    @Test
    fun `normal and undefined mean no transform`() {
        assertEquals(0f to false, ImageEncoder.transformFor(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0f to false, ImageEncoder.transformFor(ExifInterface.ORIENTATION_UNDEFINED))
    }

    @Test
    fun `an unrecognised value is treated as upright rather than throwing`() {
        // A corrupt EXIF block should cost us a rotation, never a crash.
        assertEquals(0f to false, ImageEncoder.transformFor(42))
        assertEquals(0f to false, ImageEncoder.transformFor(-1))
    }

    @Test
    fun `the three plain rotations map to their angles`() {
        assertEquals(90f to false, ImageEncoder.transformFor(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180f to false, ImageEncoder.transformFor(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270f to false, ImageEncoder.transformFor(ExifInterface.ORIENTATION_ROTATE_270))
    }

    @Test
    fun `rotate 90 is the case that produced the sideways post`() {
        // Portrait photos from the phone's main camera carry ORIENTATION_ROTATE_90.
        val (degrees, mirrored) = ImageEncoder.transformFor(ExifInterface.ORIENTATION_ROTATE_90)

        assertEquals(90f, degrees, 0f)
        assertEquals(false, mirrored)
    }

    @Test
    fun `the mirrored orientations set the flip flag`() {
        assertEquals(0f to true, ImageEncoder.transformFor(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(180f to true, ImageEncoder.transformFor(ExifInterface.ORIENTATION_FLIP_VERTICAL))
        assertEquals(90f to true, ImageEncoder.transformFor(ExifInterface.ORIENTATION_TRANSPOSE))
        assertEquals(270f to true, ImageEncoder.transformFor(ExifInterface.ORIENTATION_TRANSVERSE))
    }

    @Test
    fun `every orientation constant is handled`() {
        // Guards against a new constant silently falling through to "upright" unnoticed.
        val all = listOf(
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270
        )
        val transformed = all.count { ImageEncoder.transformFor(it) != (0f to false) }

        assertEquals("all but ORIENTATION_NORMAL must transform", all.size - 1, transformed)
    }

    @Test
    fun `the shared cap matches what the services document`() {
        // AGENTS.md and the README both cite 1600 px as the evidence that an upload went through
        // this encoder. If the constant moves, that evidence stops meaning anything.
        assertEquals(1600, ImageEncoder.MAX_IMAGE_DIMENSION_PX)
        assertEquals(85, ImageEncoder.JPEG_QUALITY)
    }
}
