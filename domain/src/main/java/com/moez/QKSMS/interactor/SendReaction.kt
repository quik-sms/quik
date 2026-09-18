/*
 * Copyright (C) 2025
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
package dev.octoshrimpy.quik.interactor

import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import io.reactivex.Flowable
import javax.inject.Inject

class SendReaction @Inject constructor(
    private val messageRepo: MessageRepository,
    private val conversationRepo: ConversationRepository,
) : Interactor<SendReaction.Params>() {

    data class Params(
        val subId: Int,
        val targetMessageId: Long,
        val emoji: String,
        val isRemoval: Boolean = false,
    )

    override fun buildObservable(params: Params): Flowable<*> = Flowable.just(params)
        .doOnNext {
            val sent = messageRepo.sendReaction(
                params.subId, params.targetMessageId, params.emoji, params.isRemoval
            )
            conversationRepo.updateConversations(sent.map { it.threadId }.distinct())
        }
}
