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
package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.model.MessageContentFilter
import dev.octoshrimpy.quik.model.MessageContentFilterData
import io.realm.Realm
import io.realm.RealmResults
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageContentFilterRepositoryImpl @Inject constructor() : MessageContentFilterRepository {

    private data class Matcher(val regex: Regex, val lowercaseBody: Boolean)

    private val matcherCache = ConcurrentHashMap<Long, Matcher>()

    override fun createFilter(data: MessageContentFilterData) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            val maxId = realm.where(MessageContentFilter::class.java)
                .max("id")?.toLong() ?: -1

            realm.executeTransaction {
                realm.insert(MessageContentFilter(maxId + 1, data.value, data.caseSensitive, data.isRegex, data.includeContacts))
            }
        }
    }

    override fun getMessageContentFilters(): RealmResults<MessageContentFilter> {
        return Realm.getDefaultInstance()
            .where(MessageContentFilter::class.java)
            .findAllAsync()
    }

    override fun getMessageContentFilter(id: Long): MessageContentFilter? {
        return Realm.getDefaultInstance()
            .where(MessageContentFilter::class.java)
            .equalTo("id", id)
            .findFirst()
    }

    override fun isBlocked(messageBody: String, address: String, contactsRepo: ContactRepository): Boolean {
        val isContact = contactsRepo.isContact(address)

        return Realm.getDefaultInstance().use { realm ->
            realm.where(MessageContentFilter::class.java)
                .findAll()
                .any { filter ->
                    if (isContact && !filter.includeContacts) {
                        false
                    } else {
                        val m = matcherCache.getOrPut(filter.id) { compileMatcher(filter) }
                        val body = if (m.lowercaseBody) messageBody.lowercase() else messageBody
                        m.regex.containsMatchIn(body)
                    }
                }
        }
    }

    private fun compileMatcher(filter: MessageContentFilter): Matcher = when {
        filter.isRegex -> {
            val opts = mutableSetOf(RegexOption.DOT_MATCHES_ALL)
            if (!filter.caseSensitive) opts.add(RegexOption.IGNORE_CASE)
            Matcher(Regex(filter.value, opts), lowercaseBody = false)
        }
        filter.caseSensitive -> {
            val pattern = "\\b" + Regex.escape(filter.value) + "\\b"
            Matcher(Regex(pattern), lowercaseBody = false)
        }
        else -> {
            val pattern = "\\b" + Regex.escape(filter.value.lowercase()) + "\\b"
            Matcher(Regex(pattern), lowercaseBody = true)
        }
    }

    override fun removeFilter(id: Long) {
        matcherCache.remove(id)
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(MessageContentFilter::class.java)
                    .equalTo("id", id)
                    .findAll()
                    .deleteAllFromRealm()
            }
        }
    }

}
