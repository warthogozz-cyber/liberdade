import { useState } from 'react'
import { patchLocation, readLocation } from './locationStore'

type ConsentKey = 'mapConsent' | 'instagramConsent'

/**
 * Whether the person already accepted a third party's notice. "Do not ask again" is kept with the
 * saved location choice; without one (nothing stored yet) it lasts only as long as the screen does,
 * and no choice the person did not make is ever written.
 */
export function useConsent(userId: string | undefined, key: ConsentKey) {
  const [onScreen, setOnScreen] = useState(false)
  const stored = userId !== undefined && readLocation(userId)?.[key] === true
  return {
    granted: stored || onScreen,
    remember: () => {
      setOnScreen(true)
      if (userId !== undefined) patchLocation(userId, { [key]: true })
    },
  }
}
