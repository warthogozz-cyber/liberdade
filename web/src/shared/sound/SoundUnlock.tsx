import { useEffect } from 'react'
import { isSoundEnabled } from './soundPreference'
import { armSoundUnlock } from './unlockSound'

/** Draws nothing; with the sound on, readies the audio context for the first tap of a session. */
export function SoundUnlock() {
  useEffect(() => (isSoundEnabled() ? armSoundUnlock() : undefined), [])
  return null
}
