import { REAUTH_USER_KEY, RETURN_PATH_KEY } from '@/shared/offline'

/** The pages a re-authentication or an invitation may bring the person back to. */
export const RETURN_PATHS = ['/convite', '/mais/familia', '/mais/privacidade', '/mais/modulos']

/** The path of `value` when it is a listed page, with its query and hash; otherwise `undefined`. */
function accepted(value: string | null | undefined): string | undefined {
  if (typeof value !== 'string' || !value.startsWith('/')) return undefined
  // Only a plain absolute path: no `//`, backslash, scheme or control character.
  // eslint-disable-next-line no-control-regex
  if (/[\\\u0000-\u001f\u007f]/.test(value) || value.includes('//')) return undefined
  const end = value.search(/[?#]/)
  const pathname = end === -1 ? value : value.slice(0, end)
  if (pathname.includes(':')) return undefined
  return RETURN_PATHS.includes(pathname) ? value : undefined
}

/** Remembers where to come back to, in this tab only. Anything but a listed page is ignored. */
export function saveReturnPath(path: string): void {
  const value = accepted(path)
  if (!value) return
  try {
    sessionStorage.setItem(RETURN_PATH_KEY, value)
  } catch {
    // Without storage the person lands on the default page.
  }
}

/** The remembered path, once: it is removed whether or not it is still valid. */
export function takeReturnPath(): string | undefined {
  try {
    const value = sessionStorage.getItem(RETURN_PATH_KEY)
    sessionStorage.removeItem(RETURN_PATH_KEY)
    return accepted(value)
  } catch {
    return undefined
  }
}

/** Remembers who is signed in as a Google re-authentication starts, to notice another person back. */
export function saveReauthUser(userId: string): void {
  try {
    sessionStorage.setItem(REAUTH_USER_KEY, userId)
  } catch {
    // Without storage a change of person is not noticed on return.
  }
}

/** Who was signed in when the re-authentication began, once; `undefined` when none was recorded. */
export function takeReauthUser(): string | undefined {
  try {
    const value = sessionStorage.getItem(REAUTH_USER_KEY)
    sessionStorage.removeItem(REAUTH_USER_KEY)
    return value || undefined
  } catch {
    return undefined
  }
}
