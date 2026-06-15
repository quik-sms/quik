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

import android.content.Context
import com.klinker.android.send_message.Utils
import com.squareup.moshi.Moshi
import dev.octoshrimpy.quik.manager.KeyManager
import dev.octoshrimpy.quik.model.EmojiReaction
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.util.EmojiPatternStrings
import io.realm.Realm
import io.realm.Sort
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class EmojiReactionRepositoryImpl @Inject constructor(
    private val context: Context,
    private val keyManager: KeyManager,
    private val moshi: Moshi,
) : EmojiReactionRepository {

    companion object {
        // Invisible (default-ignorable) characters used to hide quik's machine payload.
        // Chosen from U+2061..U+2064 so they don't collide with the U+200A..U+200D chars used
        // by the iOS/Google tapback patterns, and won't appear in normal message text.
        private const val ZW_START = '\u2061'   // function application
        private const val ZW_END = '\u2064'     // invisible plus
        // Alphabet for the payload: 8 distinct BMP default-ignorable code points, so each char
        // carries 3 bits (vs 1 bit for a two-symbol scheme) and the invisible run is ~1/3 as long.
        // All are non-combining, non-bidi, render nothing, survive SMS UCS-2 transport verbatim,
        // and are distinct from the sentinels and the U+200A..U+200D pattern chars. (16 symbols /
        // 4 bits would need code points that risk a visible glyph or U+FFFD substitution on the
        // receiver, so 8 symbols / 3 bits is the densest safe choice.)
        private val ZW_ALPHABET = charArrayOf(
            '\u2060', // word joiner
            '\u2062', // invisible times
            '\u2063', // invisible separator
            '\u206a', // inhibit symmetric swapping (deprecated format)
            '\u206b', // activate symmetric swapping
            '\u206c', // inhibit arabic form shaping
            '\u206d', // activate arabic form shaping
            '\u206e', // national digit shapes
        )
        private const val ZW_BITS_PER_CHAR = 3
        private val ZW_INDEX: Map<Char, Int> =
            ZW_ALPHABET.withIndex().associate { (i, c) -> c to i }

        private const val PAYLOAD_FIELD_SEPARATOR = '\u001f'   // unit separator
        // The replied-to text is shown only in the visible fallback line (not duplicated into the
        // payload); cap it so reactions to long messages stay within a couple of SMS segments.
        private const val MAX_VISIBLE_WORDS = 20
    }

    // We use an ordered map to make sure we can test tapback regexes before generic ones
    private val reactionPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?> = linkedMapOf(
        Regex( // Google Messages
            "(?s)^\u200a[^\u200b\u200a]*\u200b([^\u200b]*)\u200b[^\u200b\u200a]*\u200a(.*)\u200a[^\u200b\u200a]*\u200a\\Z"
        ) to { match ->
            ParsedEmojiReaction(
            match.groupValues[1], match.groupValues[2]
            )
        }
    )
    private val removalPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?> = linkedMapOf(
        Regex( // Google Messages
            "(?s)^\u200a[^\u200c\u200a]*\u200c([^\u200c]*)\u200c[^\u200c\u200a]*\u200a(.*)\u200a[^\u200c\u200a]*\u200a\\Z"
        ) to { match ->
            ParsedEmojiReaction(
                match.groupValues[1], match.groupValues[2], isRemoval = true
            )
        }
    )

    init {
        val assetEntries = loadEmojiPatternEntriesFromAssets()
        assetEntries.forEach { (localeTag, strings) ->
            try {
                addPatternsForLocaleStrings(localeTag, strings, reactionPatterns, removalPatterns)
            } catch (e: Exception) {
                Timber.w(e, "Failed to load asset patterns for locale: $localeTag")
            }
        }
        Timber.i("Loaded emoji reaction patterns for locales: ${assetEntries.map { it.first }}")
    }

    private fun addPatternsForLocaleStrings(
        localeTag: String,
        strings: EmojiPatternStrings,
        reactionPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?>,
        removalPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?>
    ) {
        // iOS tapbacks (important to add these before generic emoji patterns as the regexes may overlap)
        listOf(
            Triple("❤️", strings.iosHeartAdded, strings.iosHeartRemoved),
            Triple("👍", strings.iosLikeAdded, strings.iosLikeRemoved),
            Triple("👎", strings.iosDislikeAdded, strings.iosDislikeRemoved),
            Triple("😂", strings.iosLaughAdded, strings.iosLaughRemoved),
            Triple("‼️", strings.iosExclamationAdded, strings.iosExclamationRemoved),
            Triple("❓", strings.iosQuestionMarkAdded, strings.iosQuestionMarkRemoved)
        ).forEach { (emoji, added, removed) ->
            added?.let {
                reactionPatterns[Regex(it)] =
                    { match -> ParsedEmojiReaction(emoji, match.groupValues[1]) }
            }
            removed?.let {
                removalPatterns[Regex(it)] =
                    { match -> ParsedEmojiReaction(emoji, match.groupValues[1], isRemoval = true) }
            }
        }

        // Generic iOS emoji patterns
        strings.iosGenericAdded?.let { pattern ->
            reactionPatterns[Regex(pattern)] = { match ->
                if (match.groupValues.getOrNull(1) == "with a sticker") null // TODO: localize "with a sticker"
                else ParsedEmojiReaction(match.groupValues[1], match.groupValues[2])
            }
        }
        strings.iosGenericRemoved?.let { pattern ->
            removalPatterns[Regex(pattern)] = { match ->
                ParsedEmojiReaction(match.groupValues[1], match.groupValues[2], isRemoval = true)
            }
        }

        Timber.d("Loaded emoji regex patterns for $localeTag from assets")
    }

    private fun loadEmojiPatternEntriesFromAssets(): List<Pair<String, EmojiPatternStrings>> {
        val dir = "emojis"
        val files = context.assets.list(dir) ?: emptyArray()
        return files.filter { it.endsWith(".json", ignoreCase = true) }
            .mapNotNull { filename ->
                val localeTag = filename.removeSuffix(".json")
                try {
                    val json = context.assets.open("$dir/$filename")
                        .bufferedReader().use {
                            it.readText()
                        }
                    val data = parseEmojiPatternsJson(json)
                    localeTag to data
                } catch (e: Exception) {
                    Timber.w(e, "Failed parsing emoji patterns asset: $filename")
                    null
                }
            }
    }

    private fun parseEmojiPatternsJson(json: String): EmojiPatternStrings {
        val adapter = moshi.adapter(EmojiPatternStrings::class.java)
        return requireNotNull(adapter.fromJson(json)) { "Invalid emoji patterns JSON" }
    }

    override fun parseEmojiReaction(body: String): ParsedEmojiReaction? {
        // quik's own zero-width payload takes priority over the iOS/Google text patterns
        parseQuikReaction(body)?.let {
            Timber.d("Quik reaction found with ${it.emoji}")
            return it
        }

        val removal = parseRemoval(body)
        if (removal != null) return removal

        for ((pattern, parser) in reactionPatterns) {
            val match = pattern.find(body) ?: continue
            val result = parser(match) ?: continue

            Timber.d("Reaction found with ${result.emoji}")
            return result
        }

        return null
    }

    override fun buildReactionBody(emoji: String, targetMessage: Message): String {
        val senderAddress = targetMessage.address
        val timestampMs = targetMessage.date
        val originalText = capWords(targetMessage.getText(false).trim())

        // Visible, iOS-style fallback so non-quik clients see a clean, readable line.
        val human = if (originalText.isEmpty()) "Reacted $emoji to a message"
                    else "Reacted $emoji to \"$originalText\""

        // Hidden machine payload, parsed only by quik. The target is resolved by sender address +
        // timestamp, so the body text is NOT duplicated here (it stays only in the visible line
        // above) to keep the SMS short.
        val payload = listOf(emoji, senderAddress, timestampMs.toString())
            .joinToString(PAYLOAD_FIELD_SEPARATOR.toString())

        return human + encodeQuikPayload(payload)
    }

    /** Cap [text] to [MAX_VISIBLE_WORDS] words, appending an ellipsis when truncated. */
    private fun capWords(text: String): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (words.size <= MAX_VISIBLE_WORDS) text.trim()
               else words.take(MAX_VISIBLE_WORDS).joinToString(" ") + "…"
    }

    /**
     * Encode a UTF-8 string into an invisible run wrapped in sentinels. The byte stream is emitted
     * [ZW_BITS_PER_CHAR] bits at a time, each group mapped to one [ZW_ALPHABET] char.
     */
    private fun encodeQuikPayload(payload: String): String {
        val sb = StringBuilder()
        sb.append(ZW_START)
        var buffer = 0
        var bits = 0
        for (byte in payload.toByteArray(Charsets.UTF_8)) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= ZW_BITS_PER_CHAR) {
                bits -= ZW_BITS_PER_CHAR
                sb.append(ZW_ALPHABET[(buffer shr bits) and 0x7])
            }
        }
        if (bits > 0)   // flush the final partial group, zero-padded on the right
            sb.append(ZW_ALPHABET[(buffer shl (ZW_BITS_PER_CHAR - bits)) and 0x7])
        sb.append(ZW_END)
        return sb.toString()
    }

    /**
     * Extract and decode a quik invisible payload from a message body, or null if absent/invalid.
     */
    private fun decodeQuikPayload(body: String): String? {
        val start = body.indexOf(ZW_START)
        if (start == -1) return null
        val end = body.indexOf(ZW_END, start + 1)
        if (end == -1) return null

        var buffer = 0
        var bits = 0
        val bytes = ByteArrayOutputStream()
        for (i in start + 1 until end) {
            val symbol = ZW_INDEX[body[i]] ?: continue   // ignore any stray non-alphabet chars
            buffer = (buffer shl ZW_BITS_PER_CHAR) or symbol
            bits += ZW_BITS_PER_CHAR
            if (bits >= 8) {
                bits -= 8
                bytes.write((buffer shr bits) and 0xFF)
            }
        }
        // trailing < 8 bits are zero padding from the encoder and are dropped
        return bytes.toByteArray().takeIf { it.isNotEmpty() }?.let { String(it, Charsets.UTF_8) }
    }

    private fun parseQuikReaction(body: String): ParsedEmojiReaction? {
        val decoded = decodeQuikPayload(body) ?: return null
        val parts = decoded.split(PAYLOAD_FIELD_SEPARATOR)
        if (parts.size < 3) return null
        return ParsedEmojiReaction(
            emoji = parts[0],
            // original text now lives only in the visible line; recover it for the text-fallback
            // matcher (the address + timestamp fast path handles the common case)
            originalMessage = extractVisibleQuotedText(body),
            quikSenderAddress = parts[1].ifEmpty { null },
            quikTimestamp = parts[2].toLongOrNull(),
        )
    }

    /** Pull the quoted text out of the visible `Reacted <emoji> to "<text>"` fallback line. */
    private fun extractVisibleQuotedText(body: String): String {
        val visible = body.substringBefore(ZW_START)
        val open = visible.indexOf('"')
        val close = visible.lastIndexOf('"')
        return if (open != -1 && close > open) visible.substring(open + 1, close) else ""
    }

    private fun parseRemoval(body: String): ParsedEmojiReaction? {
        for ((pattern, parser) in removalPatterns) {
            val match = pattern.find(body) ?: continue
            val result = parser(match) ?: continue

            Timber.d("Removal found with ${result.emoji}")
            return result
        }

        return null
    }

    private fun parseTruncatedMessages(originalMessageText: String): Regex {
        val reactionText = originalMessageText.trim()

        val delimiter = "\u2026"
        val index = reactionText.lastIndexOf(delimiter)
        val regexPattern = if (index == -1) {
            Regex.escape(reactionText)
        } else {
            val before = reactionText.take(index)
            Regex.escape(before) + ".*"
        }
        return Regex("^$regexPattern$", RegexOption.DOT_MATCHES_ALL)
    }

    /**
     * Search for messages in the same thread with matching text content
     * We'll search recent messages first
     */
    override fun findTargetMessage(
        threadId: Long,
        originalMessageText: String,
        realm: Realm,
        quikSenderAddress: String?,
        quikTimestamp: Long?,
    ): Message? {
        // Fast path: quik reactions carry the original sender's address and the original
        // message's timestamp, so we can look up the exact target without text matching
        if (quikSenderAddress != null && quikTimestamp != null) {
            // 1. exact timestamp match
            realm.where(Message::class.java)
                .equalTo("threadId", threadId)
                .equalTo("address", quikSenderAddress)
                .equalTo("date", quikTimestamp)
                .findFirst()
                ?.let {
                    Timber.d("Found quik reaction target by exact timestamp: message ID ${it.id}")
                    return it
                }

            // 2. widen to ±2000ms in case the stored date drifted slightly
            realm.where(Message::class.java)
                .equalTo("threadId", threadId)
                .equalTo("address", quikSenderAddress)
                .between("date", quikTimestamp - 2000, quikTimestamp + 2000)
                .sort("date", Sort.DESCENDING)
                .findFirst()
                ?.let {
                    Timber.d("Found quik reaction target by timestamp window: message ID ${it.id}")
                    return it
                }

            Timber.w("No quik reaction target by timestamp, falling back to text matching")
        }

        val startTime = System.currentTimeMillis()
        val messages = realm.where(Message::class.java)
            .equalTo("threadId", threadId)
            .sort("date", Sort.DESCENDING)
            .findAll()
        val endTime = System.currentTimeMillis()
        Timber.d("Found ${messages.size} messages as potential emoji targets in ${endTime - startTime}ms")

        val originalMessageRegex = parseTruncatedMessages(originalMessageText)
        val match = messages.find { message ->
            originalMessageRegex.matches(message.getText(false).trim())
        }
        if (match != null) {
            Timber.d("Found match for reaction target: message ID ${match.id}")
            return match
        }

        Timber.w("No target message found for reaction text: '$originalMessageText'")
        return null
    }

    /**
     * The address to attribute a reaction to. For incoming reactions this is the message sender;
     * for our own outgoing reactions [Message.address] is the recipient, so use our own number
     * instead so the reaction shows as coming from us.
     */
    private fun reactionSenderAddress(reactionMessage: Message): String =
        if (reactionMessage.isMe())
            Utils.getMyPhoneNumber(context).takeUnless { it.isNullOrEmpty() } ?: reactionMessage.address
        else
            reactionMessage.address

    private fun removeEmojiReaction(
        reactionMessage: Message,
        reaction: ParsedEmojiReaction,
        targetMessage: Message?,
        realm: Realm,
    ) {
        if (targetMessage == null) {
            Timber.w("Cannot remove emoji reaction '${reaction.emoji}': no target message found")
            return
        }

        val senderAddress = reactionSenderAddress(reactionMessage)
        val existingReaction = targetMessage.emojiReactions.find { candidate ->
            candidate.senderAddress == senderAddress && candidate.emoji == reaction.emoji
        }

        if (existingReaction != null) {
            existingReaction.deleteFromRealm()
            Timber.d("Removed emoji reaction: ${reaction.emoji} to message ${targetMessage.id}")
        } else {
            Timber.w("No existing emoji reaction found to remove: ${reaction.emoji} to message ${targetMessage.id}")
        }

        reactionMessage.isEmojiReaction = true
        realm.insertOrUpdate(reactionMessage)
    }

    override fun saveEmojiReaction(
        reactionMessage: Message,
        parsedReaction: ParsedEmojiReaction,
        targetMessage: Message?,
        realm: Realm,
    ) {
        if (parsedReaction.isRemoval) {
            removeEmojiReaction(reactionMessage, parsedReaction, targetMessage, realm)
            return
        }

        val reaction = EmojiReaction().apply {
            id = keyManager.newId()
            reactionMessageId = reactionMessage.id
            senderAddress = reactionSenderAddress(reactionMessage)
            emoji = parsedReaction.emoji
            originalMessageText = parsedReaction.originalMessage
            threadId = reactionMessage.threadId
        }
        realm.insertOrUpdate(reaction)

        if (targetMessage != null) {
            reactionMessage.isEmojiReaction = true
            realm.insertOrUpdate(reactionMessage)

            // Overwrite any previous reaction from this sender for this target
            val priorFromSender = targetMessage.emojiReactions.filter { it.senderAddress == reaction.senderAddress }
            priorFromSender.forEach { it.deleteFromRealm() }

            targetMessage.emojiReactions.add(reaction)

            Timber.i("Saved emoji reaction: ${reaction.emoji} to message ${targetMessage.id}")
        } else {
            Timber.w("No target message, cannot save emoji reaction: ${reaction.emoji}")
        }
    }

    override fun deleteAndReparseAllEmojiReactions(realm: Realm, onProgress: (SyncRepository.SyncProgress) -> Unit) {
        val startTime = System.currentTimeMillis()

        realm.delete(EmojiReaction::class.java)
        realm.where(Message::class.java).findAll().map {
            it.isEmojiReaction = false
        }

        val allMessages = realm.where(Message::class.java)
            .beginGroup()
                .beginGroup()
                    .equalTo("type", "sms")
                    .isNotEmpty("body")
                .endGroup()
                .or()
                .beginGroup()
                    .equalTo("type", "mms")
                    .notEqualTo("messageType", 130.toLong())
                    .isNotEmpty("parts.text")
                .endGroup()
            .endGroup()
            .sort("date", Sort.ASCENDING) // parse oldest to newest to handle reactions & removals properly
            .findAll()

        val max = allMessages?.count() ?: 0
        var progress = 0

        allMessages.forEach { message ->
            val text = message.getText(false)
            val parsedReaction = parseEmojiReaction(text)
            if (parsedReaction != null) {
                val targetMessage = findTargetMessage(
                    message.threadId,
                    parsedReaction.originalMessage,
                    realm,
                    parsedReaction.quikSenderAddress,
                    parsedReaction.quikTimestamp,
                )
                saveEmojiReaction(
                    message,
                    parsedReaction,
                    targetMessage,
                    realm,
                )
                progress++
                // Update the progress every 25 messages, and then at completion
                // that way we don't spam the UI
                if (progress % 25 == 0 || progress == max) {
                    onProgress(
                        SyncRepository.SyncProgress.ParsingEmojis(
                            max = max,
                            progress = progress,
                            indeterminate = false
                        )
                    )
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Timber.d("Deleted and reparsed all emoji reactions in ${endTime - startTime}ms")
    }

}
