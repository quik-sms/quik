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
package dev.octoshrimpy.quik.common

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.AbsListView
import android.widget.CheckedTextView
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.TextViewStyler
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

data class MenuItem(val title: String, val actionId: Int)

/**
 * Wrapper around a Material single-choice dialog.
 */
class QkDialog @Inject constructor(
    private val context: Context,
    private val colors: Colors,
    private val textViewStyler: TextViewStyler
) {

    val menuItemClicks: Subject<Int> = PublishSubject.create()

    var data: List<MenuItem> = emptyList()
    var selectedItem: Int? = null

    var title: String? = null

    fun setData(@ArrayRes titles: Int, @ArrayRes values: Int = -1) {
        val valueInts = if (values != -1) context.resources.getIntArray(values) else null

        data = context.resources.getStringArray(titles)
                .mapIndexed { index, title -> MenuItem(title, valueInts?.getOrNull(index) ?: index) }
    }

    fun show(activity: Activity) {
        val items = data.map { item -> item.title as CharSequence }.toTypedArray()
        val selectedIndex = selectedItem?.let { selected ->
            data.indexOfFirst { item -> item.actionId == selected }
        } ?: -1

        val dialog = MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setSingleChoiceItems(items, selectedIndex) { dialog, index ->
                    menuItemClicks.onNext(data[index].actionId)
                    dialog.dismiss()
                }
                .create()

        dialog.show()
        styleRows(dialog, activity)
    }

    private fun styleRows(dialog: AlertDialog, activity: Activity) {
        val list = dialog.listView ?: return
        val tint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(
                colors.theme().theme,
                activity.resolveThemeColor(android.R.attr.textColorTertiary)
            )
        )

        fun styleRow(view: View) {
            (view as? CheckedTextView)?.let { textView ->
                TextViewCompat.setCompoundDrawableTintList(textView, tint)
                textViewStyler.applyFont(textView)
                textViewStyler.setTextSize(textView, TextViewStyler.SIZE_PRIMARY)
            }
        }

        fun styleVisibleRows() {
            (0 until list.childCount).forEach { index -> styleRow(list.getChildAt(index)) }
        }

        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) = styleVisibleRows()

            override fun onScroll(
                view: AbsListView?,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) = styleVisibleRows()
        })
        styleVisibleRows()
    }

    fun setTitle(@StringRes title: Int) {
        this.title = context.getString(title)
    }

}