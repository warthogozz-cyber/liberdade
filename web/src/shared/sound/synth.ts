// Two short sine notes (C6, E6), 120 ms each, under 300 ms in all and quiet (D13).
const NOTES = [1046.5, 1318.51]
const NOTE_SECONDS = 0.12
const ATTACK_SECONDS = 0.01
const PEAK_GAIN = 0.08

let shared: AudioContext | undefined

/** The shared context, created on first use. */
function audioContext(): AudioContext {
  if (!shared) {
    // Mixes with other audio instead of interrupting it, where the browser supports it.
    const session = (navigator as Navigator & { audioSession?: { type: string } }).audioSession
    if (session) session.type = 'ambient'
    shared = new AudioContext()
  }
  return shared
}

/**
 * Creates the shared context and resumes it. It must run inside a person's gesture (a tap or key),
 * because WebKit keeps a context suspended when it is created or resumed outside one. After that,
 * `play` can run at any time, such as after a network round trip.
 */
export function unlock(): void {
  audioContext()
    .resume()
    .catch(() => {})
}

/** Whether the shared context exists and is running. */
export function isRunning(): boolean {
  return shared?.state === 'running'
}

/** Plays the notes on the shared context, which `unlock` readied on an earlier gesture. */
export function play(context: AudioContext = audioContext()): void {
  if (context.state === 'suspended') context.resume().catch(() => {})
  const begin = context.currentTime
  NOTES.forEach((frequency, index) => {
    const start = begin + index * NOTE_SECONDS
    const end = start + NOTE_SECONDS
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    oscillator.type = 'sine'
    oscillator.frequency.value = frequency
    gain.gain.setValueAtTime(0, start)
    gain.gain.linearRampToValueAtTime(PEAK_GAIN, start + ATTACK_SECONDS)
    gain.gain.linearRampToValueAtTime(0, end)
    oscillator.connect(gain)
    gain.connect(context.destination)
    oscillator.start(start)
    oscillator.stop(end)
  })
}
