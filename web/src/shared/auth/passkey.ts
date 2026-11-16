import {
  startAuthentication,
  startRegistration,
  type PublicKeyCredentialCreationOptionsJSON,
  type PublicKeyCredentialRequestOptionsJSON,
} from '@simplewebauthn/browser'
import { postJson } from '@/shared/api'

/**
 * Signs in with a passkey of this device. Throws the browser's `NotAllowedError` when the person
 * closes the prompt, or the ApiError of a refused answer. The caller starts the new session's cache.
 */
export async function signInWithPasskey(): Promise<void> {
  const optionsJSON = (await postJson(
    '/webauthn/authenticate/options',
  )) as PublicKeyCredentialRequestOptionsJSON
  const credential = await startAuthentication({ optionsJSON })
  await postJson('/login/webauthn', credential)
}

/** Creates a passkey on this device for the signed-in person, named `label`. */
export async function registerPasskey(label: string): Promise<void> {
  const optionsJSON = (await postJson(
    '/webauthn/register/options',
  )) as PublicKeyCredentialCreationOptionsJSON
  const credential = await startRegistration({ optionsJSON })
  await postJson('/webauthn/register', { publicKey: { credential, label } })
}
