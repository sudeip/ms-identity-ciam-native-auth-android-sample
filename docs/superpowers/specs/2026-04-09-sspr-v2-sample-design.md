# Native Auth V2 SSPR Sample Design

## Goal

Complete the sample app's end-to-end V2 self-service password reset flow on top of
`origin/spetrescu/native-auth-v2-test`, while preserving the existing three-screen UI
and the optional V1 path.

## Design

- `PasswordResetFragment` starts V2 SSPR with `resetPasswordV2` and navigates when
  the result is `CodeRequired`.
- `PasswordResetCodeFragment` receives `NativeAuthFlowStateV2` through its argument
  bundle, calls `submitCode` or `resendCode`, and navigates on `NewPasswordRequired`.
- `PasswordResetNewPasswordFragment` receives the next V2 state, calls
  `submitNewPassword`, and treats `Complete` as an already signed-in account.
- `NativeAuthErrorV2.nextState` replaces the current state when present so invalid
  codes and passwords can be corrected without restarting the flow.
- V1 behavior remains unchanged when `Configuration.useNativeAuthV2` is false.

## Scope

Only the existing password-reset fragments and any directly required imports or
navigation types will change. This prototype adds no tests, telemetry, new screens,
or unrelated refactoring.

## Verification

Build the source-linked `localDebug` sample and manually verify code resend,
invalid-code retry, weak-password retry, successful completion, current-account
display, and access-token acquisition.
