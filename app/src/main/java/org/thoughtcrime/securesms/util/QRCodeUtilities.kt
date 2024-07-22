package org.thoughtcrime.securesms.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QRCodeUtilities {

    fun encode(
        data: String,
        size: Int,
        isInverted: Boolean = false,
        hasTransparentBackground: Boolean = true,
        dark: Int = Color.BLACK,
        light: Int = Color.WHITE,
    ): Bitmap? = runCatching {
        val hints = hashMapOf(
            EncodeHintType.MARGIN to 0,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        val color = if (isInverted) light else dark
        val background = if (isInverted) dark else light
        val result = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, hints)
        Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until result.height) {
                for (x in 0 until result.width) {
                    when {
                        result.get(x, y) -> setPixel(x, y, color)
                        !hasTransparentBackground -> setPixel(x, y, background)
                    }
                }
            }
        }
    }.getOrNull()
}
