package dev.octoshrimpy.quik.common.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.core.content.ContextCompat
import dev.octoshrimpy.quik.R
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricLockManager @Inject constructor(
    private val context: Context
) : DefaultLifecycleObserver {

    @Volatile
    var needsAuth: Boolean = false

    private val authenticationInProgress = AtomicBoolean(false)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        needsAuth = true
        authenticationInProgress.set(false)
    }

    fun shouldAuthenticate(lockEnabled: Boolean, requiresBiometricLock: Boolean): Boolean {
        return lockEnabled && requiresBiometricLock && needsAuth && !authenticationInProgress.get()
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onCancel: () -> Unit
    ) {
        authenticationInProgress.set(true)
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticationInProgress.set(false)
                needsAuth = false
                onSuccess()
            }

            override fun onAuthenticationFailed() = Unit

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authenticationInProgress.set(false)
                onCancel()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_prompt_title))
            .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(context.getString(R.string.biometric_prompt_negative))
            .build()

        prompt.authenticate(promptInfo)
    }

    fun isHardwareAvailable(context: Context): Boolean {
        return BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
}
