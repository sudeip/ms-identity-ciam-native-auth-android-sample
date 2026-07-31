package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.UserAttributes
import com.microsoft.identity.nativeauth.parameters.NativeAuthResetPasswordParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignUpParameters
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.states.NativeAuthFlowStateV2

/**
 * Facade over the Native Auth V2 SDK surface. Starts the V2 entry points, retains the state
 * handed back so a multi-step flow can be continued, and returns the unified [NativeAuthResultV2].
 */
class AuthManager(private val application: INativeAuthPublicClientApplication) {

    var currentState: NativeAuthFlowStateV2? = null
        private set

    suspend fun signIn(email: String, password: CharArray? = null): NativeAuthResultV2 {
        val parameters = NativeAuthSignInParameters(username = email)
        parameters.password = password
        return track(application.signInV2(parameters))
    }

    suspend fun signUp(email: String, password: CharArray? = null): NativeAuthResultV2 {
        val parameters = NativeAuthSignUpParameters(username = email)
        parameters.password = password
        return track(application.signUpV2(parameters))
    }

    suspend fun resetPassword(email: String): NativeAuthResultV2 {
        val parameters = NativeAuthResetPasswordParameters(username = email)
        return track(application.resetPasswordV2(parameters))
    }

    suspend fun submitCode(code: String): NativeAuthResultV2? =
        currentState?.let { track(it.submitCode(code)) }

    suspend fun submitPassword(password: CharArray): NativeAuthResultV2? =
        currentState?.let { track(it.submitPassword(password)) }

    suspend fun submitNewPassword(password: CharArray): NativeAuthResultV2? =
        currentState?.let { track(it.submitNewPassword(password)) }

    suspend fun submitAttributes(attributes: UserAttributes): NativeAuthResultV2? =
        currentState?.let { track(it.submitAttributes(attributes)) }

    suspend fun selectAuthMethod(method: AuthMethod, verificationContact: String? = null): NativeAuthResultV2? =
        currentState?.let { track(it.selectAuthMethod(method, verificationContact)) }

    suspend fun submitChallenge(challenge: String): NativeAuthResultV2? =
        currentState?.let { track(it.submitChallenge(challenge)) }

    suspend fun resendCode(): NativeAuthResultV2? =
        currentState?.let { track(it.resendCode()) }

    private fun track(result: NativeAuthResultV2): NativeAuthResultV2 {
        currentState = when (result) {
            is NativeAuthResultV2.CodeRequired -> result.nextState
            is NativeAuthResultV2.PasswordRequired -> result.nextState
            is NativeAuthResultV2.NewPasswordRequired -> result.nextState
            is NativeAuthResultV2.AttributesRequired -> result.nextState
            is NativeAuthResultV2.AttributesInvalid -> result.nextState
            is NativeAuthResultV2.MFARequired -> result.nextState
            is NativeAuthResultV2.MFAVerificationRequired -> result.nextState
            is NativeAuthResultV2.StrongAuthRegistrationRequired -> result.nextState
            is NativeAuthResultV2.StrongAuthVerificationRequired -> result.nextState
            else -> null
        }
        return result
    }
}
