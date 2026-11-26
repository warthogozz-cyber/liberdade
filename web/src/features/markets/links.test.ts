import { describe, expect, it } from 'vitest'
import {
  isValidHttpsUrl,
  isValidInstagram,
  mapsDirectionsUrl,
  mapsSearchUrl,
  wazeUrl,
} from './links'

describe('links', () => {
  it('buildsTheThreeUrls', () => {
    expect(mapsSearchUrl(-10.00012, -50.00034)).toBe(
      'https://www.google.com/maps/search/?api=1&query=-10.00012%2C-50.00034',
    )
    expect(mapsDirectionsUrl(-10.00012, -50.00034)).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=-10.00012%2C-50.00034',
    )
    expect(wazeUrl(-10.00012, -50.00034)).toBe(
      'https://waze.com/ul?ll=-10.00012,-50.00034&navigate=yes',
    )
  })

  it('acceptsOnlyPlainHttps', () => {
    expect(isValidHttpsUrl('https://x.example/encarte')).toBe(true)
    expect(isValidHttpsUrl('http://x.example')).toBe(false)
    expect(isValidHttpsUrl('https://user:pass@x.example')).toBe(false)
    expect(isValidHttpsUrl(`https://x.example/${'a'.repeat(500)}`)).toBe(false)
    expect(isValidHttpsUrl('javascript:alert(1)')).toBe(false)
  })

  it('acceptsTheInstagramForms', () => {
    for (const ok of [
      '@loja.exemplo',
      'loja_exemplo',
      'https://www.instagram.com/loja.exemplo/',
      'https://www.instagram.com/p/AbC-123/',
      'https://www.instagram.com/reel/AbC_123/',
    ]) {
      expect(isValidInstagram(ok), ok).toBe(true)
    }
    for (const bad of [
      '',
      '@',
      'a b',
      'x'.repeat(31),
      'https://example.com/loja',
      'http://www.instagram.com/loja/',
    ]) {
      expect(isValidInstagram(bad), bad).toBe(false)
    }
  })
})
