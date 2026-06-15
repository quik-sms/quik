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

import androidx.annotation.DrawableRes
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.util.Preferences

/** Drawable shown for a message swipe [action], or 0 when there is no icon. */
@DrawableRes
fun messageSwipeActionIcon(action: Int): Int = when (action) {
    Preferences.MESSAGE_SWIPE_ACTION_REACT -> R.drawable.ic_favorite_black_24dp
    Preferences.MESSAGE_SWIPE_ACTION_REPLY -> R.drawable.ic_reply_white_24dp
    Preferences.MESSAGE_SWIPE_ACTION_COPY -> R.drawable.ic_content_copy_black_24dp
    Preferences.MESSAGE_SWIPE_ACTION_FORWARD -> R.drawable.ic_forward_black_24dp
    Preferences.MESSAGE_SWIPE_ACTION_DELETE -> R.drawable.ic_delete_white_24dp
    else -> 0
}
