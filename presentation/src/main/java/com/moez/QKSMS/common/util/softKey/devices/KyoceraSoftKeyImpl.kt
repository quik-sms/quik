package com.moez.QKSMS.common.util.softKey.devices

import android.graphics.Bitmap
import android.view.Window
import com.moez.QKSMS.common.util.softKey.SoftKeyGuide


/**
 *  Code From:
 * https://habr.com/ru/companies/timeweb/articles/844936/
 * https://github.com/vladkorotnev/TraditionalT9/blob/c6fe6efab402afc6c2a74bcc17dbfa75cf469428/src/org/nyanya/android/traditionalt9/quirks/kyocera/KYY31SoftkeyGuide.java
 *
 * Renamed from KYY31SoftkeyGuide to KyoceraSoftkeyGuide
 */

class KyoceraSoftKeyImpl private constructor(private val instance: Any) :
    SoftKeyGuide {  // set once at construction, never null


    override fun getCarrierSoftkeyGuide(): Any? {
        try {
            val method = instance.javaClass.getMethod("getCarrierSoftkeyGuide")
            return method.invoke(instance)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun getEnabled(index: Int): Boolean {
        try {
            val method = instance.javaClass.getMethod("getEnabled", Int::class.javaPrimitiveType)
            return method.invoke(instance, index) as Boolean
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun getText(index: Int): CharSequence? {
        try {
            val method = instance.javaClass.getMethod("getText", Int::class.javaPrimitiveType)
            return method.invoke(instance, index) as CharSequence?
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun hide() {
        try {
            val method = instance.javaClass.getMethod("hide")
            method.invoke(instance)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun invalidate() {
        try {
            val method = instance.javaClass.getMethod("invalidate")
            method.invoke(instance)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun pendingApply(enabled: Boolean) {
        try {
            val method =
                instance.javaClass.getMethod("pendingApply", Boolean::class.javaPrimitiveType)
            method.invoke(instance, enabled)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setCarrierSoftkeyGuide(carrierSoftkeyGuide: Any) {
        try {
            val method = instance.javaClass.getMethod("setCarrierSoftkeyGuide", Any::class.java)
            method.invoke(instance, carrierSoftkeyGuide)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setDrawable(index: Int, resId: Int) {
        try {
            val method = instance.javaClass.getMethod(
                "setDrawable", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            method.invoke(instance, index, resId)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setDrawable(index: Int, bitmap: Bitmap) {
        try {
            val method = instance.javaClass.getMethod(
                "setDrawable", Int::class.javaPrimitiveType, Bitmap::class.java
            )
            method.invoke(instance, index, bitmap)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setEnabled(index: Int, enabled: Boolean) {
        try {
            val method = instance.javaClass.getMethod(
                "setEnabled", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
            )
            method.invoke(instance, index, enabled)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setPriority(index: Int, priority: Int) {
        try {
            val method = instance.javaClass.getMethod(
                "setPriority", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            method.invoke(instance, index, priority)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setResource(resId: Int) {
        try {
            val method = instance.javaClass.getMethod("setResource", Int::class.javaPrimitiveType)
            method.invoke(instance, resId)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setText(index: Int, resId: Int) {
        try {
            val method = instance.javaClass.getMethod(
                "setText", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            method.invoke(instance, index, resId)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setText(index: Int, text: CharSequence) {
        try {
            val method = instance.javaClass.getMethod(
                "setText", Int::class.javaPrimitiveType, CharSequence::class.java
            )
            method.invoke(instance, index, text)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setTextDrawable(index: Int, textResId: Int, drawableResId: Int) {
        try {
            val method = instance.javaClass.getMethod(
                "setTextDrawable",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(instance, index, textResId, drawableResId)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setTextDrawable(index: Int, textResId: Int, bitmap: Bitmap) {
        try {
            val method = instance.javaClass.getMethod(
                "setTextDrawable",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Bitmap::class.java
            )
            method.invoke(instance, index, textResId, bitmap)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setTextDrawable(index: Int, text: CharSequence, drawableResId: Int) {
        try {
            val method = instance.javaClass.getMethod(
                "setTextDrawable",
                Int::class.javaPrimitiveType,
                CharSequence::class.java,
                Int::class.javaPrimitiveType
            )
            method.invoke(instance, index, text, drawableResId)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun setTextDrawable(index: Int, text: CharSequence, bitmap: Bitmap) {
        try {
            val method = instance.javaClass.getMethod(
                "setTextDrawable",
                Int::class.javaPrimitiveType,
                CharSequence::class.java,
                Bitmap::class.java
            )
            method.invoke(instance, index, text, bitmap)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun show() {
        try {
            val method = instance.javaClass.getMethod("show")
            method.invoke(instance)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    override fun translucent(enabled: Boolean) {
        try {
            val method =
                instance.javaClass.getMethod("translucent", Boolean::class.javaPrimitiveType)
            method.invoke(instance, enabled)
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    /**
     * Check if the device is a kyocera
     */
    companion object {
        fun createFor(window: Window?): KyoceraSoftKeyImpl? = try {
            val cls = Class.forName("jp.kyocera.kcfp.util.KCfpSoftkeyGuide")
            val method = cls.getMethod("getSoftkeyGuide", Window::class.java)
            val result = method.invoke(null, window)
            if (result != null) KyoceraSoftKeyImpl(result) else null
        } catch (e: Exception) {
            null
        }
    }
}