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
package dev.octoshrimpy.quik.manager

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KeyManagerImplTest {

    @Test
    fun newId_isSequentialAfterReset() {
        // reset() marks the manager initialised, so newId() never opens a Realm here
        val keys = KeyManagerImpl().apply { reset() }

        assertEquals(listOf(1L, 2L, 3L), List(3) { keys.newId() })
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

}
