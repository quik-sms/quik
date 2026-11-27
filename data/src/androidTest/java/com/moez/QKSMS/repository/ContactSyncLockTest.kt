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
package dev.octoshrimpy.quik.repository

import android.content.ContentResolver
import android.database.Cursor
import android.database.MatrixCursor
import android.os.SystemClock
import android.preference.PreferenceManager
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.f2prateek.rx.preferences2.RxSharedPreferences
import dev.octoshrimpy.quik.manager.KeyManager
import dev.octoshrimpy.quik.mapper.CursorToContact
import dev.octoshrimpy.quik.mapper.CursorToContactGroup
import dev.octoshrimpy.quik.mapper.CursorToContactGroupMember
import dev.octoshrimpy.quik.mapper.CursorToConversation
import dev.octoshrimpy.quik.mapper.CursorToMessage
import dev.octoshrimpy.quik.mapper.CursorToPart
import dev.octoshrimpy.quik.mapper.CursorToRecipient
import dev.octoshrimpy.quik.model.Contact
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.PhoneNumber
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.util.ContactNumberIndex
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import io.realm.Realm
import io.realm.RealmConfiguration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Provider

/**
 * Contact sync used to match every recipient against every contact number with the slow
 * PhoneNumberUtils.compare, inside a Realm write transaction. With many contacts and many
 * recipients that have no contact, the transaction ran for over ten seconds, and an incoming SMS
 * (whose receiver has to write to Realm and has a ten second budget) timed out: ANR, app killed.
 *
 * The lock test below reproduces that contention and fails on the old code; the other tests make
 * sure the faster matching still links the same contacts.
 */
@RunWith(AndroidJUnit4::class)
class ContactSyncLockTest {

    private val context = InstrumentationRegistry.getTargetContext()
    private val phoneNumberUtils = PhoneNumberUtils(context)

    /** An in-memory Realm is deleted when its last instance closes, so one stays open for the whole test. */
    private lateinit var keepAlive: Realm

    @Before
    fun setUp() {
        Realm.init(context)
        Realm.setDefaultConfiguration(RealmConfiguration.Builder()
                .inMemory()
                .name("contact-sync-${System.nanoTime()}.realm")
                .build())
        keepAlive = Realm.getDefaultInstance()
    }

    @After
    fun tearDown() {
        keepAlive.close()
    }

    @Test
    fun syncContacts_doesNotBlockAnIncomingMessageWrite() {
        // Many recipients that match no contact, but share their last 7 digits with a contact, which
        // is the slow path: Android's loose compare says "maybe", libphonenumber says no
        val contacts = List(4_000) { i -> contact("c$i", i.toLong(), "+1 (646) 555-%04d".format(i)) }
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                it.insert(List(4_000) { i -> Recipient(id = i.toLong(), address = "+1 (212) 555-%04d".format(i)) })
            }
        }
        val syncRepo = syncRepository(contacts)

        // An "SMS receiver": small writes, one after another, timing how long each waits for the lock
        val stop = AtomicBoolean(false)
        var longestWriteMs = 0L
        var writes = 0
        val receiver = Thread {
            Realm.getDefaultInstance().use { realm ->
                var id = 1_000_000L
                while (!stop.get()) {
                    val started = SystemClock.elapsedRealtime()
                    realm.executeTransaction { it.insertOrUpdate(message(id++)) }
                    longestWriteMs = maxOf(longestWriteMs, SystemClock.elapsedRealtime() - started)
                    writes++
                    SystemClock.sleep(20)
                }
            }
        }
        receiver.start()

        val sync = Executors.newSingleThreadExecutor()
        sync.submit { syncRepo.syncContacts() }.get(180, TimeUnit.SECONDS)
        sync.shutdown()
        stop.set(true)
        receiver.join(30_000)

        assertTrue("the receiver never got to write during the sync", writes >= 2)
        assertTrue("an incoming-message write waited ${longestWriteMs}ms for the Realm write lock " +
                "during contact sync; Android kills the SMS receiver after 10000ms", longestWriteMs < 2_000)
    }

    @Test
    fun syncContacts_stillLinksRecipientsToContacts() {
        val contacts = listOf(
                contact("alice", 1, "+1 555-010-0199"),
                contact("bob", 2, "(555) 010-0200", "555-010-0300"),
                contact("short", 4, "12345"),
                contact("carol", 5, "+44 20 7946 0958")
        )
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                it.insert(listOf(
                        Recipient(id = 1, address = "5550100199"),
                        Recipient(id = 2, address = "+15550100300"),
                        Recipient(id = 3, address = "12345"),
                        Recipient(id = 4, address = "+442079460958"),
                        Recipient(id = 5, address = "5550100999"),
                        Recipient(id = 6, address = "1234567")
                ))
            }
        }

        syncRepository(contacts).syncContacts()

        // This thread has no Looper, so its cached Realm instance doesn't see the sync until refreshed
        val linked = Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Recipient::class.java).findAll().associate { it.id to it.contact?.lookupKey }
        }
        assertEquals(mapOf(1L to "alice", 2L to "bob", 3L to "short", 4L to "carol", 5L to null, 6L to null), linked)
    }

    @Test
    fun contactNumberIndex_findsTheSameContactAsALinearSearch() {
        val random = Random(20261118)
        val formats = listOf("+1%s%s", "(%s) %s", "%s-%s", "1%s%s", "+1 (%s) %s")
        fun number(area: Int, local: Int) =
                formats[random.nextInt(formats.size)].format(area, "555%04d".format(local))

        val contacts = List(400) { i -> contact("c$i", i.toLong(), number(200 + random.nextInt(50), i)) }
        val addresses = List(400) { i -> number(200 + random.nextInt(50), i) } +           // some share a number
                List(100) { "%d555%04d".format(300 + random.nextInt(50), random.nextInt(10_000)) } + // some don't
                listOf("5550100199", "+15550100199", "555 0100199", "12345", "1234567", "+11234567", "", "QUIKAPP")

        val index = ContactNumberIndex(phoneNumberUtils, contacts)
        addresses.forEach { address ->
            val linear = contacts.find { contact ->
                contact.numbers.any { phoneNumberUtils.compare(address, it.address) }
            }
            assertEquals(address, linear?.lookupKey, index.find(address)?.lookupKey)
        }
    }

    /** A SyncRepositoryImpl whose contacts provider holds [contacts]; nothing else is used by syncContacts. */
    private fun syncRepository(contacts: List<Contact>): SyncRepositoryImpl {
        val cursorToContact = object : CursorToContact {
            override fun getContactsCursor(): Cursor =
                    MatrixCursor(arrayOf("position")).apply { contacts.indices.forEach { addRow(arrayOf<Any>(it)) } }

            override fun map(from: Cursor): Contact = contacts[from.getInt(0)]
        }
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        @Suppress("UNCHECKED_CAST")
        return SyncRepositoryImpl(
                mock(ContentResolver::class.java),
                mock(ConversationRepository::class.java),
                mock(CursorToConversation::class.java),
                mock(CursorToMessage::class.java),
                mock(CursorToPart::class.java),
                mock(CursorToRecipient::class.java),
                cursorToContact,
                mock(CursorToContactGroup::class.java),
                mock(CursorToContactGroupMember::class.java),
                mock(KeyManager::class.java),
                phoneNumberUtils,
                mock(Provider::class.java) as Provider<MessageRepository>,
                RxSharedPreferences.create(sharedPrefs),
                mock(EmojiReactionRepository::class.java)
        )
    }

    private fun contact(lookupKey: String, firstNumberId: Long, vararg numbers: String) = Contact().apply {
        this.lookupKey = lookupKey
        name = lookupKey
        numbers.forEachIndexed { i, address ->
            this.numbers.add(PhoneNumber(id = firstNumberId * 10 + i, address = address))
        }
    }

    private fun message(id: Long) = Message().apply {
        this.id = id
        threadId = 1
        address = "5550100"
        body = "incoming $id"
        date = id
        type = "sms"
        boxId = 1
    }

}
