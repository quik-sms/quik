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

import android.preference.PreferenceManager
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.squareup.moshi.Moshi
import dev.octoshrimpy.quik.manager.KeyManagerImpl
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.ScheduledMessage
import dev.octoshrimpy.quik.util.Preferences
import io.realm.Realm
import io.realm.RealmConfiguration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Realm instances on background (non-looper) threads don't auto-refresh, and getDefaultInstance()
 * returns the thread's cached instance. Many repository methods never close the instance they
 * open, so on a shared background thread it stays open, pinned to an old version, and any later
 * read on that thread without refresh() sees old data. Each test pins a thread that way, writes
 * from another thread, then calls a repository method on the pinned thread.
 */
@RunWith(AndroidJUnit4::class)
class StaleBackgroundReadsTest {

    private val context = InstrumentationRegistry.getTargetContext()

    private lateinit var pinnedThread: ExecutorService
    private var pinnedRealm: Realm? = null

    @Before
    fun setUp() {
        Realm.init(context)
        Realm.setDefaultConfiguration(RealmConfiguration.Builder()
                .inMemory()
                .name("stale-reads-${System.nanoTime()}.realm")
                .build())

        // Leave an instance open on this thread, as QUIK's unclosed getDefaultInstance() calls do
        pinnedThread = Executors.newSingleThreadExecutor()
        onPinnedThread { pinnedRealm = Realm.getDefaultInstance() }
    }

    @After
    fun tearDown() {
        onPinnedThread { pinnedRealm?.close() }
        pinnedThread.shutdown()
    }

    /** Shows the mechanism itself, so a failure below isn't an artifact of the test setup. */
    @Test
    fun pinnedThread_withoutRefresh_doesNotSeeNewData() {
        writeOnAnotherThread { realm -> realm.insertOrUpdate(message(1)) }

        val count = onPinnedThread {
            Realm.getDefaultInstance().use { realm -> realm.where(Message::class.java).count() }
        }

        assertEquals(0L, count)
    }

    @Test
    fun backup_includesMessagesWrittenAfterThreadWasPinned() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val prefs = Preferences(context, RxSharedPreferences.create(sharedPrefs), sharedPrefs)
        val backupRepo = BackupRepositoryImpl(context, Moshi.Builder().build(), prefs,
                mock(SyncRepository::class.java))

        writeOnAnotherThread { realm -> realm.insertOrUpdate(listOf(message(1), message(2))) }

        // The backup reports how many messages it found through its progress
        var found = 0
        val progress = backupRepo.getBackupProgress()
                .subscribe { p -> if (p is BackupRepository.Progress.Running) found = p.max }
        onPinnedThread { backupRepo.performBackup() }
        progress.dispose()

        assertEquals(2, found)
    }

    @Test
    fun scheduledMessageIds_includeMessagesScheduledAfterThreadWasPinned() {
        val scheduledRepo = ScheduledMessageRepositoryImpl()

        writeOnAnotherThread { realm -> realm.insertOrUpdate(ScheduledMessage(id = 7, date = 1)) }

        val ids = onPinnedThread { scheduledRepo.getAllScheduledMessageIdsSnapshot() }

        assertEquals(listOf(7L), ids)
    }

    @Test
    fun newId_isUniqueAcrossConcurrentCallers() {
        val keys = KeyManagerImpl().apply { reset() }
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)

        val ids = (1..threads)
                .map { pool.submit(Callable { start.await(); List(5_000) { keys.newId() } }) }
                .also { start.countDown() }
                .flatMap { future -> future.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals("duplicate IDs handed out", ids.size, ids.toSet().size)
    }

    private fun <T> onPinnedThread(block: () -> T): T =
            pinnedThread.submit(Callable { block() }).get(30, TimeUnit.SECONDS)

    private fun writeOnAnotherThread(block: (Realm) -> Unit) {
        Executors.newSingleThreadExecutor().run {
            submit { Realm.getDefaultInstance().use { realm -> realm.executeTransaction { block(realm) } } }
                    .get(30, TimeUnit.SECONDS)
            shutdown()
        }
    }

    private fun message(id: Long) = Message().apply {
        this.id = id
        threadId = 1
        address = "5550100"
        body = "test $id"
        date = id
        type = "sms"
        boxId = 1
    }

}
