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
package dev.octoshrimpy.quik.feature.settings.swipe

import android.content.Context
import androidx.annotation.DrawableRes
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkPresenter
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class MessageSwipeActionsPresenter @Inject constructor(
    context: Context,
    private val prefs: Preferences
) : QkPresenter<MessageSwipeActionsView, MessageSwipeActionsState>(MessageSwipeActionsState()) {

    init {
        val actionLabels = context.resources.getStringArray(R.array.settings_message_swipe_actions)

        disposables += prefs.messageSwipeRight.asObservable()
                .subscribe { action -> newState { copy(rightLabel = actionLabels[action], rightIcon = iconForAction(action)) } }

        disposables += prefs.messageSwipeLeft.asObservable()
                .subscribe { action -> newState { copy(leftLabel = actionLabels[action], leftIcon = iconForAction(action)) } }
    }

    override fun bindIntents(view: MessageSwipeActionsView) {
        super.bindIntents(view)

        view.actionClicks()
                .map { action ->
                    when (action) {
                        MessageSwipeActionsView.Action.RIGHT -> prefs.messageSwipeRight.get()
                        MessageSwipeActionsView.Action.LEFT -> prefs.messageSwipeLeft.get()
                    }
                }
                .autoDisposable(view.scope())
                .subscribe(view::showSwipeActions)

        view.actionSelected()
                .withLatestFrom(view.actionClicks()) { actionId, action ->
                    when (action) {
                        MessageSwipeActionsView.Action.RIGHT -> prefs.messageSwipeRight.set(actionId)
                        MessageSwipeActionsView.Action.LEFT -> prefs.messageSwipeLeft.set(actionId)
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()
    }

    @DrawableRes
    private fun iconForAction(action: Int) = when (action) {
        Preferences.MESSAGE_SWIPE_ACTION_REACT -> R.drawable.message_emoji
        Preferences.MESSAGE_SWIPE_ACTION_REPLY -> R.drawable.ic_reply_white_24dp
        Preferences.MESSAGE_SWIPE_ACTION_COPY -> R.drawable.ic_content_copy_black_24dp
        Preferences.MESSAGE_SWIPE_ACTION_FORWARD -> R.drawable.ic_forward_black_24dp
        Preferences.MESSAGE_SWIPE_ACTION_DELETE -> R.drawable.ic_delete_white_24dp
        else -> 0
    }

}
