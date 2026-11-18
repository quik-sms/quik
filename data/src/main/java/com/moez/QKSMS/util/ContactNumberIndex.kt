/*
 * Copyright (C) 2026
 *
 * This file is part of QUIK.
 *
 * QUIK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QUIK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QUIK.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.util

import dev.octoshrimpy.quik.model.Contact

/**
 * Finds the contact that owns a phone number without comparing it against every number of every
 * contact.
 *
 * [PhoneNumberUtils.compare] is slow, and the sync code used to call it for each recipient against
 * each contact number, inside a Realm write transaction. Recipients without a contact (shortcodes,
 * spam, blocked numbers) paid for a full scan every time. On a phone with a large contact list that
 * held the write lock for over ten seconds, so the SMS receiver, which needs that lock to store an
 * incoming message, timed out and the app was killed (ANR).
 *
 * Two addresses can only compare equal when their normalized forms match, or when the characters
 * Android's loose comparison looks at (digits, letters and `+*#`) agree for at least their last
 * [KEY_LENGTH] positions; libphonenumber's SHORT_NSN_MATCH, which [PhoneNumberUtils.compare] also
 * requires, needs the same. So every number is filed under its trailing key and the full compare
 * runs only against the few numbers in the same bucket. Candidates keep the order the contacts were
 * given in, so [find] returns the same contact a linear search would have.
 */
class ContactNumberIndex(
    private val phoneNumberUtils: PhoneNumberUtils,
    contacts: List<Contact>
) {

    private class Entry(val contact: Contact, val address: String)

    private val buckets = HashMap<String, MutableList<Entry>>()

    init {
        contacts.forEach { contact ->
            contact.numbers.forEach { number ->
                buckets.getOrPut(key(number.address)) { mutableListOf() } += Entry(contact, number.address)
            }
        }
    }

    /** The first contact, in the order given to the constructor, with a number matching [address]. */
    fun find(address: String): Contact? {
        val candidates = buckets[key(address)] ?: return null
        return candidates.firstOrNull { entry -> phoneNumberUtils.compare(address, entry.address) }?.contact
    }

    private fun key(address: String): String = address
            .filter { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it in "+*#" }
            .lowercase()
            .takeLast(KEY_LENGTH)

    companion object {
        /** Android's loose comparison (`PhoneNumberUtils.MIN_MATCH`) needs this many trailing digits to agree. */
        const val KEY_LENGTH = 7
    }

}
