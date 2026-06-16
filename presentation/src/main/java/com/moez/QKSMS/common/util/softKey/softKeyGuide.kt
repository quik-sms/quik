package com.moez.QKSMS.common.util.softKey

import android.graphics.Bitmap
import android.view.Window
import com.moez.QKSMS.common.util.softKey.devices.KyoceraSoftKeyImpl
import com.moez.QKSMS.common.util.softKey.devices.NoSoftKeysImpl



interface SoftKeyGuide{

    fun getCarrierSoftkeyGuide(): Any? = null

    fun getEnabled(index: Int): Boolean = false
    fun getText(index: Int): CharSequence? = null
    fun hide() {}
    fun invalidate() {}
    fun pendingApply(enabled: Boolean) {}
    fun setCarrierSoftkeyGuide(carrierSoftkeyGuide: Any) {}
    fun setDrawable(index: Int, resId: Int) {}
    fun setDrawable(index: Int, bitmap: Bitmap) {}
    fun setEnabled(index: Int, enabled: Boolean) {}
    fun setPriority(index: Int, priority: Int) {}
    fun setResource(resId: Int) {}
    fun setText(index: Int, resId: Int) {}
    fun setText(index: Int, text: CharSequence) {}
    fun setTextDrawable(index: Int, textResId: Int, drawableResId: Int) {}
    fun setTextDrawable(index: Int, textResId: Int, bitmap: Bitmap) {}
    fun setTextDrawable(index: Int, text: CharSequence, drawableResId: Int) {}
    fun setTextDrawable(index: Int, text: CharSequence, bitmap: Bitmap) {}
    fun show() {}
    fun translucent(enabled: Boolean) {}

    /**
     * Figure out what device and return appropriate instance
     */
    companion object {
        fun create(window: Window): SoftKeyGuide {
            return KyoceraSoftKeyImpl.createFor(window)
//                ?: SharpSoftKeyImpl.createFor(window)
                ?: NoSoftKeysImpl()
        }
    }
}