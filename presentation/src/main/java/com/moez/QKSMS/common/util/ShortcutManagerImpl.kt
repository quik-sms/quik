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
package dev.octoshrimpy.quik.common.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import dev.octoshrimpy.quik.common.util.extensions.getThemedIcon
import dev.octoshrimpy.quik.common.util.extensions.toPerson
import dev.octoshrimpy.quik.feature.compose.ComposeActivity
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import me.leolin.shortcutbadger.ShortcutBadger
import timber.log.Timber
import javax.inject.Inject

class ShortcutManagerImpl @Inject constructor(
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val colors: Colors
) : dev.octoshrimpy.quik.manager.ShortcutManager {

    override fun updateBadge() {
        val count = messageRepo.getUnreadCount().toInt()
        ShortcutBadger.applyCount(context, count)
    }

    /**
     * Replaces shortcuts with newly created ones of the top conversations. Respects rate limiting.
     */
    override fun updateShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return

        val shortcutManager =
            context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
        if (shortcutManager.isRateLimitingActive) return

        val shortcuts: List<ShortcutInfoCompat> = conversationRepo.getTopConversations()
            .take(
                shortcutManager.maxShortcutCountPerActivity -
                        shortcutManager.manifestShortcuts.size
            )
            .map { conversation -> createShortcutForConversation(conversation) }

        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    /**
     * Get the shortcut for a threadId. Will create it if it doesn't exist.
     */
    override fun getOrCreateShortcut(threadId: Long): ShortcutInfoCompat? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return null

        val conv = conversationRepo.getConversation(threadId) ?: return null
        val sc = createShortcutForConversation(conv)
        pushShortcut(sc, conv)

        return sc
    }

    private fun createShortcutForConversation(conversation: Conversation): ShortcutInfoCompat {
        Timber.v("creating shortcut for conversation ${conversation.id}")

        val icon = when {
            conversation.recipients.size == 1 -> {
                val recipient = conversation.recipients.first()!!
                recipient.getThemedIcon(
                    context,
                    colors.theme(recipient),
                    ShortcutManagerCompat.getIconMaxWidth(context),
                    ShortcutManagerCompat.getIconMaxHeight(context)
                )
            }

            else -> {
                conversation.getThemedIcon(
                    context,
                    ShortcutManagerCompat.getIconMaxWidth(context),
                    ShortcutManagerCompat.getIconMaxHeight(context)
                )
            }
        }

        val persons = conversation.recipients.map { it.toPerson(context, colors) }.toTypedArray()

        val intent = Intent(context, ComposeActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra("threadId", conversation.id)
            .putExtra("fromShortcut", true)

        val sc = ShortcutInfoCompat.Builder(context, "${conversation.id}")
            .setShortLabel(conversation.getTitle())
            .setLongLabel(conversation.getTitle())
            .setIcon(icon)
            .setIntent(intent)
            .setPersons(persons)
            .setLongLived(true)
            .build()

        return sc
    }

    /**
     * Pushes dynamic shortcut, removing old shortcut if space is needed.
     * Includes static shortcuts when calculating space,
     * unlike [ShortcutManagerCompat.pushDynamicShortcut]
     */
    @RequiresApi(25)
    private fun pushShortcut(shortcut: ShortcutInfoCompat, conversation: Conversation) {
        Timber.v("pushing shortcut for conversation ${conversation.id}")

        // pushDynamicShortcut excludes static shortcuts in total count, so we must check ourselves
        val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
        val hasNoShortcut = shortcutManager.dynamicShortcuts.find {
            it.id == conversation.id.toString()
        } == null
        val maxShortcutsReached = shortcutManager.manifestShortcuts.size +
                shortcutManager.dynamicShortcuts.size >= shortcutManager.maxShortcutCountPerActivity

        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && hasNoShortcut && maxShortcutsReached
        ) {
            var rank = -1
            var lowestRankShortcut: String? = null
            for (sc in shortcutManager.dynamicShortcuts) {
                if (sc.rank > rank) {
                    lowestRankShortcut = sc.id
                    rank = sc.rank
                }
            }
            Timber.v("removing shortcut for conversation $lowestRankShortcut to make space")
            shortcutManager.removeDynamicShortcuts(listOf(lowestRankShortcut))
        }

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}