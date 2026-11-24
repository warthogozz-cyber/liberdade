// Two short sine notes (C6, E6), 120 ms each, under 300 ms in all and quiet (D13).
const NOTES = [1046.5, 1318.51]
const NOTE_SECONDS = 0.12
const ATTACK_SECONDS = 0.01
const PEAK_GAIN = 0.08

let shared: AudioContext | undefined

function audioContext(): AudioContext {
  if (!shared) {
    // Mixes with other audio instead of interrupting it, where the browser supports it.
    const session = (navigator as Navigator & { audioSession?: { type: string } }).audioSession
    if (session) session.type = 'ambient'
    shared = new AudioContext()
  }
  return shared
}

/** Plays the notes; the context is created on the first call, which is a person's gesture. */
export function play(context: AudioContext = audioContext()): void {
  if (context.state === 'suspended') void context.resume()
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
