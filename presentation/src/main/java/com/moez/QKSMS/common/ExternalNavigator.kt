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

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import dev.octoshrimpy.quik.BuildConfig
import dev.octoshrimpy.quik.manager.BillingManager
import dev.octoshrimpy.quik.manager.PermissionManager
import javax.inject.Inject
import androidx.core.net.toUri
import dev.octoshrimpy.quik.feature.notificationprefs.NotificationPrefsActivity
import dev.octoshrimpy.quik.manager.NotificationManager

/**
 * Navigator to open activities outside of the app
 * Sharing and Viewing files remains in [Navigator]
 */
class ExternalNavigator @Inject constructor(
    context: Context,
    private val billingManager: BillingManager,
    private val permissions: PermissionManager,
    private val notificationManager: NotificationManager
) : QkNavigator(context) {
    fun showDeveloper() {
        val intent = Intent(Intent.ACTION_VIEW,
            "https://github.com/quik-sms/quik/graphs/contributors".toUri())
        startActivityExternal(intent)
    }

    fun showSourceCode() {
        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/quik-sms/quik".toUri())
        startActivityExternal(intent)
    }

    fun showChangelog() {
        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/quik-sms/quik/releases".toUri())
        startActivityExternal(intent)
    }

    fun showLicense() {
        val intent = Intent(Intent.ACTION_VIEW,
            "https://github.com/quik-sms/quik/blob/master/LICENSE".toUri())
        startActivityExternal(intent)
    }

    fun makePhoneCall(address: String) {
        val action = if (permissions.hasCalling()) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, "tel:$address".toUri())
        startActivityExternal(intent)
    }

    fun showDonation() {
        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/quik-sms/quik".toUri())
        startActivityExternal(intent)
    }

    fun showRating() {
        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/quik-sms/quik".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY
                    or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                    or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        try {
            startActivityExternal(intent)
        } catch (_: ActivityNotFoundException) {
            val url = "https://github.com/quik-sms/quik"
            startActivityExternal(Intent(Intent.ACTION_VIEW, url.toUri()))
        }
    }

    /**
     * Launch the Play Store and display the Call Blocker listing
     */
    fun installCallBlocker() {
        val url = "https://play.google.com/store/apps/details?id=com.cuiet.blockCalls"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivityExternal(intent)
    }

    /**
     * Launch the Play Store and display the Call Control listing
     */
    fun installCallControl() {
        val url = "https://play.google.com/store/apps/details?id=com.flexaspect.android.everycallcontrol"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivityExternal(intent)
    }

    /**
     * Launch the Play Store and display the Should I Answer? listing
     */
    fun installSia() {
        val url = "https://play.google.com/store/apps/details?id=org.mistergroup.shouldianswer"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivityExternal(intent)
    }

    fun showSupport() {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = "mailto:".toUri()
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("quik@octo.sh"))
        intent.putExtra(Intent.EXTRA_SUBJECT, "QUIK Support")
        intent.putExtra(Intent.EXTRA_TEXT, StringBuilder("\n\n")
            .append("\n\n--- Please write your message above this line ---\n\n")
            .append("Package: ${context.packageName}\n")
            .append("Version: ${BuildConfig.VERSION_NAME}\n")
            .append("Device: ${Build.BRAND} ${Build.MODEL}\n")
            .append("SDK: ${Build.VERSION.SDK_INT}\n")
            .append("Upgraded"
                .takeIf { billingManager.upgradeStatus.blockingFirst() } ?: "")
            .toString())
        startActivityExternal(intent)
    }

    fun showInvite() {
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://github.com/quik-sms/quik/releases/latest")
            .let { Intent.createChooser(it, null) }
            .let(::startActivityExternal)
    }

    fun showExactAlarmsSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData("package:${context.packageName}".toUri())
            startActivity(intent)
        }
    }

    fun showPermissionsInSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", context.packageName, null)

        startActivity(intent)
    }

    fun addContact(address: String) {
        val intent = Intent(Intent.ACTION_INSERT)
            .setType(ContactsContract.Contacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, address)

        startActivityExternal(intent)
    }

    fun showContact(lookupKey: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey))

        startActivityExternal(intent)
    }

    // This must use startActivityForResult
    fun showDefaultSmsDialog(context: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            context.startActivityForResult(intent, 42389)
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            context.startActivity(intent)
        }
    }

    fun showNotificationSettings(threadId: Long = 0) {
        val intent = Intent(context, NotificationPrefsActivity::class.java)
        intent.putExtra("threadId", threadId)
        startActivity(intent)
    }

    fun showNotificationChannel(threadId: Long = 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (threadId != 0L) {
                notificationManager.createNotificationChannel(threadId)
            }

            val channelId = notificationManager.buildNotificationChannelId(threadId)
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            startActivity(intent)
        }
    }
}
