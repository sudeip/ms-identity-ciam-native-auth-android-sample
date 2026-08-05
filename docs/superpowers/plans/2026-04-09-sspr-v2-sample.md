# Native Auth V2 SSPR Sample Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the sample app's end-to-end V2 password-reset flow while preserving its existing V1/V2 configuration switch.

**Architecture:** Keep the current three-fragment UI and `Configuration.useNativeAuthV2` branch. Pass each V2 continuation as a parcelled `NativeAuthFlowStateV2`; recoverable `NativeAuthErrorV2` results update that state for retry, and `Complete` returns to the reset screen with an account already cached.

**Tech Stack:** Kotlin, Android fragments, Kotlin coroutines, MSAL Native Auth V1 and V2 APIs.

## Global Constraints

- Build on `origin/spetrescu/native-auth-v2-test`.
- Keep `Configuration.useNativeAuthV2` and the existing V1 behavior unchanged.
- Modify only the three password-reset fragments and directly required imports/types.
- Do not add tests, telemetry, new screens, or unrelated refactoring in this prototype.
- Do not include the user's modified `auto-config.json` in commits.

---

### Task 1: Start V2 SSPR and pass its continuation

**Files:**
- Modify: `app/src/main/java/com/azuresamples/msalnativeauthandroidkotlinsampleapp/PasswordResetFragment.kt`

**Interfaces:**
- Consumes: `AuthManager.resetPassword(email: String): NativeAuthResultV2`
- Produces: `navigateToResetPasswordCodeFragment(nextState: NativeAuthFlowStateV2)`

- [ ] **Step 1: Add the V2 state import and make navigation accept either API's parcelable state**

Import `NativeAuthFlowStateV2`. Keep the existing V1 overload and add a V2 overload, both delegating to one parcelable helper:

```kotlin
private fun navigateToResetPasswordCodeFragment(nextState: ResetPasswordCodeRequiredState) {
    navigateToResetPasswordCodeFragment(state = nextState)
}

private fun navigateToResetPasswordCodeFragment(nextState: NativeAuthFlowStateV2) {
    navigateToResetPasswordCodeFragment(state = nextState)
}

private fun navigateToResetPasswordCodeFragment(state: Parcelable) {
    val bundle = Bundle()
    bundle.putParcelable(Constants.STATE, state)
    val fragment = PasswordResetCodeFragment()
    fragment.arguments = bundle

    requireActivity().supportFragmentManager
        .beginTransaction()
        .setReorderingAllowed(true)
        .addToBackStack(fragment::class.java.name)
        .replace(R.id.scenario_fragment, fragment)
        .commit()
}
```

- [ ] **Step 2: Route the V2 start result**

Update `handleResultV2` so `CodeRequired` navigates with its continuation, while `Complete`, errors, and unexpected results keep their current handling:

```kotlin
is NativeAuthResultV2.CodeRequired -> {
    navigateToResetPasswordCodeFragment(result.nextState)
}
```

- [ ] **Step 3: Check the fragment diff**

Run:

```powershell
git diff --check -- app/src/main/java/com/azuresamples/msalnativeauthandroidkotlinsampleapp/PasswordResetFragment.kt
```

Expected: no output and exit code 0.

---

### Task 2: Continue V1 or V2 through code verification

**Files:**
- Modify: `app/src/main/java/com/azuresamples/msalnativeauthandroidkotlinsampleapp/PasswordResetCodeFragment.kt`

**Interfaces:**
- Consumes: a bundle state that is either `ResetPasswordCodeRequiredState` or `NativeAuthFlowStateV2`
- Produces: `NativeAuthFlowStateV2.submitCode(code)`, `NativeAuthFlowStateV2.resendCode()`, and navigation to the new-password fragment

- [ ] **Step 1: Store the correct state without changing the switch**

Replace the single V1 state property with nullable V1/V2 properties. Select the type using the existing switch:

```kotlin
private var currentStateV1: ResetPasswordCodeRequiredState? = null
private var currentStateV2: NativeAuthFlowStateV2? = null

val state = arguments?.getParcelable<Parcelable>(Constants.STATE)
if (Configuration.useNativeAuthV2) {
    currentStateV2 = state as NativeAuthFlowStateV2
} else {
    currentStateV1 = state as ResetPasswordCodeRequiredState
}
```

- [ ] **Step 2: Preserve the existing V1 submit-code path and add V2 handling**

Branch in `submitCode()` using `Configuration.useNativeAuthV2`. For V2 call `currentStateV2!!.submitCode(code)` and handle:

```kotlin
when (val result = currentStateV2!!.submitCode(code)) {
    is NativeAuthResultV2.NewPasswordRequired -> {
        navigateToResetPasswordPasswordFragment(result.nextState)
    }
    is NativeAuthErrorV2 -> {
        currentStateV2 = result.nextState ?: currentStateV2
        displayDialog(
            result.error ?: getString(R.string.unexpected_sdk_error_title),
            result.errorMessage
        )
    }
    else -> displayDialog(
        getString(R.string.unexpected_sdk_result_title),
        result.toString()
    )
}
```

- [ ] **Step 3: Preserve the existing V1 resend path and add V2 handling**

For V2 call `currentStateV2!!.resendCode()`. On `CodeRequired`, replace `currentStateV2` with `result.nextState` and show the existing resend toast. On `NativeAuthErrorV2`, retain `result.nextState` when present and show its error dialog.

- [ ] **Step 4: Support both navigation state types**

Keep the V1 overload and add a V2 overload, delegating both through one `Parcelable` helper as in Task 1.

- [ ] **Step 5: Check the fragment diff**

Run:

```powershell
git diff --check -- app/src/main/java/com/azuresamples/msalnativeauthandroidkotlinsampleapp/PasswordResetCodeFragment.kt
```

Expected: no output and exit code 0.

---

### Task 3: Submit the new password and finish signed in

**Files:**
- Modify: `app/src/main/java/com/azuresamples/msalnativeauthandroidkotlinsampleapp/PasswordResetNewPasswordFragment.kt`

**Interfaces:**
- Consumes: a bundle state that is either `ResetPasswordPasswordRequiredState` or `NativeAuthFlowStateV2`
- Produces: `NativeAuthFlowStateV2.submitNewPassword(password)` and direct completion on `NativeAuthResultV2.Complete`

- [ ] **Step 1: Store the V1 or V2 state**

Use separate nullable state properties and select the parcelled state with `Configuration.useNativeAuthV2`, matching Task 2.

- [ ] **Step 2: Preserve the V1 reset/sign-in continuation**

Move the existing V1 `submitPassword` and `signInAfterPasswordReset` behavior into a `resetPasswordV1(password)` helper without changing its behavior.

- [ ] **Step 3: Add direct V2 completion**

For V2 call `currentStateV2!!.submitNewPassword(password)` and handle:

```kotlin
when (val result = currentStateV2!!.submitNewPassword(password)) {
    is NativeAuthResultV2.Complete -> {
        Toast.makeText(
            requireContext(),
            getString(R.string.password_reset_success_message),
            Toast.LENGTH_LONG
        ).show()
        finish()
    }
    is NativeAuthErrorV2 -> {
        currentStateV2 = result.nextState ?: currentStateV2
        displayDialog(
            result.error ?: getString(R.string.unexpected_sdk_error_title),
            result.errorMessage
        )
    }
    else -> displayDialog(
        getString(R.string.unexpected_sdk_result_title),
        result.toString()
    )
}
```

Always clear the password field and zero the `CharArray` after either API call.

- [ ] **Step 4: Build the source-linked sample**

Place this repository at `C:\Users\djanardhan\android-complete\nativeauthsample`, check out the matching Common and MSAL branches, then run:

```powershell
.\gradlew.bat :NativeAuthSample:assembleLocalDebug
```

Expected: `BUILD SUCCESSFUL`, unless private Azure Artifacts credentials are unavailable.

- [ ] **Step 5: Review and commit only intended files**

Run:

```powershell
git diff --check
git status --short
```

Stage the three fragments and plan, excluding `auto-config.json`, then commit with the required Copilot trailer.

- [ ] **Step 6: Push all prototype branches**

Push `djanardhan/sspr-v2-prototype` in the sample, MSAL, and Common repositories so another machine can check out the same branch name in all three.
