package com.example.ui.components

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.AppLockPreferences

@Composable
fun AppLockScreen(
    lockPreferences: AppLockPreferences,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val activity = context as? Activity

    fun showBiometric() {
        if (activity == null) return
        val prompt = BiometricPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
            override fun onAuthenticationFailed() { error = true }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.lock_biometric_title))
            .setSubtitle(context.getString(R.string.lock_biometric_subtitle))
            .setNegativeButtonText(context.getString(R.string.lock_use_pin))
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(lockPreferences.method) {
        if (lockPreferences.method == AppLockPreferences.Method.BIOMETRIC &&
            activity != null &&
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        ) showBiometric()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(if (lockPreferences.method == AppLockPreferences.Method.PIN) R.string.lock_enter_pin else R.string.lock_unlock_to_continue),
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (lockPreferences.method == AppLockPreferences.Method.PIN || lockPreferences.hasPin) {
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 32) { pin = it; error = false } },
                label = { Text(stringResource(R.string.lock_pin_label)) },
                isError = error,
                supportingText = if (error) ({ Text(stringResource(R.string.lock_wrong_pin)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
            )
            Button(
                onClick = { if (lockPreferences.verifyPin(pin)) onUnlocked() else error = true },
                modifier = Modifier.padding(top = 16.dp)
            ) { Text(stringResource(R.string.lock_unlock)) }
        }
        if (lockPreferences.method == AppLockPreferences.Method.BIOMETRIC) {
            Button(onClick = ::showBiometric, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.lock_try_biometric))
            }
        }
    }
}
