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
import com.squareup.moshi.Moshi
import dev.octoshrimpy.quik.manager.KeyManager
import dev.octoshrimpy.quik.model.EmojiReaction
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.util.EmojiPatternStrings
import dev.octoshrimpy.quik.util.Preferences
import io.realm.Realm
import io.realm.Sort
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

class EmojiReactionRepositoryImpl @Inject constructor(
    private val context: Context,
    private val keyManager: KeyManager,
    private val moshi: Moshi,
    private val prefs: Preferences,
) : EmojiReactionRepository {
    companion object {
        // Invisible delimiters used by the Google Messages reaction wire format
        private const val HAIR = " "  // hair space, wraps the message parts
        private const val ZWSP = "​"  // zero-width space, wraps the emoji when reacting
        private const val ZWNJ = "‌"  // zero-width non-joiner, wraps the emoji when un-reacting
    }

    // Locale-tagged pattern/template strings loaded from assets, kept for outgoing generation
    private val patternStringsByLocale = mutableMapOf<String, EmojiPatternStrings>()
    // We use an ordered map to make sure we can test tapback regexes before generic ones
    private val reactionPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?> = linkedMapOf(
        Regex( // Google Messages
            "(?s)^\u200a[^\u200b\u200a]*\u200b([^\u200b]*)\u200b[^\u200b\u200a]*\u200a(.*)\u200a[^\u200b\u200a]*\u200a\\Z"
        ) to { match ->
            ParsedEmojiReaction(
            match.groupValues[1], match.groupValues[2], format = EmojiReaction.FORMAT_GOOGLE
            )
        }
    )
    private val removalPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?> = linkedMapOf(
        Regex( // Google Messages
            "(?s)^\u200a[^\u200c\u200a]*\u200c([^\u200c]*)\u200c[^\u200c\u200a]*\u200a(.*)\u200a[^\u200c\u200a]*\u200a\\Z"
        ) to { match ->
            ParsedEmojiReaction(
                match.groupValues[1], match.groupValues[2], isRemoval = true,
                format = EmojiReaction.FORMAT_GOOGLE
            )
        }
    )

    init {
        val assetEntries = loadEmojiPatternEntriesFromAssets()
        assetEntries.forEach { (localeTag, strings) ->
            patternStringsByLocale[localeTag] = strings
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
                    { match -> ParsedEmojiReaction(emoji, match.groupValues[1], format = EmojiReaction.FORMAT_IOS_TAPBACK) }
            }
            removed?.let {
                removalPatterns[Regex(it)] =
                    { match -> ParsedEmojiReaction(emoji, match.groupValues[1], isRemoval = true, format = EmojiReaction.FORMAT_IOS_TAPBACK) }
            }
        }

        // Generic iOS emoji patterns
        strings.iosGenericAdded?.let { pattern ->
            reactionPatterns[Regex(pattern)] = { match ->
                if (match.groupValues.getOrNull(1) == "with a sticker") null // TODO: localize "with a sticker"
                else ParsedEmojiReaction(match.groupValues[1], match.groupValues[2], format = EmojiReaction.FORMAT_IOS_GENERIC)
            }
        }
        strings.iosGenericRemoved?.let { pattern ->
            removalPatterns[Regex(pattern)] = { match ->
                ParsedEmojiReaction(match.groupValues[1], match.groupValues[2], isRemoval = true, format = EmojiReaction.FORMAT_IOS_GENERIC)
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

    private fun parseRemoval(body: String): ParsedEmojiReaction? {
        for ((pattern, parser) in removalPatterns) {
            val match = pattern.find(body) ?: continue
            val result = parser(match) ?: continue

            Timber.d("Removal found with ${result.emoji}")
            return result
        }

        return null
    }

    override fun buildReactionBody(
        emoji: String,
        targetText: String,
        isRemoval: Boolean,
        format: String,
    ): String = when {
        format.startsWith("ios") -> buildIosReactionBody(emoji, targetText, isRemoval)
        else -> buildGoogleReactionBody(emoji, targetText, isRemoval)
    }

    /**
     * Reconstructs the invisible-delimiter encoding that Google Messages (and Quik's own parser)
     * understand. The visible words are cosmetic; the hair-space / zero-width delimiters carry the
     * structure, so this is locale-independent.
     */
    private fun buildGoogleReactionBody(emoji: String, targetText: String, isRemoval: Boolean): String {
        val emojiDelim = if (isRemoval) ZWNJ else ZWSP
        val verb = if (isRemoval) "Removed " else "Reacted "
        val preposition = if (isRemoval) " from " else " to "
        return HAIR + verb + emojiDelim + emoji + emojiDelim + preposition +
                HAIR + targetText + HAIR + HAIR
    }

    private fun buildIosReactionBody(emoji: String, targetText: String, isRemoval: Boolean): String {
        val strings = templatesForCurrentLocale()
        iosTapbackTemplate(emoji, isRemoval, strings)?.let { template ->
            return String.format(template, targetText)
        }
        val generic = when {
            isRemoval -> strings?.iosGenericRemovedTemplate ?: "Removed %1\$s from “%2\$s”"
            else -> strings?.iosGenericAddedTemplate ?: "Reacted %1\$s to “%2\$s”"
        }
        return String.format(generic, emoji, targetText)
    }

    /** Returns the iOS-readable template for one of the six standard tapback emojis, else null. */
    private fun iosTapbackTemplate(
        emoji: String,
        isRemoval: Boolean,
        strings: EmojiPatternStrings?,
    ): String? = when (emoji) {
        "❤️" -> if (isRemoval) strings?.iosHeartRemovedTemplate ?: "Removed a heart from “%s”"
                else strings?.iosHeartAddedTemplate ?: "Loved “%s”"
        "👍" -> if (isRemoval) strings?.iosLikeRemovedTemplate ?: "Removed a like from “%s”"
                else strings?.iosLikeAddedTemplate ?: "Liked “%s”"
        "👎" -> if (isRemoval) strings?.iosDislikeRemovedTemplate ?: "Removed a dislike from “%s”"
                else strings?.iosDislikeAddedTemplate ?: "Disliked “%s”"
        "😂" -> if (isRemoval) strings?.iosLaughRemovedTemplate ?: "Removed a laugh from “%s”"
                else strings?.iosLaughAddedTemplate ?: "Laughed at “%s”"
        "‼️" -> if (isRemoval) strings?.iosExclamationRemovedTemplate ?: "Removed an exclamation from “%s”"
                else strings?.iosExclamationAddedTemplate ?: "Emphasized “%s”"
        "❓" -> if (isRemoval) strings?.iosQuestionMarkRemovedTemplate ?: "Removed a question mark from “%s”"
                else strings?.iosQuestionMarkAddedTemplate ?: "Questioned “%s”"
        else -> null
    }

    private fun templatesForCurrentLocale(): EmojiPatternStrings? {
        val locale = Locale.getDefault()
        return patternStringsByLocale[locale.toLanguageTag().replace('-', '_')]
            ?: patternStringsByLocale[locale.language]
            ?: patternStringsByLocale["en"]
    }

    override fun resolveFormat(threadId: Long, realm: Realm): String =
        when (prefs.reactionSendFormat.get()) {
            Preferences.REACTION_FORMAT_GOOGLE -> EmojiReaction.FORMAT_GOOGLE
            Preferences.REACTION_FORMAT_IOS -> EmojiReaction.FORMAT_IOS_TAPBACK
            else -> {
                // Auto: mirror the most recent reaction format we received in this thread
                val lastReceived = realm.where(EmojiReaction::class.java)
                    .equalTo("threadId", threadId)
                    .equalTo("fromMe", false)
                    .sort("id", Sort.DESCENDING)
                    .findFirst()
                if (lastReceived?.format?.startsWith("ios") == true) EmojiReaction.FORMAT_IOS_TAPBACK
                else EmojiReaction.FORMAT_GOOGLE
            }
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
        realm: Realm
    ): Message? {
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
     * Two reactions are from the same reactor if they're both ours, or both incoming from the same
     * address. Used to enforce one reaction per person per message.
     */
    private fun sameReactor(a: EmojiReaction, b: EmojiReaction): Boolean =
        if (b.fromMe) a.fromMe else (!a.fromMe && a.senderAddress == b.senderAddress)

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

        val fromMe = reactionMessage.isMe()
        val existingReaction = targetMessage.emojiReactions.find { candidate ->
            candidate.emoji == reaction.emoji &&
                    if (fromMe) candidate.fromMe
                    else (!candidate.fromMe && candidate.senderAddress == reactionMessage.address)
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
            senderAddress = reactionMessage.address
            emoji = parsedReaction.emoji
            originalMessageText = parsedReaction.originalMessage
            threadId = reactionMessage.threadId
            fromMe = reactionMessage.isMe()
            format = parsedReaction.format
        }
        realm.insertOrUpdate(reaction)

        if (targetMessage != null) {
            reactionMessage.isEmojiReaction = true
            realm.insertOrUpdate(reactionMessage)

            // Overwrite any previous reaction from this same reactor for this target
            val priorFromSender = targetMessage.emojiReactions.filter { sameReactor(it, reaction) }
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
                    realm
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
