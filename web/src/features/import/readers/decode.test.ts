import { describe, expect, it } from 'vitest'
import { decode } from './decode'
import { fixtureBytes } from './fixtures.test-util'

describe('decode', () => {
  it('ofxHeaderSays1252', async () => {
    const text = decode(await fixtureBytes('ofx-sgml-1252.ofx'), 'ofx')
    expect(text).toContain('Doação mensal')
  })

  it('csvFallsBackTo1252', async () => {
    const text = decode(await fixtureBytes('csv-debit-credit-columns-1252.csv'), 'csv')
    expect(text).toContain('Doação mensal')
    expect(text).toContain('AÇOUGUE SÃO JOÃO')
    expect(text).toContain('Débito')
  })

  it('csvKeepsStrictUtf8', async () => {
    const text = decode(await fixtureBytes('csv-semicolon-comma-decimal.csv'), 'csv')
    expect(text).toContain('Doação mensal')
  })

  it('ofxXmlDefaultsToUtf8', async () => {
    expect(decode(await fixtureBytes('ofx-xml-utf8.ofx'), 'ofx')).toContain('DOAÇÃO MENSAL')
  })

  it('stripsTheBom', async () => {
    const bytes = new Uint8Array([0xef, 0xbb, 0xbf, ...new TextEncoder().encode('Data;Valor')])
    for (const hint of ['csv', 'ofx'] as const) {
      const text = decode(bytes, hint)
      expect(text).toBe('Data;Valor')
      expect(text.charCodeAt(0)).not.toBe(0xfeff)
    }
  })
})
