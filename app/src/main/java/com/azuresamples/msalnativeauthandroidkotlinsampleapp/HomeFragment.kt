package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentHomeBinding
import com.google.android.material.tabs.TabLayout
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.claims.ClaimsRequest
import com.microsoft.identity.client.claims.RequestedClaimAdditionalInformation
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.parameters.NativeAuthGetAccessTokenParameters
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccessTokenError
import com.microsoft.identity.nativeauth.statemachine.results.GetAccessTokenResult
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Branded Home / dashboard screen, mirroring the web app's PageLayout.jsx + App.jsx:
 * a hero + dummy car-search/reservation widget (always visible) and, once signed in, an
 * account dashboard with profile info, a step-up-MFA demo, and decoded ID/access token claims.
 */
class HomeFragment : Fragment() {

    private lateinit var authClient: INativeAuthPublicClientApplication
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    companion object {
        /**
         * Authentication Context Class Reference (ACRS) ID that a Conditional Access policy in
         * the Microsoft Entra admin center can be scoped to (e.g. requiring MFA). Must match the
         * Authentication Context configured for this app's client ID - same value the companion
         * web app uses (see authConfig.js's stepUpAuthenticationContext), but confirm with your
         * tenant admin that the policy covers this Android app registration too, not just the web one.
         */
        private const val STEP_UP_AUTHENTICATION_CONTEXT = "c1"
        private const val ACRS_CLAIM = "acrs"

        /**
         * Conditional Access "needs interaction" rejections, as raw AADSTS codes: 50076 (an MFA
         * challenge is required) and 50079 (the user hasn't enrolled a second factor yet, so one
         * must be registered). Both mean "redo this as an interactive/browser request", the same
         * as GetAccessTokenError.isBrowserRequired() - but that flag never actually gets set for a
         * step-up getAccessToken() call (see requestStepUp's comment), so it's matched by code here
         * instead, straight out of the error text the SDK surfaces.
         */
        private val STEP_UP_REQUIRED_AADSTS_CODES = listOf("AADSTS50076", "AADSTS50079")
    }

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private var currentAccountState: AccountState? = null

    private var pickupDateMillis: Long? = null
    private var returnDateMillis: Long? = null

    private var idTokenClaims: List<ClaimsUtils.ClaimRow> = emptyList()
    private var accessTokenClaims: List<ClaimsUtils.ClaimRow> = emptyList()
    private var sensitiveDataRevealed = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // This screen draws its own branded header (top_bar, below) - the system action bar
        // would just be a second, redundant banner above it.
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        authClient = AuthClient.getAuthClient()

        binding.searchLocationText.setText(getString(R.string.search_location_default))
        showSearchForm()
        setupDobSpinners()
        binding.driverPhoneText.formatPhoneAsTyped()
        initializeListeners()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        getStateAndUpdateUI()
    }

    /**
     * Called by MainActivity right after a successful browser-based sign-in (see
     * MainActivity.signInWithBrowser) to reflect the new account immediately. That flow is
     * launched right from this screen without ever navigating away from it, so this fragment's
     * view is never destroyed and onResume() never runs again once the browser returns -
     * refreshing has to be requested directly. Safe to call even if this fragment isn't the one
     * currently on screen (its view may not exist yet, or anymore).
     */
    fun refreshAccountState() {
        if (_binding == null) return
        getStateAndUpdateUI()
    }

    private fun initializeListeners() {
        binding.navSignInJoinButton.setOnClickListener { navigateToLogin(null) }
        binding.navSignOutButton.setOnClickListener { signOut() }
        binding.browserSignOutButton.setOnClickListener { browserSignOut() }

        binding.searchPickupDateText.setOnClickListener {
            pickDate(pickupDateMillis) { millis ->
                pickupDateMillis = millis
                binding.searchPickupDateText.setText(dateFormat.format(millis))
            }
        }
        binding.searchReturnDateText.setOnClickListener {
            pickDate(returnDateMillis) { millis ->
                returnDateMillis = millis
                binding.searchReturnDateText.setText(dateFormat.format(millis))
            }
        }
        binding.driverPhoneText.doAfterTextChanged { binding.driverPhoneLayout.error = null }
        binding.driverEmailText.doAfterTextChanged { binding.driverEmailLayout.error = null }
        binding.driverFirstNameText.doAfterTextChanged { binding.driverFirstNameLayout.error = null }
        binding.driverLastNameText.doAfterTextChanged { binding.driverLastNameLayout.error = null }

        binding.searchCarsButton.setOnClickListener { showReservationFlow() }
        binding.continueAsGuestButton.setOnClickListener { confirmReservation() }
        binding.reserveNowButton.setOnClickListener { confirmReservation() }
        binding.yesSignMeUpButton.setOnClickListener { navigateToLogin("signUp") }
        binding.loginButton.setOnClickListener { navigateToLogin("signIn") }
        binding.continueToMyAccountButton.setOnClickListener { showSearchForm() }

        binding.accessSensitiveFeatureButton.setOnClickListener { requestStepUp() }

        binding.tokenTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                bindClaims(if (tab.position == 0) idTokenClaims else accessTokenClaims)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupDobSpinners() {
        val spinners = listOf(binding.driverDobDaySpinner, binding.driverDobMonthSpinner, binding.driverDobYearSpinner)
        val options = listOf(ValidationUtils.DAY_OPTIONS, ValidationUtils.MONTH_OPTIONS, ValidationUtils.YEAR_OPTIONS)

        val clearDobError = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.driverDobErrorText.visibility = View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinners.forEachIndexed { index, spinner ->
            spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options[index]).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinner.onItemSelectedListener = clearDobError
        }
    }

    private fun pickDate(currentMillis: Long?, onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        currentMillis?.let { calendar.timeInMillis = it }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val picked = Calendar.getInstance()
                picked.set(year, month, day, 0, 0, 0)
                onPicked(picked.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun getStateAndUpdateUI() {
        CoroutineScope(Dispatchers.Main).launch {
            when (val accountResult = authClient.getCurrentAccount()) {
                is GetAccountResult.AccountFound -> displaySignedInState(accountResult.resultValue)
                is GetAccountResult.NoAccountFound -> displaySignedOutState()
                else -> displaySignedOutState()
            }
        }
    }

    private fun displaySignedInState(accountState: AccountState) {
        binding.navSignInJoinButton.visibility = View.GONE
        binding.navSignOutButton.visibility = View.VISIBLE
        binding.dashboardSection.visibility = View.VISIBLE

        // A signed-in guest already has verified name/email - lock those fields, same as
        // ReservationSignup.jsx does via its `disabled={isSignedIn}` props.
        binding.driverFirstNameText.isEnabled = false
        binding.driverLastNameText.isEnabled = false
        binding.driverEmailText.isEnabled = false
        binding.signedOutActions.visibility = View.GONE
        binding.reserveNowButton.visibility = View.VISIBLE

        loadAccountDetails(accountState)
    }

    private fun displaySignedOutState() {
        binding.navSignInJoinButton.visibility = View.VISIBLE
        binding.navSignOutButton.visibility = View.GONE
        binding.dashboardSection.visibility = View.GONE

        binding.driverFirstNameText.isEnabled = true
        binding.driverLastNameText.isEnabled = true
        binding.driverEmailText.isEnabled = true
        binding.signedOutActions.visibility = View.VISIBLE
        binding.reserveNowButton.visibility = View.GONE
    }

    private fun loadAccountDetails(accountState: AccountState) {
        currentAccountState = accountState
        CoroutineScope(Dispatchers.Main).launch {
            val idToken = accountState.getIdToken()
            idTokenClaims = ClaimsUtils.decodeJwtClaims(idToken)
            val idClaimsMap = idTokenClaims.associate { it.claim to it.value }
            val savedProfile = ProfileStore.get()

            val firstName = savedProfile?.firstName ?: idClaimsMap["given_name"] ?: ""
            val lastName = savedProfile?.lastName ?: idClaimsMap["family_name"] ?: ""
            val email = idClaimsMap["email"] ?: idClaimsMap["preferred_username"] ?: ""

            binding.driverFirstNameText.setText(firstName)
            binding.driverLastNameText.setText(lastName)
            binding.driverEmailText.setText(email)

            binding.profileSummaryText.text = buildString {
                append(getString(R.string.profile_name_label)).append(' ').append("$firstName $lastName".trim()).append('\n')
                append(getString(R.string.profile_phone_label)).append(' ').append(savedProfile?.phone?.ifBlank { null } ?: "Not provided").append('\n')
                append(getString(R.string.profile_dob_label)).append(' ').append(savedProfile?.dob?.ifBlank { null } ?: "Not provided")
            }

            sensitiveDataRevealed = false
            updateSensitiveDataUi()

            val accessTokenResult = accountState.getAccessToken(NativeAuthGetAccessTokenParameters())
            if (accessTokenResult is GetAccessTokenResult.Complete) {
                accessTokenClaims = ClaimsUtils.decodeJwtClaims(accessTokenResult.resultValue.accessToken)
            }

            binding.tokenTabs.getTabAt(0)?.select()
            bindClaims(idTokenClaims)
        }
    }

    private fun bindClaims(rows: List<ClaimsUtils.ClaimRow>) {
        binding.claimsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.claimsRecyclerView.adapter = ClaimsTableAdapter(rows)
    }

    /**
     * Requests the "acrs" (Authentication Context Class Reference) claim on the access token,
     * same as the web app's stepUpAuthRequest. A Conditional Access policy scoped to that
     * Authentication Context (e.g. requiring MFA) then gets evaluated by Entra:
     * - If it can be satisfied silently (or isn't configured), getAccessToken succeeds directly.
     * - If interaction (e.g. MFA) is required, the SDK can't prompt for it inline - it surfaces
     *   GetAccessTokenError.isBrowserRequired() and the challenge is completed via a browser
     *   popup instead (same acquireToken(...).startAuthorizationFromActivity(...) pattern
     *   WebFallbackFragment uses for its own browser-required fallback).
     *
     * No forceRefresh here - once MFA has been satisfied, the resulting access token (with the
     * acrs claim already on it) stays in the cache and getAccessToken() below will happily return
     * it again for the rest of the session, instead of re-challenging MFA on every tap.
     */
    private fun requestStepUp() {
        val accountState = currentAccountState ?: return

        CoroutineScope(Dispatchers.Main).launch {
            val parameters = NativeAuthGetAccessTokenParameters().apply {
                claimsRequest = buildStepUpClaimsRequest()
            }

            when (val result = accountState.getAccessToken(parameters)) {
                is GetAccessTokenResult.Complete -> applyStepUpResult(result.resultValue.accessToken)
                is GetAccessTokenError -> {
                    if (result.isBrowserRequired() || requiresInteractiveStepUp(result)) {
                        startInteractiveStepUp()
                    } else {
                        displayDialog(getString(R.string.msal_exception_title), result.exception?.message ?: result.errorMessage)
                    }
                }
            }
        }
    }

    /**
     * isBrowserRequired() reads a native-auth-specific errorType field, but this step-up call is a
     * silent token refresh under the hood (AcquireTokenSilent), not a native-auth API call - so a
     * Conditional Access rejection here comes back as a generic ServiceException whose errorType is
     * never populated. isBrowserRequired() alone can therefore never be true for this call; match
     * the well-known "needs interaction" AADSTS codes in the error text instead.
     */
    private fun requiresInteractiveStepUp(result: GetAccessTokenError): Boolean {
        val text = result.exception?.message ?: result.errorMessage ?: return false
        return STEP_UP_REQUIRED_AADSTS_CODES.any { text.contains(it) }
    }

    private fun buildStepUpClaimsRequest(): ClaimsRequest {
        val additionalInformation = RequestedClaimAdditionalInformation()
        additionalInformation.essential = true
        additionalInformation.value = STEP_UP_AUTHENTICATION_CONTEXT

        val claimsRequest = ClaimsRequest()
        claimsRequest.requestClaimInAccessToken(ACRS_CLAIM, additionalInformation)
        return claimsRequest
    }

    /** email/preferred_username off the ID token, used as login_hint so the step-up's browser
     *  call doesn't have to show an account picker to know which account to continue. */
    private fun currentAccountEmail(): String =
        idTokenClaims.firstOrNull { it.claim == "email" }?.value
            ?: idTokenClaims.firstOrNull { it.claim == "preferred_username" }?.value
            ?: ""

    private fun startInteractiveStepUp() {
        authClient.acquireToken(
            AcquireTokenParameters(
                AcquireTokenParameters.Builder()
                    .startAuthorizationFromActivity(requireActivity())
                    // Must match the scopes requestStepUp()'s native call above used (that call
                    // left NativeAuthGetAccessTokenParameters.scopes unset, which the SDK defaults
                    // to openid/offline_access/profile) - a different scope set here would be a
                    // different token request, and Conditional Access wouldn't necessarily embed
                    // the requested acrs claim into it the same way, even once MFA is satisfied.
                    // It'd also cache under a different scope key, so the *next* tap's silent
                    // getAccessToken() call wouldn't find this token and would re-challenge MFA.
                    .withScopes(mutableListOf("openid", "offline_access", "profile"))
                    .withClaims(buildStepUpClaimsRequest())
                    // Prompt.LOGIN would skip Entra's account picker too, but it also forces a full
                    // credential re-entry - defeating the point of step-up, which should feel like
                    // "just the MFA challenge" on top of the session that's already signed in.
                    // login_hint disambiguates the account instead, so there's nothing to pick:
                    // Entra reuses the existing SSO session silently and goes straight to whatever
                    // Conditional Access still needs (the SMS/OTP prompt), with no picker and no
                    // re-typed password.
                    .withLoginHint(currentAccountEmail())
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            applyStepUpResult(authenticationResult.accessToken)
                        }

                        override fun onError(exception: MsalException) {
                            displayDialog(getString(R.string.msal_exception_title), exception.message)
                        }

                        override fun onCancel() = Unit
                    })
            )
        )
    }

    private fun applyStepUpResult(accessToken: String) {
        val claims = ClaimsUtils.decodeJwtClaims(accessToken)
        accessTokenClaims = claims
        if (binding.tokenTabs.selectedTabPosition == 1) bindClaims(accessTokenClaims)

        val acrs = claims.firstOrNull { it.claim == ACRS_CLAIM }?.value
        sensitiveDataRevealed = acrs?.contains(STEP_UP_AUTHENTICATION_CONTEXT) == true
        updateSensitiveDataUi()

        if (!sensitiveDataRevealed) {
            Toast.makeText(requireContext(), R.string.step_up_not_satisfied_message, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateSensitiveDataUi() {
        binding.driversLicenseText.text = getString(R.string.drivers_license_label) + " " +
            if (sensitiveDataRevealed) getString(R.string.drivers_license_demo_value) else getString(R.string.drivers_license_masked)
        binding.accessSensitiveFeatureButton.text =
            if (sensitiveDataRevealed) getString(R.string.sensitive_data_now_visible_button) else getString(R.string.access_sensitive_feature_button)
        binding.accessSensitiveFeatureButton.isEnabled = !sensitiveDataRevealed
    }

    private fun signOut() {
        CoroutineScope(Dispatchers.Main).launch {
            val accountResult = authClient.getCurrentAccount()
            if (accountResult is GetAccountResult.AccountFound) {
                val signOutResult = accountResult.resultValue.signOut()
                if (signOutResult is SignOutResult.Complete) {
                    Toast.makeText(requireContext(), getString(R.string.sign_out_successful_message), Toast.LENGTH_SHORT).show()
                    ProfileStore.clear()
                    showSearchForm()
                    displaySignedOutState()
                }
            }
        }
    }

    /**
     * Native signOut() above only clears MSAL's local token cache - it never touches the
     * browser's own Entra session cookie, which MainActivity's Profile flow (and step-up's
     * login_hint call) establish/reuse via Custom Tabs. That cookie is what lets those flows feel
     * seamless, so it's intentionally left alone by the native sign-out above; this is a separate,
     * explicit action for clearing it - hitting the v2.0 logout endpoint in a Custom Tab, the same
     * way a website's own "log out" link would.
     */
    private fun browserSignOut() {
        val authority = AuthClient.authorityUrl?.trimEnd('/') ?: return
        // logout_hint tells Entra which session to end, so it skips its own "which account do you
        // want to sign out" picker - same idea as login_hint on the sign-in side. Fine to assume a
        // single account here (see currentAccountEmail's caller); with more than one signed in on
        // the device, this would need to loop over them or drop the hint and accept the picker.
        val logoutUrl = "$authority/oauth2/v2.0/logout?logout_hint=${Uri.encode(currentAccountEmail())}"
        CustomTabsIntent.Builder().build().launchUrl(requireContext(), Uri.parse(logoutUrl))
    }

    // --- Booking widget state: search -> driver details -> confirmation ---

    private fun showSearchForm() {
        binding.searchForm.visibility = View.VISIBLE
        binding.reservationFlow.visibility = View.GONE
        binding.confirmationSection.visibility = View.GONE
    }

    private fun showReservationFlow() {
        binding.searchForm.visibility = View.GONE
        binding.reservationFlow.visibility = View.VISIBLE
        binding.confirmationSection.visibility = View.GONE
        updateReservationSummary()
    }

    private fun updateReservationSummary() {
        val location = binding.searchLocationText.text?.toString()?.ifBlank { getString(R.string.search_location_default) }
            ?: getString(R.string.search_location_default)

        val calendar = Calendar.getInstance()
        val fallbackPickup = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 2) }
        val fallbackReturn = (fallbackPickup.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 3) }

        val pickup = pickupDateMillis ?: fallbackPickup.timeInMillis
        val dropoff = returnDateMillis ?: fallbackReturn.timeInMillis
        val days = maxOf(1, ((dropoff - pickup) / (1000 * 60 * 60 * 24)).toInt())
        val dailyRate = 58
        val total = days * dailyRate

        binding.reservationSummaryText.text = buildString {
            append(getString(R.string.reservation_vehicle_name)).append(" (").append(getString(R.string.reservation_vehicle_class)).append(")\n\n")
            append(getString(R.string.reservation_pickup_label)).append(' ').append(location).append(", ").append(dateFormat.format(pickup)).append('\n')
            append(getString(R.string.reservation_return_label)).append(' ').append(location).append(", ").append(dateFormat.format(dropoff)).append('\n')
            append(getString(R.string.reservation_extras_label)).append(' ').append(getString(R.string.reservation_extras)).append("\n\n")
            append(getString(R.string.reservation_total_format, days, dailyRate)).append(" = ")
            append(getString(R.string.reservation_total_estimated_format, total))
        }
    }

    private fun confirmReservation() {
        binding.driverFirstNameLayout.error = null
        binding.driverLastNameLayout.error = null
        binding.driverEmailLayout.error = null
        binding.driverDobErrorText.visibility = View.GONE
        binding.driverPhoneLayout.error = null

        val firstName = binding.driverFirstNameText.text?.toString().orEmpty()
        val lastName = binding.driverLastNameText.text?.toString().orEmpty()
        val email = binding.driverEmailText.text?.toString().orEmpty()
        val phone = binding.driverPhoneText.text?.toString().orEmpty()
        // Name/email come from the verified ID token and are locked (disabled) once signed in -
        // same as ReservationSignup.jsx's disabled={isSignedIn}, so they're not re-validated here.
        val signedIn = !binding.driverEmailText.isEnabled

        var valid = true

        if (!signedIn) {
            if (firstName.isBlank()) {
                binding.driverFirstNameLayout.error = getString(R.string.first_name_required_message)
                valid = false
            }
            if (lastName.isBlank()) {
                binding.driverLastNameLayout.error = getString(R.string.last_name_required_message)
                valid = false
            }
            if (!ValidationUtils.isValidEmail(email)) {
                binding.driverEmailLayout.error = getString(R.string.invalid_email_message)
                valid = false
            }
        }

        val day = binding.driverDobDaySpinner.selectedOptionOrNull()
        val month = binding.driverDobMonthSpinner.selectedOptionOrNull()
        val year = binding.driverDobYearSpinner.selectedOptionOrNull()
        var dobDisplay = ""
        if (day == null || month == null || year == null) {
            binding.driverDobErrorText.text = getString(R.string.dob_required_message)
            binding.driverDobErrorText.visibility = View.VISIBLE
            valid = false
        } else if (ValidationUtils.calculateAge(day, month, year) < ValidationUtils.MINIMUM_AGE) {
            binding.driverDobErrorText.text = getString(R.string.minimum_age_rental_message, ValidationUtils.MINIMUM_AGE)
            binding.driverDobErrorText.visibility = View.VISIBLE
            valid = false
        } else {
            dobDisplay = dateFormat.format(Calendar.getInstance().apply { set(year, month - 1, day, 0, 0, 0) }.time)
        }

        // Phone is optional here (unlike the Join Banana Club profile step) - only validated if provided.
        if (phone.isNotBlank() && !ValidationUtils.isValidPhone(phone)) {
            binding.driverPhoneLayout.error = getString(R.string.invalid_phone_message)
            valid = false
        }

        if (!valid) return

        ProfileStore.save(
            ProfileStore.Profile(
                firstName = firstName,
                lastName = lastName,
                preferredName = ProfileStore.get()?.preferredName.orEmpty(),
                dob = dobDisplay,
                phone = if (phone.isNotBlank()) ValidationUtils.normalizePhone(phone) else ""
            )
        )

        binding.reservationFlow.visibility = View.GONE
        binding.confirmationSection.visibility = View.VISIBLE
        binding.reservationConfirmedThanksText.text = getString(R.string.reservation_confirmed_thanks_format, firstName, email)
        binding.continueToMyAccountButton.visibility = if (binding.navSignOutButton.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        // Refresh the dashboard's profile summary if already signed in.
        CoroutineScope(Dispatchers.Main).launch {
            val accountResult = authClient.getCurrentAccount()
            if (accountResult is GetAccountResult.AccountFound) {
                loadAccountDetails(accountResult.resultValue)
            }
        }
    }

    private fun displayDialog(error: String? = null, message: String?) {
        AlertDialog.Builder(requireContext())
            .setTitle(error)
            .setMessage(message)
            .create()
            .show()
    }

    private fun navigateToLogin(mode: String?) {
        // Opened as a bottom sheet right over this screen (mirrors the web nav's dropdown flyout)
        // instead of navigating to a separate page - see LoginFragment's class doc.
        val fragment = LoginFragment()
        if (mode != null) {
            fragment.arguments = Bundle().apply { putString(Constants.LOGIN_MODE, mode) }
        }
        fragment.onAuthenticated = { getStateAndUpdateUI() }
        fragment.show(childFragmentManager, LoginFragment::class.java.simpleName)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
