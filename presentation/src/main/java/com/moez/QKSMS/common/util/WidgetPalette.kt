package dev.octoshrimpy.quik.common.util

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.extensions.getColorCompat
import dev.octoshrimpy.quik.util.Preferences
import javax.inject.Inject
import javax.inject.Singleton

data class WidgetPalette(
    val background: Int,
    val toolbar: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textTertiary: Int
)

@Singleton
class WidgetPaletteResolver @Inject constructor(
    private val context: Context,
    private val prefs: Preferences
) {

    fun resolve(): WidgetPalette {
        val night = isNightMode(context, prefs)
        val black = night && prefs.black.get()
        val dynamicContext = dynamicColorsContext(context, prefs, night)

        val background = if (black) {
            context.getColorCompat(R.color.black)
        } else {
            resolveColor(
                dynamicContext,
                com.google.android.material.R.attr.colorSurface,
                if (night) R.color.backgroundDark else R.color.backgroundLight
            )
        }

        val textSecondary = resolveColor(
            dynamicContext,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            if (night) R.color.textSecondaryDark else R.color.textSecondary
        )

        return WidgetPalette(
            background = background,
            toolbar = background,
            textPrimary = resolveColor(
                dynamicContext,
                com.google.android.material.R.attr.colorOnSurface,
                if (night) R.color.textPrimaryDark else R.color.textPrimary
            ),
            textSecondary = textSecondary,
            textTertiary = dynamicColor(
                dynamicContext,
                com.google.android.material.R.attr.colorOnSurfaceVariant
            ) ?: context.getColorCompat(
                if (night) R.color.textTertiaryDark else R.color.textTertiary
            )
        )
    }

    private fun resolveColor(dynamicContext: Context?, attribute: Int, fallbackRes: Int): Int {
        return dynamicColor(dynamicContext, attribute) ?: context.getColorCompat(fallbackRes)
    }
}

internal fun dynamicColorsContext(
    context: Context,
    prefs: Preferences,
    isNight: Boolean = isNightMode(context, prefs)
): Context? {
    if (!DynamicColors.isDynamicColorAvailable() || !prefs.dynamicColors.get()) return null

    val configuration = Configuration(context.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (isNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    val baseTheme = if (prefs.black.get()) R.style.AppTheme_Black else R.style.AppTheme
    val baseContext = ContextThemeWrapper(context.createConfigurationContext(configuration), baseTheme)
    val overlay = if (prefs.black.get()) {
        R.style.ThemeOverlay_Quik_DynamicColors_Black
    } else {
        R.style.ThemeOverlay_Quik_DynamicColors
    }
    return DynamicColors.wrapContextIfAvailable(baseContext, overlay)
}

internal fun dynamicColor(dynamicContext: Context?, attribute: Int): Int? {
    return dynamicContext?.let {
        MaterialColors.getColor(it, attribute, 0).takeIf { color -> color != 0 }
    }
}

private fun isNightMode(context: Context, prefs: Preferences): Boolean = when (prefs.nightMode.get()) {
    Preferences.NIGHT_MODE_SYSTEM ->
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    else -> prefs.night.get()
}