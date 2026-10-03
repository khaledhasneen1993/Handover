package com.khaled.handover

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khaled.handover.ui.HandoverScreen
import com.khaled.handover.ui.OnboardingScreen
import com.khaled.handover.ui.HandoverTheme
import com.khaled.handover.ui.HandoverViewModel

class MainActivity: AppCompatActivity() {
    companion object { const val EXTRA_INSPECTION_ID = "handover_inspection_id" }
    private var pendingInspectionId by mutableStateOf<String?>(null)
    private var unlocked by mutableStateOf(true)
    private val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    override fun onCreate(savedInstanceState: Bundle?) {
        val preference = getSharedPreferences("handover_preferences", MODE_PRIVATE)
        val language = preference.getString("language", "en") ?: "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        super.onCreate(savedInstanceState)
        pendingInspectionId = intent?.getStringExtra(EXTRA_INSPECTION_ID)
        unlocked = !preference.getBoolean("lock_enabled", false)
        enableEdgeToEdge()
        setContent {
            val vm: HandoverViewModel = viewModel()
            var hasStarted by rememberSaveable { mutableStateOf(preference.getBoolean("onboarding_seen", false)) }
            LaunchedEffect(pendingInspectionId, unlocked, hasStarted, vm.recoveryReady) {
                val target = pendingInspectionId
                if (target != null && unlocked && hasStarted && vm.recoveryReady) {
                    if (vm.repo.dao.getInspection(target) != null) vm.inspect(target)
                    pendingInspectionId = null
                }
            }
            val theme = preference.getString("theme", "system") ?: "system"
            HandoverTheme(if (theme == "system") null else theme == "dark") {
                if (!unlocked) LockScreen(::authenticate)
                else if (!hasStarted) OnboardingScreen(onStart = {
                    preference.edit().putBoolean("onboarding_seen", true).apply()
                    hasStarted = true
                    vm.go("CREATE")
                })
                else HandoverScreen(vm)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingInspectionId = intent.getStringExtra(EXTRA_INSPECTION_ID)
    }
    override fun onResume() {
        super.onResume()
        if (getSharedPreferences("handover_preferences",MODE_PRIVATE).getBoolean("lock_enabled",false) && !unlocked) authenticate()
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && getSharedPreferences("handover_preferences",MODE_PRIVATE).getBoolean("lock_enabled",false)) unlocked = false
    }
    private fun authenticate() {
        if(BiometricManager.from(this).canAuthenticate(allowed)!=BiometricManager.BIOMETRIC_SUCCESS) return
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object:BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) {super.onAuthenticationSucceeded(result);unlocked=true}
            override fun onAuthenticationFailed() {super.onAuthenticationFailed();unlocked=false}
        }).authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Handover")
            .setSubtitle("Use biometrics or your device screen lock")
            .setAllowedAuthenticators(allowed).build())
    }
}

@Composable private fun LockScreen(onUnlock:()->Unit) {
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        Column(Modifier.padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("Handover is locked",style=MaterialTheme.typography.headlineMedium)
            Text("This app lock restricts access to the interface; it is not separate database encryption.")
            Button(onClick=onUnlock){Text("Unlock")}
        }
    }
}
