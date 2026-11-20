/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.common.util

import android.content.Context
import android.graphics.Color
import androidx.core.content.res.getColorOrThrow
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.DynamicColors
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.extensions.getColorCompat
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.Observable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class Colors @Inject constructor(
    private val context: Context,
    private val prefs: Preferences
) {

    val dynamicColorsSupported: Boolean
        get() = DynamicColors.isDynamicColorAvailable()

    data class Theme(val theme: Int, private val colors: Colors) {
        val highlight by lazy { colors.highlightColorForTheme(theme) }
        val textPrimary by lazy { colors.textPrimaryOnThemeForColor(theme) }
        val textSecondary by lazy { colors.textSecondaryOnThemeForColor(theme) }
        val textTertiary by lazy { colors.textTertiaryOnThemeForColor(theme) }
    }

    val materialColors: List<List<Int>> = listOf(
        R.array.material_red,
        R.array.material_pink,
        R.array.material_purple,
        R.array.material_deep_purple,
        R.array.material_indigo,
        R.array.material_blue,
        R.array.material_light_blue,
        R.array.material_cyan,
        R.array.material_teal,
        R.array.material_green,
        R.array.material_light_green,
        R.array.material_lime,
        R.array.material_yellow,
        R.array.material_amber,
        R.array.material_orange,
        R.array.material_deep_orange,
        R.array.material_brown,
        R.array.material_gray,
        R.array.material_blue_gray)
            .map { res -> context.resources.obtainTypedArray(res) }
            .map { typedArray -> (0 until typedArray.length()).map(typedArray::getColorOrThrow) }

    private val randomColors: List<Int> = context.resources.obtainTypedArray(R.array.random_colors)
            .let { typedArray -> (0 until typedArray.length()).map(typedArray::getColorOrThrow) }

    private val minimumContrastRatio = 2.0
    private val primaryTextOnLightTheme = context.getColorCompat(R.color.textPrimary)
    private val secondaryTextOnLightTheme = context.getColorCompat(R.color.textSecondary)
    private val tertiaryTextOnLightTheme = context.getColorCompat(R.color.textTertiary)
    private val primaryTextOnDarkTheme = context.getColorCompat(R.color.textPrimaryDark)
    private val secondaryTextOnDarkTheme = context.getColorCompat(R.color.textSecondaryDark)
    private val tertiaryTextOnDarkTheme = context.getColorCompat(R.color.textTertiaryDark)

    fun theme(recipient: Recipient? = null): Theme {
        val pref = prefs.theme(recipient?.id ?: 0)
        val color = when {
            recipient == null || !prefs.autoColor.get() -> dynamicThemeColor() ?: pref.get()
            pref.isSet -> pref.get()
            else -> generateColor(recipient)
        }
        return Theme(color, this)
    }

    fun themeObservable(recipient: Recipient? = null): Observable<Theme> {
        val pref = when {
            recipient == null -> prefs.theme()
            prefs.autoColor.get() -> prefs.theme(recipient.id, generateColor(recipient))
            else -> prefs.theme(recipient.id, prefs.theme().get())
        }
        val colors = if (recipient == null || !prefs.autoColor.get()) {
            pref.asObservable()
                    .map { color ->
                        if (prefs.dynamicColors.get()) dynamicThemeColor() ?: color else color
                    }
        } else {
            pref.asObservable()
        }
        return colors
                .map { color -> Theme(color, this) }
    }

    private fun dynamicThemeColor(): Int? = dynamicColor(
        dynamicColorsContext(context, prefs),
        com.google.android.material.R.attr.colorPrimary
    )

    fun highlightColorForTheme(theme: Int): Int = FloatArray(3)
            .apply { Color.colorToHSV(theme, this) }
            .let { hsv -> hsv.apply { set(2, 0.75f) } } // 75% value
            .let { hsv -> Color.HSVToColor(85, hsv) } // 33% alpha

    fun textPrimaryOnThemeForColor(color: Int): Int = textColorOnTheme(
        themeColor = color,
        textOnDarkTheme = primaryTextOnDarkTheme,
        textOnLightTheme = primaryTextOnLightTheme
    )

    fun textSecondaryOnThemeForColor(color: Int): Int = textColorOnTheme(
        themeColor = color,
        textOnDarkTheme = secondaryTextOnDarkTheme,
        textOnLightTheme = secondaryTextOnLightTheme
    )

    fun textTertiaryOnThemeForColor(color: Int): Int = textColorOnTheme(
        themeColor = color,
        textOnDarkTheme = tertiaryTextOnDarkTheme,
        textOnLightTheme = tertiaryTextOnLightTheme
    )

    private fun textColorOnTheme(themeColor: Int, textOnDarkTheme: Int, textOnLightTheme: Int): Int {
        val contrastRatio = ColorUtils.calculateContrast(textOnDarkTheme, themeColor)
        return if (contrastRatio < minimumContrastRatio) textOnLightTheme else textOnDarkTheme
    }

    private fun generateColor(recipient: Recipient): Int {
        val index = recipient.address.hashCode().absoluteValue % randomColors.size
        return randomColors[index]
    }
}
