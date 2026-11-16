// `passkey.ts` is not here on purpose: it pulls @simplewebauthn/browser, which stays out of the
// entry (D27). Lazy code imports `@/shared/auth/passkey` directly.
export { startGoogleSignIn } from './google'
export { ReauthProvider } from './ReauthProvider'
export { RETURN_PATHS, saveReturnPath, takeReturnPath } from './returnPath'
export { useReauth } from './useReauth'
