/*
 * Copyright (C) 2025
 *
 * This file is part of QUIK.
 *
 * QUIK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package dev.octoshrimpy.quik.feature.blocking.junk

import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkPresenter
import dev.octoshrimpy.quik.extensions.anyOf
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import timber.log.Timber
import javax.inject.Inject

class JunkPresenter @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository
) : QkPresenter<JunkView, JunkState>(JunkState(
    data = messageRepo.getJunkMessages()
)) {

    override fun bindIntents(view: JunkView) {
        super.bindIntents(view)

        view.menuReadyIntent
            .autoDisposable(view.scope())
            .subscribe { newState { copy() } }

        view.optionsItemIntent
            // selectionChanges emits the adapter's live selection list, so snapshot it before
            // handing it to another thread, or clearSelection() will empty it underneath us
            .withLatestFrom(view.selectionChanges) { itemId, ids -> itemId to ids.toList() }
            .autoDisposable(view.scope())
            .subscribe { (itemId, ids) ->
                when (itemId) {
                    R.id.restore -> {
                        Completable
                            .fromAction {
                                val threadIds = getThreadIds(ids)
                                messageRepo.restoreJunk(ids)
                                threadIds.forEach { threadId ->
                                    conversationRepo.getOrCreateConversation(threadId)
                                }
                                if (threadIds.isNotEmpty()) conversationRepo.updateConversations(threadIds)
                            }
                            .subscribeOn(Schedulers.io())
                            .subscribe({}, { error -> Timber.w(error, "failed to restore junk") })
                        view.clearSelection()
                    }
                    R.id.delete -> view.showDeleteDialog(ids)
                }
            }

        view.confirmDeleteIntent
            .map { ids -> ids.toList() }
            .observeOn(Schedulers.io())
            .doOnNext { ids ->
                try {
                    val threadIds = getThreadIds(ids)
                    messageRepo.deleteMessages(ids)
                    if (threadIds.isNotEmpty()) conversationRepo.updateConversations(threadIds)
                } catch (e: Exception) {
                    Timber.w(e, "failed to delete junk")
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(view.scope())
            .subscribe { view.clearSelection() }

        view.selectionChanges
            .autoDisposable(view.scope())
            .subscribe { selection -> newState { copy(selected = selection.size) } }

        view.backClicked
            .withLatestFrom(state) { _, state ->
                when (state.selected) {
                    0 -> view.goBack()
                    else -> view.clearSelection()
                }
            }
            .autoDisposable(view.scope())
            .subscribe()
    }

    private fun getThreadIds(messageIds: List<Long>): Set<Long> =
        Realm.getDefaultInstance().use { realm ->
            realm.where(Message::class.java)
                .anyOf("id", messageIds.toLongArray())
                .findAll()
                .map { it.threadId }
                .toSet()
        }

}
