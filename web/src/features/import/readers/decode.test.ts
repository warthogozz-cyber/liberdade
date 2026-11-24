import { describe, expect, it } from 'vitest'
import { decode } from './decode'
import { fixtureBytes } from './fixtures.test-util'

describe('decode', () => {
  it('ofx1252FallsBackFromUtf8', async () => {
    const text = decode(await fixtureBytes('ofx-sgml-1252.ofx'))
    expect(text).toContain('Doação mensal')
  })

  it('csvFallsBackTo1252', async () => {
    const text = decode(await fixtureBytes('csv-debit-credit-columns-1252.csv'))
    expect(text).toContain('Doação mensal')
    expect(text).toContain('AÇOUGUE SÃO JOÃO')
    expect(text).toContain('Débito')
  })

  it('csvKeepsStrictUtf8', async () => {
    const text = decode(await fixtureBytes('csv-semicolon-comma-decimal.csv'))
    expect(text).toContain('Doação mensal')
  })

  it('ofxXmlDefaultsToUtf8', async () => {
    expect(decode(await fixtureBytes('ofx-xml-utf8.ofx'))).toContain('DOAÇÃO MENSAL')
  })

  it('ofxSgmlUtf8WithCharsetNoneIsUtf8', async () => {
    const text = decode(await fixtureBytes('ofx-sgml-utf8-charset-none.ofx'))
    expect(text).toContain('AÇOUGUE SÃO JOÃO')
    expect(text).toContain('FARMÁCIA ESPERANÇA')
    expect(text).toContain('Doação mensal')
  })

  it('stripsTheBom', async () => {
    const bytes = new Uint8Array([0xef, 0xbb, 0xbf, ...new TextEncoder().encode('Data;Valor')])
    const text = decode(bytes)
    expect(text).toBe('Data;Valor')
    expect(text.charCodeAt(0)).not.toBe(0xfeff)
  })

  it('readsUtf16ByItsBom', () => {
    const sample = 'Data;Descrição;Valor'
    const units = [...sample].map((c) => c.charCodeAt(0))
    const le = new Uint8Array([0xff, 0xfe, ...units.flatMap((u) => [u & 0xff, u >> 8])])
    const be = new Uint8Array([0xfe, 0xff, ...units.flatMap((u) => [u >> 8, u & 0xff])])
    expect(decode(le)).toBe(sample)
    expect(decode(be)).toBe(sample)
    expect(decode(le)).not.toContain('\0')
  })
})
