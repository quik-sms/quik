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
package dev.octoshrimpy.quik.blocking

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.core.os.bundleOf
import dev.octoshrimpy.quik.util.tryOrNull
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.subjects.SingleSubject
import javax.inject.Inject

object Protocol {
    const val action = "sms.screening.provider.PublicSMSScreeningService"

    const val smsScreening = 1
    const val smsScreeningResult = 2

    // request
    const val keyNumber = "number"
    const val keySmsContent = "smsContent"
    const val keySimSlot = "simSlot"

    // response
    const val keyShouldBlock = "shouldBlock"
    const val keyReason = "reason" // Why the message is blocked or allowed
}


const val SpamBlockerPackageName = "spam.blocker"

@SuppressLint("QueryPermissionsNeeded")
private fun PackageManager.queryPublicScreeningProviders(): List<ResolveInfo> {
    val intent = Intent(Protocol.action)
    val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        queryIntentServices(intent, 0)
    }

    return services.filter { it.serviceInfo?.exported == true }
}

fun listAvailableProviders(context: Context): List<ComponentName> {
    return context.packageManager
        .queryPublicScreeningProviders()
        .map {
            ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)
        }
}


class SpamBlockerBlockingClient @Inject constructor(
    private val context: Context
) : BlockingClient {

    override fun isAvailable(): Boolean {
        return listAvailableProviders(context).any {
            it.packageName == SpamBlockerPackageName
        }
    }

    override fun getClientCapability() = BlockingClient.Capability.CANT_BLOCK

    override fun shouldBlock(address: String): Single<BlockingClient.Action> = isBlacklisted(address)

    override fun isBlacklisted(address: String): Single<BlockingClient.Action> {
        return Binder(context, address).isBlocked()
                .map { blocked ->
                    when (blocked) {
                        true -> BlockingClient.Action.Block()
                        false -> BlockingClient.Action.DoNothing
                    }
                }
    }

    override fun block(addresses: List<String>): Completable = Completable.fromCallable { openSettings() }

    override fun unblock(addresses: List<String>): Completable = Completable.fromCallable { openSettings() }

    override fun openSettings() {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(SpamBlockerPackageName)
        intent?.let { context.startActivity(it) }
    }

    private class Binder(
        private val context: Context,
        private val address: String
    ) : ServiceConnection {

        private val subject: SingleSubject<Boolean> = SingleSubject.create()
        private var serviceMessenger: Messenger? = null
        private var isBound: Boolean = false

        fun isBlocked(): Single<Boolean> {
            // If either version of Should I Answer? is installed and SIA is enabled, build the
            // intent to request a rating
            val intent: Intent? = tryOrNull(false) {
                context.packageManager.getApplicationInfo(SpamBlockerPackageName, 0).enabled
                Intent(Protocol.action).setPackage(SpamBlockerPackageName)
            }

            // If the intent isn't null, bind the service and wait for a result. Otherwise, don't block
            if (intent != null) {
                val r = context.bindService(intent, this, Context.BIND_AUTO_CREATE)
                Log.i("quik spamblocker", "bind service: $r")
            } else {
                subject.onSuccess(false)
            }

            return subject
        }

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            serviceMessenger = Messenger(service)
            isBound = true

            val message = Message().apply {
                what = Protocol.smsScreening
                data = bundleOf(Protocol.keyNumber to address)
                replyTo = Messenger(IncomingHandler { response ->
                    Log.i("quik spamblocker", "shouldBlock: ${response.shouldBlock}")
                    subject.onSuccess(response.shouldBlock)

                    // We're done, so unbind the service
                    if (isBound && serviceMessenger != null) {
                        context.unbindService(this@Binder)
                    }
                })
            }

            serviceMessenger?.send(message)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            serviceMessenger = null
            isBound = false
        }
    }

    private class IncomingHandler(private val callback: (response: Response) -> Unit) : Handler() {
        class Response(bundle: Bundle) {
            val shouldBlock = bundle.getBoolean(Protocol.keyShouldBlock)

            // optional
//            val blockReason = bundle.getString(Protocol.keyBlockReason)
        }

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                Protocol.smsScreeningResult -> callback(Response(msg.data))
                else -> super.handleMessage(msg)
            }
        }
    }
}
