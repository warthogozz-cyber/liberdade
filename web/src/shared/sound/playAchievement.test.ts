import { describe, expect, it } from 'vitest'
import { playAchievement } from './playAchievement'

describe('playAchievement', () => {
  it('resolvesWithoutAudioSupport', async () => {
    // jsdom has no AudioContext: the call must not throw.
    await expect(playAchievement()).resolves.toBeUndefined()
  })
})
