package dev.octoshrimpy.quik.feature.webaccess

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkThemedActivity

class WebAccessActivity : QkThemedActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var url: TextView
    private lateinit var token: TextView
    private lateinit var startStop: Button

    private val refresh = object : Runnable {
        override fun run() {
            updateState()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        title = getString(R.string.web_access_title)
        setContentView(createContentView())
    }

    override fun onResume() {
        super.onResume()
        refresh.run()
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun createContentView(): LinearLayout {
        val padding = (16 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)

            addView(TextView(context).apply {
                text = getString(R.string.web_access_summary)
                textSize = 16f
            }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            status = label()
            url = label()
            token = label()
            addView(status)
            addView(url)
            addView(token)

            startStop = Button(context).apply {
                setOnClickListener {
                    if (WebAccessServer.isRunning) WebAccessServer.stop() else WebAccessServer.start()
                    updateState()
                }
            }
            addView(startStop, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            addView(TextView(context).apply {
                text = getString(R.string.web_access_warning)
                textSize = 14f
            }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun label(): TextView = TextView(this).apply {
        gravity = Gravity.START
        textSize = 15f
        setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun updateState() {
        val running = WebAccessServer.isRunning
        status.text = getString(if (running) R.string.web_access_status_running else R.string.web_access_status_stopped)
        url.text = getString(R.string.web_access_url, if (running) WebAccessServer.localUrl else "-")
        token.text = getString(R.string.web_access_token, if (running) WebAccessServer.token else "-")
        startStop.text = getString(if (running) R.string.web_access_stop else R.string.web_access_start)
    }
}
