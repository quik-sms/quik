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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
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
            .withLatestFrom(view.selectionChanges) { itemId, ids ->
                when (itemId) {
                    R.id.restore -> {
                        Schedulers.io().scheduleDirect {
                            val threadIds = Realm.getDefaultInstance().use { realm ->
                                realm.where(Message::class.java)
                                    .anyOf("id", ids.toLongArray())
                                    .findAll()
                                    .map { it.threadId }
                                    .toSet()
                            }
                            messageRepo.restoreJunk(ids)
                            threadIds.forEach { threadId ->
                                conversationRepo.getOrCreateConversation(threadId)
                            }
                            if (threadIds.isNotEmpty()) conversationRepo.updateConversations(threadIds)
                        }
                        view.clearSelection()
                    }
                    R.id.delete -> view.showDeleteDialog(ids)
                }
            }
            .autoDisposable(view.scope())
            .subscribe()

        view.confirmDeleteIntent
            .observeOn(Schedulers.io())
            .doOnNext { ids -> messageRepo.deleteMessages(ids) }
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

}
