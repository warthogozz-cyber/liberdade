/** Plays the achievement sound. The synth loads on demand, and a device without audio stays silent. */
export async function playAchievement(): Promise<void> {
  try {
    const { play } = await import('./synth')
    play()
  } catch {
    // No Web Audio, or the chunk failed to load: a missing sound is never an error.
  }
}
