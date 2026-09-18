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

data class ParsedEmojiReaction(
    val emoji: String,
    val originalMessage: String,
    val isRemoval: Boolean = false,
    val format: String = "",
)

interface EmojiReactionRepository {
    fun parseEmojiReaction(body: String): ParsedEmojiReaction?

    fun findTargetMessage(threadId: Long, originalMessageText: String, realm: Realm): Message?

    /**
     * Builds the text body of an outgoing reaction message in the given wire [format], so that
     * the recipient's messaging app can render it as a reaction. [format] is one of the
     * `EmojiReaction.FORMAT_*` constants.
     */
    fun buildReactionBody(
        emoji: String,
        targetText: String,
        isRemoval: Boolean,
        format: String,
    ): String

    /**
     * Determines the best wire format to use when sending a reaction to the given thread. Honours
     * the user's send-format preference; when set to auto, mirrors the format last received in the
     * thread, falling back to the Google Messages format.
     */
    fun resolveFormat(threadId: Long, realm: Realm): String

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
