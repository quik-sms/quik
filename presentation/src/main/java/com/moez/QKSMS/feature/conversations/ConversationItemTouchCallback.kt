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
package dev.octoshrimpy.quik.feature.conversations

import android.content.Context
import android.graphics.Bitmap
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.SwipeItemTouchCallback
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class ConversationItemTouchCallback @Inject constructor(
    colors: Colors,
    disposables: CompositeDisposable,
    private val prefs: Preferences,
    context: Context
) : SwipeItemTouchCallback(colors, disposables, context) {

    override val noneAction = Preferences.SWIPE_ACTION_NONE

    override fun rightActionObservable(): Observable<Int> = prefs.swipeRight.asObservable()
    override fun leftActionObservable(): Observable<Int> = prefs.swipeLeft.asObservable()

    override fun iconForAction(action: Int, tint: Int): Bitmap? = actionBitmap(when (action) {
        Preferences.SWIPE_ACTION_ARCHIVE -> R.drawable.ic_archive_white_24dp
        Preferences.SWIPE_ACTION_DELETE -> R.drawable.ic_delete_white_24dp
        Preferences.SWIPE_ACTION_BLOCK -> R.drawable.ic_block_white_24dp
        Preferences.SWIPE_ACTION_CALL -> R.drawable.ic_call_white_24dp
        Preferences.SWIPE_ACTION_READ -> R.drawable.ic_check_white_24dp
        Preferences.SWIPE_ACTION_UNREAD -> R.drawable.ic_markunread_black_24dp
        Preferences.SWIPE_ACTION_SPEAK -> R.drawable.ic_speaker_black_24dp
        else -> null
    }, tint)

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        swipes.onNext(Pair(viewHolder.itemId, direction))

        // Archive removes the row, so only animate back to neutral for the other actions
        val action = if (direction == ItemTouchHelper.RIGHT) rightAction else leftAction
        if (action != Preferences.SWIPE_ACTION_ARCHIVE) {
            adapter?.notifyItemChanged(viewHolder.adapterPosition)
        }
    }

}
