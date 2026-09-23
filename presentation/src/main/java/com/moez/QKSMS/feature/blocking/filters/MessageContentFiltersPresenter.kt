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
package dev.octoshrimpy.quik.feature.blocking.filters

import android.content.Context
import android.net.Uri
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.common.base.QkPresenter
import dev.octoshrimpy.quik.model.MessageContentFilterData
import dev.octoshrimpy.quik.repository.MessageContentFilterRepository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject

class MessageContentFiltersPresenter @Inject constructor(
    private val context: Context,
    private val filterRepo: MessageContentFilterRepository,
) : QkPresenter<MessageContentFiltersView, MessageContentFiltersState>(
        MessageContentFiltersState(filters = filterRepo.getMessageContentFilters())
) {

    companion object {
        // Filter lists are realistically kilobytes. Cap the read so a huge or
        // malicious SAF payload cannot force an OutOfMemoryError on the process.
        private const val MAX_FILE_BYTES = 1_048_576
        private const val MAX_PHRASE_LEN = 4096
        private const val MAX_ENTRIES = 10_000
        // ReDoS probe: run each imported regex against a pathological input on
        // a bounded timeout. Catastrophic backtracking that would otherwise
        // freeze the SMS receive worker gets caught at import time instead.
        private const val REDOS_PROBE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!"
        private const val REDOS_TIMEOUT_MS = 200L
    }

    override fun bindIntents(view: MessageContentFiltersView) {
        super.bindIntents(view)

        view.removeFilter()
            .observeOn(Schedulers.io())
            .doOnNext(filterRepo::removeFilter)
            .subscribeOn(Schedulers.io())
            .autoDisposable(view.scope())
            .subscribe()

        view.addFilter()
            .autoDisposable(view.scope())
            .subscribe { view.showAddDialog() }

        view.saveFilter()
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .autoDisposable(view.scope())
            .subscribe { filterData -> filterRepo.createFilter(filterData) }

        view.importClicks()
            .autoDisposable(view.scope())
            .subscribe { view.selectImportFile() }

        view.importFileSelected()
            .observeOn(Schedulers.io())
            .map { uri -> importFromUri(uri) }
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(view.scope())
            .subscribe { result ->
                when (result) {
                    is ImportResult.Success -> {
                        view.showImportResult(result.imported, result.skipped)
                        if (result.regexErrors.isNotEmpty()) view.showRegexErrors(result.regexErrors)
                    }
                    is ImportResult.Failure -> view.showImportError()
                }
            }
    }

    private sealed class ImportResult {
        data class Success(
            val imported: Int,
            val skipped: Int,
            val regexErrors: List<String>
        ) : ImportResult()
        object Failure : ImportResult()
    }

    private fun readBounded(uri: Uri, maxBytes: Int): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) return@use null
                    out.write(buf, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } catch (t: Throwable) {
            null
        }
    }

    private enum class ProbeResult { Ok, Unsafe, Timeout }

    // java.util.regex does not honour thread interrupts, so a runaway
    // backtrack keeps its executor thread pinned even after cancel(true).
    // Reuse one executor for the whole import; on timeout, orphan it and
    // spin up a fresh one for the next probe.
    private fun probeRegex(executor: ExecutorService, value: String): ProbeResult {
        val regex = try {
            Regex(value, setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        } catch (t: Throwable) {
            return ProbeResult.Unsafe
        }
        val future = executor.submit(Callable { regex.containsMatchIn(REDOS_PROBE) })
        return try {
            future.get(REDOS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            ProbeResult.Ok
        } catch (t: TimeoutException) {
            future.cancel(true)
            ProbeResult.Timeout
        } catch (t: Throwable) {
            ProbeResult.Unsafe
        }
    }

    private fun importFromUri(uri: Uri): ImportResult {
        val payload = readBounded(uri, MAX_FILE_BYTES) ?: return ImportResult.Failure

        val array = try {
            JSONArray(payload)
        } catch (t: Throwable) {
            return ImportResult.Failure
        }

        var imported = 0
        var skipped = 0
        val regexErrors = mutableListOf<String>()
        val entries = minOf(array.length(), MAX_ENTRIES)
        var probeExecutor: ExecutorService? = null
        try {
            for (i in 0 until entries) {
                val entry = array.optJSONObject(i)
                if (entry == null) {
                    skipped++
                    continue
                }
                val action = entry.optString("action", "")
                val phrase = entry.optString("phrase", "")
                if (action != "junk" || phrase.isBlank() || phrase.length > MAX_PHRASE_LEN) {
                    skipped++
                    continue
                }
                val useRegex = entry.optBoolean("useRegex", false)
                val caseSensitive = entry.optBoolean("caseSensitive", false) && !useRegex
                val value = if (useRegex) phrase else phrase.trim()
                if (useRegex) {
                    val executor = probeExecutor
                        ?: Executors.newSingleThreadExecutor().also { probeExecutor = it }
                    when (probeRegex(executor, value)) {
                        ProbeResult.Ok -> Unit
                        ProbeResult.Unsafe -> {
                            regexErrors.add(value)
                            skipped++
                            continue
                        }
                        ProbeResult.Timeout -> {
                            executor.shutdownNow()
                            probeExecutor = null
                            regexErrors.add(value)
                            skipped++
                            continue
                        }
                    }
                }
                filterRepo.createFilter(
                    MessageContentFilterData(
                        value = value,
                        caseSensitive = caseSensitive,
                        isRegex = useRegex,
                        includeContacts = false
                    )
                )
                imported++
            }
        } finally {
            probeExecutor?.shutdownNow()
        }
        return ImportResult.Success(imported, skipped, regexErrors)
    }

}
