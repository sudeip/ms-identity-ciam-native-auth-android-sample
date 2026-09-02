package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentLoginBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignUpParameters
import com.microsoft.identity.nativeauth.statemachine.errors.SignInError
import com.microsoft.identity.nativeauth.statemachine.errors.SignUpError
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.states.SignUpCodeRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Branded "Sign In / Join Banana Club" front door, mirroring the web app's nav flyout
 * (NavigationBar.jsx) - a dropdown anchored to the nav bar, not a page navigation - plus, for
 * Join Banana Club, the loyalty profile step ProfileCompletion.jsx shows in preAuth mode. Opened
 * as a bottom sheet directly over HomeFragment (see HomeFragment.openLogin) rather than replacing
 * it, so signing in/up happens right where it was triggered instead of navigating to a blank page.
 *
 * Unlike the web app - which redirects to Entra's hosted page to collect email/password after the
 * profile step - native auth collects those in-app, so here the profile step simply leads into the
 * same email/password form used for Sign In. The email/password sign-in and sign-up calls are the
 * same ones EmailPasswordSignInSignUpFragment uses - only the presentation and navigation differ.
 */
class LoginFragment : BottomSheetDialogFragment() {

    private lateinit var authClient: INativeAuthPublicClientApplication
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    private enum class Mode { SIGN_IN, SIGN_UP }
    private var mode = Mode.SIGN_IN

    // Collected in the profile step, before there's an account to attach it to. Saved into
    // ProfileStore once sign-up actually completes, same as the web app stashing its draft in
    // sessionStorage until the verified email comes back (reservationDraftStore.js).
    private var pendingProfile: ProfileStore.Profile? = null

    /** Set by whoever shows this sheet; called right before it dismisses after a completed sign-in. */
    var onAuthenticated: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)

        authClient = AuthClient.getAuthClient()

        setupDobSpinners()
        binding.profilePhoneText.formatPhoneAsTyped()
        binding.profilePhoneText.doAfterTextChanged { binding.profilePhoneLayout.error = null }
        binding.profileFirstNameText.doAfterTextChanged { binding.profileFirstNameLayout.error = null }
        binding.profileLastNameText.doAfterTextChanged { binding.profileLastNameLayout.error = null }
        initializeButtonListeners()

        when (arguments?.getString(Constants.LOGIN_MODE)) {
            "signUp" -> showProfileStep()
            "signIn" -> showCredentialsStep(Mode.SIGN_IN)
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // This sheet's content (especially the profile step) is tall enough that peek height
        // would just hide most of it - expand fully open right away instead of making the user
        // drag it up. BottomSheetDialog.getBehavior() isn't public in this Material version, so
        // look up the sheet view itself and derive the behavior from it instead.
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            BottomSheetBehavior.from(it).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
    }

    private fun setupDobSpinners() {
        val spinners = listOf(binding.profileDobDaySpinner, binding.profileDobMonthSpinner, binding.profileDobYearSpinner)
        val options = listOf(ValidationUtils.DAY_OPTIONS, ValidationUtils.MONTH_OPTIONS, ValidationUtils.YEAR_OPTIONS)

        val clearDobError = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.profileDobErrorText.visibility = View.GONE
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

    private fun initializeButtonListeners() {
        binding.signInModeButton.setOnClickListener { showCredentialsStep(Mode.SIGN_IN) }
        binding.joinModeButton.setOnClickListener { showProfileStep() }

        binding.profileContinueButton.setOnClickListener { continueFromProfileStep() }
        binding.profileCancelButton.setOnClickListener { dismiss() }

        binding.switchModeLink.setOnClickListener {
            if (mode == Mode.SIGN_IN) showProfileStep() else showCredentialsStep(Mode.SIGN_IN)
        }
        binding.submitButton.setOnClickListener {
            when (mode) {
                Mode.SIGN_IN -> signIn()
                Mode.SIGN_UP -> signUp()
            }
        }
    }

    private fun showProfileStep() {
        binding.profileSection.visibility = View.VISIBLE
        binding.formSection.visibility = View.GONE
    }

    private fun continueFromProfileStep() {
        binding.profileFirstNameLayout.error = null
        binding.profileLastNameLayout.error = null
        binding.profileDobErrorText.visibility = View.GONE
        binding.profilePhoneLayout.error = null

        val firstName = binding.profileFirstNameText.text?.toString().orEmpty()
        val lastName = binding.profileLastNameText.text?.toString().orEmpty()
        val phone = binding.profilePhoneText.text?.toString().orEmpty()

        var valid = true

        if (firstName.isBlank()) {
            binding.profileFirstNameLayout.error = getString(R.string.first_name_required_message)
            valid = false
        }
        if (lastName.isBlank()) {
            binding.profileLastNameLayout.error = getString(R.string.last_name_required_message)
            valid = false
        }
        if (!binding.profileTermsCheckbox.isChecked) {
            Toast.makeText(requireContext(), R.string.terms_required_message, Toast.LENGTH_SHORT).show()
            valid = false
        }

        val day = binding.profileDobDaySpinner.selectedOptionOrNull()
        val month = binding.profileDobMonthSpinner.selectedOptionOrNull()
        val year = binding.profileDobYearSpinner.selectedOptionOrNull()
        var dobDisplay = ""
        if (day == null || month == null || year == null) {
            binding.profileDobErrorText.text = getString(R.string.dob_required_message)
            binding.profileDobErrorText.visibility = View.VISIBLE
            valid = false
        } else if (ValidationUtils.calculateAge(day, month, year) < ValidationUtils.MINIMUM_AGE) {
            binding.profileDobErrorText.text = getString(R.string.minimum_age_message, ValidationUtils.MINIMUM_AGE)
            binding.profileDobErrorText.visibility = View.VISIBLE
            valid = false
        } else {
            dobDisplay = dateFormat.format(Calendar.getInstance().apply { set(year, month - 1, day, 0, 0, 0) }.time)
        }

        if (!ValidationUtils.isValidPhone(phone)) {
            binding.profilePhoneLayout.error = getString(R.string.invalid_phone_message)
            valid = false
        }

        if (!valid) return

        pendingProfile = ProfileStore.Profile(
            firstName = firstName,
            lastName = lastName,
            preferredName = binding.profilePreferredNameText.text?.toString().orEmpty(),
            dob = dobDisplay,
            phone = ValidationUtils.normalizePhone(phone)
        )

        showCredentialsStep(Mode.SIGN_UP)
    }

    private fun showCredentialsStep(newMode: Mode) {
        mode = newMode
        binding.profileSection.visibility = View.GONE
        binding.formSection.visibility = View.VISIBLE

        when (mode) {
            Mode.SIGN_IN -> {
                binding.formTitle.text = getString(R.string.login_sign_in_button)
                binding.submitButton.text = getString(R.string.login_sign_in_button)
                binding.switchModeLink.text = getString(R.string.login_join_button)
            }
            Mode.SIGN_UP -> {
                binding.formTitle.text = getString(R.string.login_join_button)
                binding.submitButton.text = getString(R.string.login_join_button)
                binding.switchModeLink.text = getString(R.string.login_sign_in_button)
            }
        }
    }

    private fun signIn() {
        CoroutineScope(Dispatchers.Main).launch {
            val email = binding.emailText.text.toString()
            val password = CharArray(binding.passwordText.length())
            binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0)

            val parameters = NativeAuthSignInParameters(username = email)
            parameters.password = password
            val actionResult: SignInResult = authClient.signIn(parameters)

            binding.passwordText.text?.clear()
            password.fill(' ')

            when (actionResult) {
                is SignInResult.Complete -> {
                    Toast.makeText(requireContext(), getString(R.string.sign_in_successful_message), Toast.LENGTH_SHORT).show()
                    onAuthenticated?.invoke()
                    dismiss()
                }
                is SignInResult.CodeRequired -> {
                    displayDialog(message = getString(R.string.sign_in_switch_to_otp_message))
                }
                is SignInResult.MFARequired,
                is SignInResult.StrongAuthMethodRegistrationRequired -> {
                    // The "More" tab's MFA sample screen covers these branches in full; kept simple here.
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
                is SignInError -> handleSignInError(actionResult)
            }
        }
    }

    private fun signUp() {
        CoroutineScope(Dispatchers.Main).launch {
            val email = binding.emailText.text.toString()
            val password = CharArray(binding.passwordText.length())
            binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0)

            val parameters = NativeAuthSignUpParameters(username = email)
            parameters.password = password
            val actionResult: SignUpResult = authClient.signUp(parameters)

            binding.passwordText.text?.clear()
            password.fill(' ')

            when (actionResult) {
                is SignUpResult.CodeRequired -> navigateToSignUpCode(actionResult.nextState)
                is SignUpError -> handleSignUpError(actionResult)
                else -> displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
            }
        }
    }

    private fun handleSignInError(error: SignInError) {
        when {
            error.isInvalidCredentials() || error.isBrowserRequired() || error.isUserNotFound() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
        }
    }

    private fun handleSignUpError(error: SignUpError) {
        when {
            error.isInvalidUsername() || error.isInvalidPassword() || error.isUserAlreadyExists() ||
                    error.isAuthNotSupported() || error.isBrowserRequired() || error.isInvalidAttributes() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
        }
    }

    private fun displayDialog(error: String? = null, message: String?) {
        AlertDialog.Builder(requireContext())
            .setTitle(error)
            .setMessage(message)
            .create()
            .show()
    }

    private fun navigateToSignUpCode(nextState: SignUpCodeRequiredState) {
        // The OTP step is a full screen of its own (no branded header) - save what the profile
        // step already collected, then hand off to it and close this sheet.
        pendingProfile?.let {
            ProfileStore.save(it)
            pendingProfile = null
        }

        val bundle = Bundle()
        bundle.putParcelable(Constants.STATE, nextState)
        val fragment = SignUpCodeFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()

        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
