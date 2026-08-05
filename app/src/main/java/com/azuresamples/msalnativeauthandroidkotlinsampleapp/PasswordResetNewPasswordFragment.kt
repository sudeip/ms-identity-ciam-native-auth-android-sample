package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentPasswordBinding
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInContinuationParameters
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.ResetPasswordSubmitPasswordError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInContinuationError
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordSubmitPasswordResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.NativeAuthFlowStateV2
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordPasswordRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignInContinuationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PasswordResetNewPasswordFragment : Fragment() {
    private var currentStateV1: ResetPasswordPasswordRequiredState? = null
    private var currentStateV2: NativeAuthFlowStateV2? = null
    private var _binding: FragmentPasswordBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = PasswordResetNewPasswordFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPasswordBinding.inflate(inflater, container, false)
        val view = binding.root

        val bundle = this.arguments
        if (Configuration.useNativeAuthV2) {
            currentStateV2 = bundle?.getParcelable(Constants.STATE) as? NativeAuthFlowStateV2
        } else {
            currentStateV1 = bundle?.getParcelable(Constants.STATE) as? ResetPasswordPasswordRequiredState
        }

        init()

        return view
    }

    private fun init() {
        initializeButtonListener()
    }

    private fun initializeButtonListener() {
        binding.create.setOnClickListener {
            resetPassword()
        }
    }

    private fun resetPassword() {
        CoroutineScope(Dispatchers.Main).launch {
            val password = CharArray(binding.passwordText.length())
            binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0)

            try {
                if (Configuration.useNativeAuthV2) {
                    resetPasswordV2(password)
                } else {
                    resetPasswordV1(password)
                }
            } finally {
                binding.passwordText.text?.clear()
                password.fill('\u0000')
            }
        }
    }

    private suspend fun resetPasswordV1(password: CharArray) {
        val state = currentStateV1 ?: return
        val actionResult: ResetPasswordSubmitPasswordResult = state.submitPassword(password)

        when (actionResult) {
            is ResetPasswordResult.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.password_reset_success_message),
                    Toast.LENGTH_LONG
                ).show()
                signInAfterPasswordReset(
                    nextState = actionResult.nextState
                )
            }
            is ResetPasswordSubmitPasswordError -> {
                handleError(actionResult)
            }
        }
    }

    private suspend fun resetPasswordV2(password: CharArray) {
        val state = currentStateV2 ?: return

        when (val actionResult = state.submitNewPassword(password)) {
            is NativeAuthResultV2.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.password_reset_success_message),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            is NativeAuthErrorV2 -> {
                currentStateV2 = actionResult.nextState ?: currentStateV2
                displayDialog(
                    actionResult.error ?: getString(R.string.unexpected_sdk_error_title),
                    actionResult.errorMessage
                )
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
            }
        }
    }

    private suspend fun signInAfterPasswordReset(nextState: SignInContinuationState) {
        val parameters = NativeAuthSignInContinuationParameters()
        val actionResult = nextState.signIn(parameters)

        when (actionResult) {
            is SignInResult.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            is SignInContinuationError -> {
                displayDialog(getString(R.string.msal_exception_title), actionResult.exception?.message ?: actionResult.errorMessage)
            }
        }
    }

    private fun handleError(error: ResetPasswordSubmitPasswordError) {
        when {
            error.isInvalidPassword() || error.isPasswordResetFailed() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private fun displayDialog(error: String?, message: String?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(error)
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun finish() {
        // Pop back to PasswordResetFragment fragment
        val fragmentManager = requireActivity().supportFragmentManager
        val name: String = PasswordResetFragment::class.java.name
        fragmentManager.popBackStack(name, 0)
    }
}
