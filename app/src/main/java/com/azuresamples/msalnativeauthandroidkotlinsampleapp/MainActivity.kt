package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.ActivityMainBinding
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Hosts two side-by-side demos of getting signed in, both landing on the same HomeFragment
 * dashboard once done (single shared MSAL account, whichever flow populated it):
 * - Home's own top bar ("Sign In / Join Banana Club") -> LoginFragment: native, in-app
 *   email/password (see LoginFragment's class doc).
 * - This bottom nav's "Profile" entry -> straight to the system browser (see openProfile):
 *   classic redirect-based sign-in, same mechanism as More -> Web Fallback. Going through the
 *   browser here (rather than native) is also what lets HomeFragment's step-up MFA demo reuse an
 *   SSO session instead of forcing a fresh login on top of the MFA prompt.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }

    private lateinit var authClient: INativeAuthPublicClientApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val clientId = intent.getStringExtra(AuthClient.EXTRA_CLIENT_ID)
        val authorityUrl = intent.getStringExtra(AuthClient.EXTRA_AUTHORITY_URL)
        AuthClient.initialize(this@MainActivity, clientId, authorityUrl)
        authClient = AuthClient.getAuthClient()

        val home = HomeFragment()
        val more = MoreFragment()

        setCurrentFragment(home, R.string.title_home)

        binding.bottomNavigationView.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.home -> setCurrentFragment(home, R.string.title_home)
                R.id.profile -> openProfile(home)
                R.id.more -> setCurrentFragment(more, R.string.title_more)
            }
            true
        }
    }

    /**
     * "Profile" bottom nav entry, next to Home: signed out -> starts a browser-based interactive
     * sign-in (see signInWithBrowser) instead of Home's native in-app sheet; signed in -> jumps to
     * Home, which already shows the account dashboard (profile summary, tokens, etc.) once signed
     * in - no separate profile screen to build.
     */
    private fun openProfile(home: HomeFragment) {
        CoroutineScope(Dispatchers.Main).launch {
            when (authClient.getCurrentAccount()) {
                is GetAccountResult.AccountFound -> setCurrentFragment(home, R.string.title_home)
                else -> signInWithBrowser(home)
            }
        }
    }

    /**
     * Goes straight to the system browser for sign-in - no in-app email/password fields, unlike
     * LoginFragment - so the browser's hosted UI collects credentials/MFA itself. Same call shape
     * as WebFallbackFragment's browser-required fallback, just entered directly instead of after a
     * failed native attempt.
     */
    private fun signInWithBrowser(home: HomeFragment) {
        authClient.acquireToken(
            AcquireTokenParameters(
                AcquireTokenParameters.Builder()
                    .startAuthorizationFromActivity(this)
                    .withScopes(mutableListOf("openid", "profile", "email"))
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            Toast.makeText(this@MainActivity, getString(R.string.sign_in_successful_message), Toast.LENGTH_SHORT).show()
                            setCurrentFragment(home, R.string.title_home)
                            // Home may already be the fragment on screen (this flow is launched
                            // right from it) - replacing it with itself doesn't re-trigger
                            // onResume(), so ask it to refresh directly too.
                            home.refreshAccountState()
                        }

                        override fun onError(exception: MsalException) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle(getString(R.string.msal_exception_title))
                                .setMessage(exception.message)
                                .create()
                                .show()
                        }

                        override fun onCancel() = Unit
                    })
            )
        )
    }

    private fun setCurrentFragment(fragment: Fragment, title: Int) {
        // Home/LoginFragment draw their own branded header and hide this system action bar
        // themselves (see their onCreateView) - restore it here for every other screen, which
        // still relies on it for a title.
        supportActionBar?.show()
        supportActionBar?.title = getString(title)
        supportFragmentManager.beginTransaction()
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }
}
