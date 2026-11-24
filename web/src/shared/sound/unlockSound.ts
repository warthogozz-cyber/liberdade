type Synth = typeof import('./synth')

let loaded: Synth | undefined
let loading: Promise<Synth | undefined> | undefined

function load(): Promise<Synth | undefined> {
  loading ??= import('./synth').then(
    (module) => (loaded = module),
    () => {
      // The chunk failed to load: a missing sound is never an error.
      loading = undefined
      return undefined
    },
  )
  return loading
}

/**
 * Unlocks the audio context from inside a person's gesture: right away when the synth is already
 * loaded, otherwise as soon as it is. A device without Web Audio stays silent.
 */
export function unlockSound(): void {
  const attempt = (synth: Synth | undefined) => {
    try {
      synth?.unlock()
    } catch {
      // No Web Audio.
    }
  }
  if (loaded) attempt(loaded)
  else void load().then(attempt)
}

/**
 * Loads the synth at idle and unlocks the audio context on the first tap or key, because WebKit
 * keeps a context created outside a gesture suspended. Returns the cleanup.
 */
export function armSoundUnlock(): () => void {
  let disposed = false
  const idle = (callback: () => void): (() => void) => {
    if (typeof requestIdleCallback === 'function') {
      const id = requestIdleCallback(callback)
      return () => cancelIdleCallback(id)
    }
    const id = setTimeout(callback, 1_000)
    return () => clearTimeout(id)
  }

  const onGesture = () => {
    if (!loaded) return
    if (loaded.isRunning()) {
      remove()
      return
    }
    unlockSound()
  }
  const remove = () => {
    window.removeEventListener('pointerdown', onGesture, true)
    window.removeEventListener('keydown', onGesture, true)
  }

  window.addEventListener('pointerdown', onGesture, true)
  window.addEventListener('keydown', onGesture, true)
  const cancelIdle = idle(() => {
    if (!disposed) void load()
  })
  return () => {
    disposed = true
    cancelIdle()
    remove()
  }
}
