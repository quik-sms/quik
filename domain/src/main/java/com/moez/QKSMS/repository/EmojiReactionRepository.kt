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
package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.model.Message
import io.realm.Realm

data class ParsedEmojiReaction(val emoji: String, val originalMessage: String, val isRemoval: Boolean = false)

interface EmojiReactionRepository {
    fun parseEmojiReaction(body: String): ParsedEmojiReaction?

    /**
     * [reactionDate] bounds the search to messages that already existed when the reaction was
     * sent. Omitting it searches the whole thread, which is slow on a long history.
     */
    fun findTargetMessage(
        threadId: Long,
        originalMessageText: String,
        realm: Realm,
        reactionDate: Long? = null,
    ): Message?

    fun saveEmojiReaction(
        reactionMessage: Message,
        parsedReaction: ParsedEmojiReaction,
        targetMessage: Message?,
        realm: Realm,
    )

    fun deleteAndReparseAllEmojiReactions(
        realm: Realm,
        onProgress: (SyncRepository.SyncProgress) -> Unit
    )
}
