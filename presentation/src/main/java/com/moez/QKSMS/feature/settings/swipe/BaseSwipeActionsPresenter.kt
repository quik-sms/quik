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
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import com.f2prateek.rx.preferences2.Preference
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.common.base.QkPresenter
import io.reactivex.rxkotlin.plusAssign

/**
 * Shared presenter for the two "swipe actions" settings screens (conversations and messages). The
 * screens are identical apart from which prefs back the left/right action, the labels array, and
 * how an action maps to an icon, so subclasses supply only those.
 */
abstract class BaseSwipeActionsPresenter(
    context: Context,
    @ArrayRes labelsArrayRes: Int,
    private val rightPref: Preference<Int>,
    private val leftPref: Preference<Int>
) : QkPresenter<SwipeActionsView, SwipeActionsState>(SwipeActionsState()) {

    private val actionLabels = context.resources.getStringArray(labelsArrayRes)

    init {
        disposables += rightPref.asObservable()
                .subscribe { action -> newState { copy(rightLabel = actionLabels[action], rightIcon = iconForAction(action)) } }

        disposables += leftPref.asObservable()
                .subscribe { action -> newState { copy(leftLabel = actionLabels[action], leftIcon = iconForAction(action)) } }
    }

    override fun bindIntents(view: SwipeActionsView) {
        super.bindIntents(view)

        view.actionClicks()
                .map { action ->
                    when (action) {
                        SwipeActionsView.Action.RIGHT -> rightPref.get()
                        SwipeActionsView.Action.LEFT -> leftPref.get()
                    }
                }
                .autoDisposable(view.scope())
                .subscribe(view::showSwipeActions)

        view.actionSelected()
                .withLatestFrom(view.actionClicks()) { actionId, action ->
                    when (action) {
                        SwipeActionsView.Action.RIGHT -> rightPref.set(actionId)
                        SwipeActionsView.Action.LEFT -> leftPref.set(actionId)
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()
    }

    @DrawableRes
    protected abstract fun iconForAction(action: Int): Int

}
