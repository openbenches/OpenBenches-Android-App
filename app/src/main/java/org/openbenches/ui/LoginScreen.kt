package org.openbenches.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import org.openbenches.ui.theme.OpenBenchesGoldButton
import org.openbenches.ui.theme.OpenBenchesHeaderBanner
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import org.openbenches.BuildConfig
import org.openbenches.MainActivity
import org.openbenches.data.AuthJwt
import androidx.browser.customtabs.CustomTabsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PREFERRED_CUSTOM_TABS_PACKAGES = listOf(
    "com.android.chrome",
    "com.chrome.beta",
    "com.chrome.dev",
    "com.chrome.canary",
    "org.mozilla.firefox",
    "com.microsoft.emmx",
    "com.sec.android.app.sbrowser",
)

/** Pre-bind a Custom Tabs provider so Auth0 is more likely to open an in-app tab that auto-closes. */
internal fun warmCustomTabs(appContext: android.content.Context) {
    runCatching {
        val pkg = CustomTabsClient.getPackageName(appContext, PREFERRED_CUSTOM_TABS_PACKAGES)
            ?: return
        CustomTabsClient.connectAndInitialize(appContext, pkg)
    }
}

/**
 * Login screen using Auth0 Universal Login.
 * This opens the same provider list seen at openbenches.eu.auth0.com and returns a token.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    globalBenchCount: Int?,
    globalBenchCountFailed: Boolean,
    authToken: String?,
    authProvider: String?,
    authPictureUrl: String?,
    authUsername: String?,
    modifier: Modifier = Modifier,
    onLoginSuccess: (
        token: String,
        username: String?,
        provider: String?,
        pictureUrl: String?,
        expiresAtMs: Long?
    ) -> Unit = { _, _, _, _, _ -> },
) {
    var isLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val auth0 = remember {
        Auth0(
            BuildConfig.AUTH0_CLIENT_ID,
            BuildConfig.AUTH0_DOMAIN
        )
    }
    // Warm Custom Tabs as soon as the login screen is shown so Auth0's ~1s bind window succeeds.
    // Custom Tabs close after redirect; a full external browser tab cannot be closed by the app.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            warmCustomTabs(context.applicationContext)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 0.dp),
            ) {
                OpenBenchesHeaderBanner(
                    globalBenchCount = globalBenchCount,
                    globalBenchCountFailed = globalBenchCountFailed,
                    centerCountOnly = true,
                    authToken = authToken,
                    authProvider = authProvider,
                    authPictureUrl = authPictureUrl,
                    authUsername = authUsername,
                    onClick = onBack,
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign in to OpenBenches",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use OpenBenches Auth0 login to choose a provider and link your account to this app.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Available Providers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("GitHub, Facebook, LinkedIn, X, WordPress, Discord, OpenStreetMap")
                    Text(
                        text = "Tap below to continue via Universal Login.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            OpenBenchesGoldButton(
                onClick = {
                    if (activity == null) {
                        loginError = "Unable to start login from this context."
                        return@OpenBenchesGoldButton
                    }
                    loginError = null
                    isLoading = true
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            warmCustomTabs(context.applicationContext)
                        }
                        WebAuthProvider.login(auth0)
                            .withScheme(BuildConfig.AUTH0_SCHEME)
                            .withScope("openid profile email")
                            .start(activity, object : Callback<Credentials, AuthenticationException> {
                                override fun onSuccess(result: Credentials) {
                                    isLoading = false
                                    val idToken = result.idToken
                                    val claims = AuthJwt.decodePayloadClaims(idToken)
                                    val username = AuthJwt.displayName(claims, null)
                                    val provider = AuthJwt.verifiedVia(claims)
                                    val pictureUrl = AuthJwt.pictureUrl(claims, null)
                                    if (idToken.isBlank()) {
                                        loginError = "Login succeeded but no token was returned."
                                        return
                                    }
                                    // Ensure the app is in front after redirect (Custom Tab should already be gone).
                                    runCatching {
                                        activity.startActivity(
                                            Intent(activity, MainActivity::class.java).apply {
                                                addFlags(
                                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                                )
                                            }
                                        )
                                    }
                                    onLoginSuccess(
                                        idToken,
                                        username,
                                        provider,
                                        pictureUrl,
                                        result.expiresAt.time
                                    )
                                }

                                override fun onFailure(error: AuthenticationException) {
                                    isLoading = false
                                    loginError = error.getDescription().ifBlank { "Login failed." }
                                }
                            })
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(if (isLoading) "Opening login..." else "Continue with Auth0")
            }

            loginError?.let { errorMessage ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
