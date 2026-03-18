package dev.octoshrimpy.quik.common.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.BiometricLockManager
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

abstract class QkActivity : AppCompatActivity() {
    @Inject lateinit var prefs: Preferences
    @Inject lateinit var biometricLockManager: BiometricLockManager

    protected open val requiresBiometricLock: Boolean = true

    protected val menu: Subject<Menu> = BehaviorSubject.create()

    protected val toolbar: Toolbar? get() = findViewById(R.id.toolbar)
    protected val toolbarTitle: TextView? get() = findViewById(R.id.toolbarTitle)
    private var contentBlocker: View? = null

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onNewIntent(intent)
        disableScreenshots(prefs.disableScreenshots.get())
        maybeAuthenticate()
    }

    override fun onResume() {
        super.onResume()
        disableScreenshots(prefs.disableScreenshots.get())
        maybeAuthenticate()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && prefs.fingerprintLock.get() && requiresBiometricLock) {
            biometricLockManager.needsAuth = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        setSupportActionBar(toolbar)
        title = title
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        setSupportActionBar(toolbar)
        title = title
    }

    override fun setTitle(titleId: Int) {
        title = getString(titleId)
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        toolbarTitle?.text = title
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        if (menu != null) {
            this.menu.onNext(menu)
        }
        return result
    }

    protected open fun showBackButton(show: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(show)
    }

    private fun disableScreenshots(disableScreenshots: Boolean) {
        if (disableScreenshots) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun maybeAuthenticate() {
        if (biometricLockManager.isAuthenticating()) return

        if (!biometricLockManager.shouldAuthenticate(prefs.fingerprintLock.get(), requiresBiometricLock)) {
            hideContentBlocker()
            return
        }

        showContentBlocker()
        biometricLockManager.authenticate(
            activity = this,
            onSuccess = ::hideContentBlocker,
            onCancel = { finish() }
        )
    }

    private fun showContentBlocker() {
        if (contentBlocker != null) return

        contentBlocker = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(getColor(android.R.color.black))
            alpha = 1.0f
            isClickable = true
            isFocusable = true
            setOnClickListener { }
        }

        (window.decorView as ViewGroup).addView(
            contentBlocker,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun hideContentBlocker() {
        contentBlocker?.let { blocker ->
            (blocker.parent as? ViewGroup)?.removeView(blocker)
        }
        contentBlocker = null
    }

}