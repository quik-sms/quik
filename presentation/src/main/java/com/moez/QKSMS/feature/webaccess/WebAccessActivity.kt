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
package dev.octoshrimpy.quik.feature.webaccess

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Toast
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkThemedActivity
import dev.octoshrimpy.quik.databinding.WebAccessActivityBinding
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

class WebAccessActivity : QkThemedActivity() {

    @Inject lateinit var server: WebAccessServer

    private lateinit var binding: WebAccessActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = WebAccessActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.toggleButton.setOnClickListener {
            if (server.isRunning()) {
                stopServer()
            } else {
                startServer()
            }
        }

        binding.copyButton.setOnClickListener {
            copyToClipboard()
        }

        updateUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            server.stop()
        }
    }

    private fun startServer() {
        if (server.start()) {
            Toast.makeText(this, R.string.web_access_started, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.web_access_failed, Toast.LENGTH_SHORT).show()
        }
        updateUi()
    }

    private fun stopServer() {
        server.stop()
        Toast.makeText(this, R.string.web_access_stopped, Toast.LENGTH_SHORT).show()
        updateUi()
    }

    private fun updateUi() {
        val running = server.isRunning()
        binding.toggleButton.text = getString(
            if (running) R.string.web_access_stop else R.string.web_access_start
        )

        if (running) {
            val ip = getLocalIpAddress()
            val url = "http://$ip:${server.serverPort}"
            binding.urlText.text = url
            binding.tokenText.text = server.accessToken
            binding.instructionsText.text = getString(R.string.web_access_instructions)
            binding.copyButton.isEnabled = true
        } else {
            binding.urlText.text = getString(R.string.web_access_not_running)
            binding.tokenText.text = ""
            binding.instructionsText.text = getString(R.string.web_access_description)
            binding.copyButton.isEnabled = false
        }
    }

    private fun copyToClipboard() {
        val ip = getLocalIpAddress()
        val url = "http://$ip:${server.serverPort}"
        val text = "$url\nToken: ${server.accessToken}"

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Web Access", text))
        Toast.makeText(this, R.string.web_access_copied, Toast.LENGTH_SHORT).show()
    }

    private fun getLocalIpAddress(): String {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            if (wifiInfo != null) {
                val ip = wifiInfo.ipAddress
                if (ip != 0) {
                    return String.format(
                        "%d.%d.%d.%d",
                        ip and 0xff,
                        ip shr 8 and 0xff,
                        ip shr 16 and 0xff,
                        ip shr 24 and 0xff
                    )
                }
            }

            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { intf ->
                intf.inetAddresses?.toList()?.forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            // Fall through
        }
        return "0.0.0.0"
    }
}
