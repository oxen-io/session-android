package com.session.beautiful.util

import android.content.Context

object DensityUtil {
    /**
     * 根据手机的分辨率从 dp(相对大小) 的单位 转成为 px(像素)
     */
    fun dpToPx(context: Context, data: Float): Int {
        // 获取屏幕密度
        val scale: Float = context.getResources().getDisplayMetrics().density
        // 结果+0.5是为了int取整时更接近
        return (data * scale + 0.5f).toInt()
    }

    /**
     * 根据手机的分辨率从 px(像素) 的单位 转成为 dp(相对大小)
     */
    fun pxToDp(context: Context, data: Float): Int {
        val scale: Float = context.getResources().getDisplayMetrics().density
        return (data / scale + 0.5f).toInt()
    }
}