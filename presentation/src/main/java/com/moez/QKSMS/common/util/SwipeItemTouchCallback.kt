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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dev.octoshrimpy.quik.common.util.extensions.dpToPx
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlin.math.max
import kotlin.math.min

/**
 * Shared swipe-to-action behaviour for the conversations list and the messages list: draws the
 * themed background and action icon while a row is swiped, and emits `(itemId, direction)` on
 * release. Subclasses supply which prefs drive the left/right actions, how an action maps to an
 * icon, and (optionally) what to do after a swipe completes.
 */
abstract class SwipeItemTouchCallback(
    colors: Colors,
    disposables: CompositeDisposable,
    private val context: Context
) : ItemTouchHelper.SimpleCallback(0, 0) {

    val swipes: Subject<Pair<Long, Int>> = PublishSubject.create()

    /**
     * Setting the adapter allows us to animate back to the original position
     */
    var adapter: RecyclerView.Adapter<*>? = null

    private val backgroundPaint = Paint()
    protected var rightAction = 0
    protected var leftAction = 0
    private var swipeRightIcon: Bitmap? = null
    private var swipeLeftIcon: Bitmap? = null

    private val iconLength = 24.dpToPx(context)

    /** Action value that means "no swipe action" (0 for both lists). */
    protected abstract val noneAction: Int

    protected abstract fun rightActionObservable(): Observable<Int>
    protected abstract fun leftActionObservable(): Observable<Int>
    protected abstract fun iconForAction(action: Int, tint: Int): Bitmap?

    init {
        disposables += colors.themeObservable()
                .doOnNext { theme -> backgroundPaint.color = theme.theme }
                .subscribeOn(Schedulers.io())
                .subscribe()

        disposables += Observables
                .combineLatest(rightActionObservable(), leftActionObservable(), colors.themeObservable()
                ) { right, left, theme ->
                    rightAction = right
                    swipeRightIcon = iconForAction(right, theme.textPrimary)
                    leftAction = left
                    swipeLeftIcon = iconForAction(left, theme.textPrimary)
                    setDefaultSwipeDirs((if (right == noneAction) 0 else ItemTouchHelper.RIGHT)
                            or (if (left == noneAction) 0 else ItemTouchHelper.LEFT))
                }
                .subscribeOn(Schedulers.io())
                .subscribe()
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView = viewHolder.itemView

            if (dX > 0) {
                c.drawRect(itemView.left.toFloat(), itemView.top.toFloat(),
                        dX, itemView.bottom.toFloat(), backgroundPaint)

                swipeRightIcon?.let { icon ->
                    val availablePx = dX.toInt() - iconLength
                    if (availablePx > 0) {
                        val src = Rect(0, 0, min(availablePx, icon.width), icon.height)
                        val dstTop = itemView.top + (itemView.bottom - itemView.top - icon.height) / 2
                        val dst = Rect(iconLength, dstTop, iconLength + src.width(), dstTop + src.height())
                        c.drawBitmap(icon, src, dst, null)
                    }
                }
            } else if (dX < 0) {
                c.drawRect(itemView.right.toFloat() + dX, itemView.top.toFloat(),
                        itemView.right.toFloat(), itemView.bottom.toFloat(), backgroundPaint)

                swipeLeftIcon?.let { icon ->
                    val availablePx = -dX.toInt() - iconLength
                    if (availablePx > 0) {
                        val src = Rect(max(0, icon.width - availablePx), 0, icon.width, icon.height)
                        val dstTop = itemView.top + (itemView.bottom - itemView.top - icon.height) / 2
                        val dst = Rect(itemView.right - iconLength - src.width(), dstTop,
                                itemView.right - iconLength, dstTop + src.height())
                        c.drawBitmap(icon, src, dst, null)
                    }
                }
            }

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        swipes.onNext(Pair(viewHolder.itemId, direction))

        // Default: nothing is removed by a swipe, so always animate back to neutral
        adapter?.notifyItemChanged(viewHolder.adapterPosition)
    }

    /** Convert a drawable resource into a tinted, icon-sized bitmap. */
    protected fun actionBitmap(@androidx.annotation.DrawableRes res: Int?, tint: Int): Bitmap? =
        res?.let(context.resources::getDrawable)
                ?.apply { setTint(tint) }
                ?.toBitmap(iconLength, iconLength)

}
