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
package dev.octoshrimpy.quik.feature.compose

import android.content.Context
import android.graphics.Bitmap
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.SwipeItemTouchCallback
import dev.octoshrimpy.quik.feature.settings.swipe.messageSwipeActionIcon
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class MessageItemTouchCallback @Inject constructor(
    colors: Colors,
    disposables: CompositeDisposable,
    private val prefs: Preferences,
    context: Context
) : SwipeItemTouchCallback(colors, disposables, context) {

    override val noneAction = Preferences.MESSAGE_SWIPE_ACTION_NONE

    override fun rightActionObservable(): Observable<Int> = prefs.messageSwipeRight.asObservable()
    override fun leftActionObservable(): Observable<Int> = prefs.messageSwipeLeft.asObservable()

    override fun iconForAction(action: Int, tint: Int): Bitmap? =
        actionBitmap(messageSwipeActionIcon(action).takeIf { it != 0 }, tint)

    // Messages are never archived/removed by a swipe, so the base onSwiped (always snap back to
    // neutral) is exactly what we want.
}
