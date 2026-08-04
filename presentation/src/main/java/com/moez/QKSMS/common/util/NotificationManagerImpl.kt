```kotlin
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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import androidx.core.content.getSystemService
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.extensions.dpToPx
import dev.octoshrimpy.quik.common.util.extensions.fromRecipient
import dev.octoshrimpy.quik.common.util.extensions.toPerson
import dev.octoshrimpy.quik.extensions.isImage
import dev.octoshrimpy.quik.feature.compose.ComposeActivity
import dev.octoshrimpy.quik.feature.qkreply.QkReplyActivity
import dev.octoshrimpy.quik.manager.PermissionManager
import dev.octoshrimpy.quik.manager.ShortcutManager
import dev.octoshrimpy.quik.mapper.CursorToPartImpl
import dev.octoshrimpy.quik.receiver.BlockThreadReceiver
import dev.octoshrimpy.quik.receiver.DeleteMessagesReceiver
import dev.octoshrimpy.quik.receiver.MessageMarkReceiver
import dev.octoshrimpy.quik.receiver.RemoteMessagingReceiver
import dev.octoshrimpy.quik.receiver.SpeakThreadsReceiver
import dev.octoshrimpy.quik.repository.ContactRepository
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import dev.octoshrimpy.quik.util.GlideApp
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import dev.octoshrimpy.quik.util.Preferences
import dev.octoshrimpy.quik.util.tryOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import dev.octoshrimpy.quik.receiver.ResendMessageReceiver

@Singleton
class NotificationManagerImpl @Inject constructor(
    private val context: Context,
    private val colors: Colors,
    private val conversationRepo: ConversationRepository,
    private val prefs: Preferences,
    private val messageRepo: MessageRepository,
    private val permissions: PermissionManager,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val contactRepo: ContactRepository,
    private val shortcutManager: ShortcutManager
) : dev.octoshrimpy.quik.manager.NotificationManager {

    companion object {
        const val DEFAULT_CHANNEL_ID = "notifications_default"
        const val BACKUP_RESTORE_CHANNEL_ID = "notifications_backup_restore"
        const val RECEIVING_WORKER_CHANNEL_ID = "notifications_receiving_worker"

        val MARK_TYPE_COUNT = MessageMarkReceiver.MarkType.values().size

        val VIBRATE_PATTERN = longArrayOf(0, 200, 0, 200)
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        // Make sure the default channel has been initialized
        createNotificationChannel()
    }

    /**
     * In order to not have conflicting pending intents for the same receiver,
     * let's generate one for each type of marking action.
     */
    fun MessageMarkReceiver.MarkType.getRequestCode(threadId: Long): Int {
        return threadId.toInt() * MARK_TYPE_COUNT + ordinal
    }

    // Required for running workers on Android 12 and older
    override fun getForegroundNotificationForWorkersOnOlderAndroids(): Notification {
        val contentIntent = Intent(context, ComposeActivity::class.java)
        val taskStackBuilder = TaskStackBuilder.create(context)
            .addParentStack(ComposeActivity::class.java)
            .addNextIntent(contentIntent)
        val contentPI = taskStackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_foreground_worker_title))
            .setContentText(context.getString(R.string.notification_foreground_worker_text))
            .setShowWhen(false)
            .setWhen(System.currentTimeMillis())
            .setSmallIcon(R.drawable.ic_notification_worker)
            .setColor(colors.theme().theme)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentPI)
            .build()
    }

    /**
     * Updates the notification for a particular conversation
     */
    override fun update(threadId: Long) {
        // If notifications are disabled, don't do anything
        if (!prefs.notifications(threadId).get()) {
            return
        }

        if (!permissions.hasNotifications()) {
            Timber.w("Cannot update notification because we don't have the notification permission")
            return
        }

        val messages = messageRepo.getUnreadUnseenMessages(threadId)

        // If there are no messages to be displayed, make sure that the notification is dismissed
        if (messages.isEmpty()) {
            notificationManager.cancel(threadId.toInt())
            notificationManager.cancel(threadId.toInt() + 100000)
            return
        }

        val conversation = conversationRepo.getConversation(threadId) ?: return
        val lastRecipient = conversation.lastMessage?.let { lastMessage ->
            conversation.recipients.find { recipient ->
                phoneNumberUtils.compare(recipient.address, lastMessage.address)
            }
        } ?: conversation.recipients.firstOrNull()

        val contentIntent = Intent(context, ComposeActivity::class.java).putExtra("threadId", threadId)
        val taskStackBuilder = TaskStackBuilder.create(context)
                .addParentStack(ComposeActivity::class.java)
                .addNextIntent(contentIntent)
        val contentPI = taskStackBuilder.getPendingIntent(threadId.toInt(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val seenIntent = Intent(context, MessageMarkReceiver::class.java)
            .putExtra("threadId", threadId)
            .putExtra("type", MessageMarkReceiver.MarkType.Seen.ordinal)
        val seenPI = PendingIntent.getBroadcast(
            context,
            MessageMarkReceiver.MarkType.Seen.getRequestCode(threadId),
            seenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // We can't store a null preference, so map it to a null Uri if the pref string is empty
        val ringtone = prefs.ringtone(threadId).get()
                .takeIf { it.isNotEmpty() }
                ?.let(Uri::parse)
                ?.also { uri ->
                    // https://commonsware.com/blog/2016/09/07/notifications-sounds-android-7p0-aggravation.html
                    context.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

        val notification = NotificationCompat.Builder(context, getChannelIdForNotification(threadId))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setColor(colors.theme(lastRecipient).theme)
                .build()

        NotificationManagerCompat.from(context).notify(threadId.toInt(), notification)
    }

    private fun getChannelIdForNotification(threadId: Long): String {
        return DEFAULT_CHANNEL_ID
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DEFAULT_CHANNEL_ID,
                context.getString(R.string.notification_channel_default),
                NotificationManager.IMPORTANCE_MAX
            )
            notificationManager.createNotificationChannel(channel)

            val backupChannel = NotificationChannel(
                BACKUP_RESTORE_CHANNEL_ID,
                context.getString(R.string.notification_channel_backup_restore),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(backupChannel)

            val workerChannel = NotificationChannel(
                RECEIVING_WORKER_CHANNEL_ID,
                context.getString(R.string.notification_channel_receiving_worker),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(workerChannel)
        }
    }
}
```