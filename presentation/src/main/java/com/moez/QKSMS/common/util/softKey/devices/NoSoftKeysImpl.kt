package com.moez.QKSMS.common.util.softKey.devices

import android.graphics.Bitmap
import android.view.Window
import com.moez.QKSMS.common.util.softKey.SoftKeyGuide


class NoSoftKeysImpl : SoftKeyGuide {  // set once at construction, never null


    override fun getCarrierSoftkeyGuide(): Any? {
        return null
    }

    override fun getEnabled(index: Int): Boolean {
        return false
    }

    override fun getText(index: Int): CharSequence? {
        return null
    }

    override fun hide() {
    }

    override fun invalidate() {
    }

    override fun pendingApply(enabled: Boolean) {
    }

    override fun setCarrierSoftkeyGuide(carrierSoftkeyGuide: Any) {
    }

    override fun setDrawable(index: Int, resId: Int) {
    }

    override fun setDrawable(index: Int, bitmap: Bitmap) {
    }

    override fun setEnabled(index: Int, enabled: Boolean) {
    }

    override fun setPriority(index: Int, priority: Int) {
    }

    override fun setResource(resId: Int) {
    }

    override fun setText(index: Int, resId: Int) {
    }

    override fun setText(index: Int, text: CharSequence) {
    }

    override fun setTextDrawable(index: Int, textResId: Int, drawableResId: Int) {
    }

    override fun setTextDrawable(index: Int, textResId: Int, bitmap: Bitmap) {
    }

    override fun setTextDrawable(index: Int, text: CharSequence, drawableResId: Int) {
    }

    override fun setTextDrawable(index: Int, text: CharSequence, bitmap: Bitmap) {
    }

    override fun show() {
    }

    override fun translucent(enabled: Boolean) {
    }



    companion object {
//        fun isSuporported(): Boolean = try {
//            Class.forName("jp.kyocera.kcfp.util.KCfpSoftkeyGuide")
//            true
//        } catch (e: ClassNotFoundException) {
//            false
//        }

        fun createFor(window: Window?): NoSoftKeysImpl? = null
    }
}