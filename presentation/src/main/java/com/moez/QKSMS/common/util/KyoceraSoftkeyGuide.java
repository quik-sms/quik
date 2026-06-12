package com.moez.QKSMS.common.util;
import android.graphics.Bitmap;
import android.view.Window;

import java.lang.reflect.Method;


/**
 *  Code From:
 * https://habr.com/ru/companies/timeweb/articles/844936/
 * https://github.com/vladkorotnev/TraditionalT9/blob/c6fe6efab402afc6c2a74bcc17dbfa75cf469428/src/org/nyanya/android/traditionalt9/quirks/kyocera/KYY31SoftkeyGuide.java
 *
 * Renamed from KYY31SoftkeyGuide to KyoceraSoftkeyGuide
 */


public class KyoceraSoftkeyGuide {
    private final Object instance;

    private KyoceraSoftkeyGuide(Object instance) {
        this.instance = instance;
    }

    public static KyoceraSoftkeyGuide createFor(Window window) {
        try {
            Class<?> externalClassInstance = Class.forName("jp.kyocera.kcfp.util.KCfpSoftkeyGuide");
            Method method = externalClassInstance.getMethod("getSoftkeyGuide", Window.class);
            Object result = method.invoke(null, window); // Static method
            return result != null ? new KyoceraSoftkeyGuide(result) : null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object getCarrierSoftkeyGuide() {
        try {
            Method method = instance.getClass().getMethod("getCarrierSoftkeyGuide");
            return method.invoke(instance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean getEnabled(int index) {
        try {
            Method method = instance.getClass().getMethod("getEnabled", int.class);
            return (boolean) method.invoke(instance, index);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CharSequence getText(int index) {
        try {
            Method method = instance.getClass().getMethod("getText", int.class);
            return (CharSequence) method.invoke(instance, index);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void hide() {
        try {
            Method method = instance.getClass().getMethod("hide");
            method.invoke(instance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void invalidate() {
        try {
            Method method = instance.getClass().getMethod("invalidate");
            method.invoke(instance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void pendingApply(boolean enabled) {
        try {
            Method method = instance.getClass().getMethod("pendingApply", boolean.class);
            method.invoke(instance, enabled);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setCarrierSoftkeyGuide(Object carrierSoftkeyGuide) {
        try {
            Method method = instance.getClass().getMethod("setCarrierSoftkeyGuide", Object.class);
            method.invoke(instance, carrierSoftkeyGuide);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setDrawable(int index, int resId) {
        try {
            Method method = instance.getClass().getMethod("setDrawable", int.class, int.class);
            method.invoke(instance, index, resId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setDrawable(int index, Bitmap bitmap) {
        try {
            Method method = instance.getClass().getMethod("setDrawable", int.class, Bitmap.class);
            method.invoke(instance, index, bitmap);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setEnabled(int index, boolean enabled) {
        try {
            Method method = instance.getClass().getMethod("setEnabled", int.class, boolean.class);
            method.invoke(instance, index, enabled);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setPriority(int index, int priority) {
        try {
            Method method = instance.getClass().getMethod("setPriority", int.class, int.class);
            method.invoke(instance, index, priority);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setResource(int resId) {
        try {
            Method method = instance.getClass().getMethod("setResource", int.class);
            method.invoke(instance, resId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setText(int index, int resId) {
        try {
            Method method = instance.getClass().getMethod("setText", int.class, int.class);
            method.invoke(instance, index, resId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setText(int index, CharSequence text) {
        try {
            Method method = instance.getClass().getMethod("setText", int.class, CharSequence.class);
            method.invoke(instance, index, text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setTextDrawable(int index, int textResId, int drawableResId) {
        try {
            Method method = instance.getClass().getMethod("setTextDrawable", int.class, int.class, int.class);
            method.invoke(instance, index, textResId, drawableResId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setTextDrawable(int index, int textResId, Bitmap bitmap) {
        try {
            Method method = instance.getClass().getMethod("setTextDrawable", int.class, int.class, Bitmap.class);
            method.invoke(instance, index, textResId, bitmap);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setTextDrawable(int index, CharSequence text, int drawableResId) {
        try {
            Method method = instance.getClass().getMethod("setTextDrawable", int.class, CharSequence.class, int.class);
            method.invoke(instance, index, text, drawableResId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setTextDrawable(int index, CharSequence text, Bitmap bitmap) {
        try {
            Method method = instance.getClass().getMethod("setTextDrawable", int.class, CharSequence.class, Bitmap.class);
            method.invoke(instance, index, text, bitmap);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void show() {
        try {
            Method method = instance.getClass().getMethod("show");method.invoke(instance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void translucent(boolean enabled) {
        try {
            Method method = instance.getClass().getMethod("translucent", boolean.class);
            method.invoke(instance, enabled);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}