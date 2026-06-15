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
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.util.Preferences
import javax.inject.Inject

class MessageSwipeActionsPresenter @Inject constructor(
    context: Context,
    prefs: Preferences
) : BaseSwipeActionsPresenter(
    context, R.array.settings_message_swipe_actions, prefs.messageSwipeRight, prefs.messageSwipeLeft
) {

    @DrawableRes
    override fun iconForAction(action: Int) = messageSwipeActionIcon(action)

}
