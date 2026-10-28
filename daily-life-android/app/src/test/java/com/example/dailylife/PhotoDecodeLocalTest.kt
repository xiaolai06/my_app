package com.example.dailylife

import android.graphics.Bitmap
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.ui.decodePhotoPreview
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoDecodeLocalTest {
    private fun checkRotationAndBound() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val file=File(context.cacheDir,"rotated-preview.jpg")
        Bitmap.createBitmap(1600,800,Bitmap.Config.ARGB_8888).let {bitmap->
            bitmap.eraseColor(android.graphics.Color.BLUE)
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
        }
        ExifInterface(file.absolutePath).apply {setAttribute(ExifInterface.TAG_ORIENTATION,"6");saveAttributes()}
        val original=file.readBytes()
        val image=decodePhotoPreview(file)!!
        assertTrue(image.height>image.width);assertTrue(image.width<=720 && image.height<=720)
        assertArrayEquals(original,file.readBytes());image.recycle()
    }
    @Test @Config(sdk=[26]) fun legacyDecoderAppliesOrientationAndBounds()=checkRotationAndBound()
    @Test @Config(sdk=[35]) fun currentApiDecoderAppliesOrientationAndBounds()=checkRotationAndBound()
}
