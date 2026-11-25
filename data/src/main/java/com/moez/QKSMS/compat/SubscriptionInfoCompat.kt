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
package dev.octoshrimpy.quik.compat

import android.telephony.SubscriptionInfo

data class SubscriptionInfoCompat(private val subscriptionInfo: SubscriptionInfo) {

    val subscriptionId get() = subscriptionInfo.subscriptionId

    val simSlotIndex get() = subscriptionInfo.simSlotIndex

    /**
     * It is extremely rare, but it is possible, in the case of a misconfigured SIM,
     * to have displayName be null, or blank.
     * If that happens, we can use simSlotIndex to provide a fallback
     */
    val safeDisplayName: CharSequence
        get() = subscriptionInfo.displayName.takeUnless {it.isNullOrBlank()} ?: "SIM ${simSlotIndex + 1}"

    val displayName: CharSequence? get() = subscriptionInfo.displayName
}
