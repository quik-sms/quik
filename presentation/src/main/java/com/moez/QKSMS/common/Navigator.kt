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
package dev.octoshrimpy.quik.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import dev.octoshrimpy.quik.compat.TelephonyCompat
import dev.octoshrimpy.quik.extensions.resourceExists
import dev.octoshrimpy.quik.feature.settings.about.AboutActivity
import dev.octoshrimpy.quik.feature.backup.BackupActivity
import dev.octoshrimpy.quik.feature.blocking.BlockingActivity
import dev.octoshrimpy.quik.feature.compose.ComposeActivity
import dev.octoshrimpy.quik.feature.conversationinfo.ConversationInfoActivity
import dev.octoshrimpy.quik.feature.gallery.GalleryActivity
import dev.octoshrimpy.quik.feature.main.MainActivity
import dev.octoshrimpy.quik.feature.messageutils.MessageUtilsActivity
import dev.octoshrimpy.quik.feature.plus.PlusActivity
import dev.octoshrimpy.quik.feature.scheduled.ScheduledActivity
import dev.octoshrimpy.quik.feature.settings.SettingsActivity
import dev.octoshrimpy.quik.model.ScheduledMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Navigator @Inject constructor(
    context: Context
) : QkNavigator(context) {
    /**
     * @param source String to indicate where this QKSMS+ screen was launched from. This should be
     * one of [main_menu, compose_schedule, settings_night, settings_theme]
     */
    fun showQksmsPlusActivity(source: String) {
        startActivity(Intent(context, PlusActivity::class.java))
    }

    fun showMainActivity() {
        startActivity(Intent(context, MainActivity::class.java))
    }

    fun showCompose(body: String? = null, attachments: List<Uri>? = null, mode: String? = null) {
        val intent = Intent(context, ComposeActivity::class.java)
        intent.putExtra(Intent.EXTRA_TEXT, body)
        intent.putExtra("mode", mode)

        attachments
            ?.takeIf { it.isNotEmpty() }
            ?.mapNotNull {
                if (it.resourceExists(context)) it
                else null
            }
            ?.let { intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(it)) }

        startActivity(intent)
    }

    fun showCompose(scheduledMessage: ScheduledMessage) {
        val scheduledThreadId = TelephonyCompat.getOrCreateThreadId(
            context,
            scheduledMessage.recipients
        )

        val intent = Intent(context, ComposeActivity::class.java)
        intent.putExtra(Intent.EXTRA_TEXT, scheduledMessage.body)
        intent.putExtra("threadId", scheduledThreadId)
        intent.putExtra("subscriptionId", scheduledMessage.subId)
        intent.putExtra("sendAsGroup", scheduledMessage.sendAsGroup)
        intent.putExtra("scheduleDateTime", scheduledMessage.date)

        scheduledMessage.recipients
            .takeIf { it.isNotEmpty() }
            ?.let { intent.putStringArrayListExtra("addresses", ArrayList(it)) }

        scheduledMessage.attachments
            .takeIf { it.isNotEmpty() }
            ?.mapNotNull {
                val uri = it.toUri()
                if (uri.resourceExists(context)) uri
                else null
            }
            ?.let { intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(it)) }

        startActivity(intent)
    }

    fun showConversation(threadId: Long, query: String? = null) {
        val intent = Intent(context, ComposeActivity::class.java)
                .putExtra("threadId", threadId)
                .putExtra("query", query)
        startActivity(intent)
    }

    fun showConversationInfo(threadId: Long) {
        val intent = Intent(context, ConversationInfoActivity::class.java)
        intent.putExtra("threadId", threadId)
        startActivity(intent)
    }

    fun showMedia(partId: Long) {
        val intent = Intent(context, GalleryActivity::class.java)
        intent.putExtra("partId", partId)
        startActivity(intent)
    }

    fun showBackup() {
        startActivity(Intent(context, BackupActivity::class.java))
    }

    fun showScheduled(conversationId: Long?) {
        val intent = Intent(context, ScheduledActivity::class.java)
        conversationId?.let { intent.putExtra("conversationId", it) }
        startActivity(intent)
    }

    fun showMessageUtils() {
        startActivity(Intent(context, MessageUtilsActivity::class.java))
    }

    fun showSettings() {
        startActivity(Intent(context, SettingsActivity::class.java))
    }

    fun showAbout() {
        startActivity(Intent(context, AboutActivity::class.java))
    }

    fun showBlockedConversations() {
        startActivity(Intent(context, BlockingActivity::class.java))
    }

    fun viewFile(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType.lowercase())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .let { Intent.createChooser(it, null) }

        startActivityExternal(intent)
    }

    fun shareFile(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND)
                .setType(mimeType.lowercase())
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .let { Intent.createChooser(it, null) }

        startActivityExternal(intent)
    }
}
