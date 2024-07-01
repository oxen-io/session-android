package org.thoughtcrime.securesms.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

object QRCodeUtilities {

    fun encode(
        data: String,
        size: Int,
        isInverted: Boolean = false,
        hasTransparentBackground: Boolean = true,
        dark: Int = Color.BLACK,
        light: Int = Color.WHITE,
    ): Bitmap {
        try {
            val hints = hashMapOf( EncodeHintType.MARGIN to 1 )
            val result = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, hints)
            val bitmap = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            val color = if (isInverted) light else dark
            val background = if (isInverted) dark else light
            for (y in 0 until result.height) {
                for (x in 0 until result.width) {
                    if (result.get(x, y)) {
                        bitmap.setPixel(x, y, color)
                    } else if (!hasTransparentBackground) {
                        bitmap.setPixel(x, y, background)
                    }
                }
            }
            return bitmap
        } catch (e: WriterException) {
            return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        }
    }
}