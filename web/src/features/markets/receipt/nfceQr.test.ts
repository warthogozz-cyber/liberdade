import { describe, expect, it } from 'vitest'
import { defaultIssuedOn, lookupBody, parseNfceInput, type ParsedNfce } from './nfceQr'
import { makeChave, withWrongCheckDigit } from './testing/nfceFixtures'

const TODAY = new Date(2026, 10, 25)
const BUYER = '12345678909'
const chave = makeChave({ aamm: '2611' })

const hex = (text: string) =>
  [...text].map((char) => char.charCodeAt(0).toString(16).padStart(2, '0')).join('')

function ok(result: ReturnType<typeof parseNfceInput>): ParsedNfce {
  if ('error' in result) throw new Error(`expected a parsed key, got ${result.error}`)
  return result
}

const v1 = (key: string, extra = '', tpAmb = 1) =>
  `https://sefaz.example.test/nfce/qrcode?chNFe=${key}&nVersao=100&tpAmb=${tpAmb}${extra}&vNF=10.00&digVal=abc&cIdToken=000001&cHashQRCode=ABCDEF`

describe('parseNfceInput', () => {
  it('v1DropsCDest', () => {
    const parsed = ok(parseNfceInput(v1(chave, `&cDest=${BUYER}`), TODAY))
    expect(parsed).toMatchObject({ version: 1, chave })
    expect(parsed.p).toBeUndefined()
    expect(JSON.stringify(parsed)).not.toContain(BUYER)
    expect(JSON.stringify(lookupBody(parsed))).not.toContain(BUYER)
    expect(lookupBody(parsed)).toEqual({ chave, version: 1 })
  })

  it('v1DateFromHex', () => {
    const dhEmi = hex('2026-11-10T10:30:00-03:00')
    const parsed = ok(parseNfceInput(v1(chave, `&dhEmi=${dhEmi}`), TODAY))
    expect(parsed.issuedOn).toBe('2026-11-10')
  })

  it('v1KeepsTheLinkOnlyWithoutABuyerDocument', () => {
    expect(ok(parseNfceInput(v1(chave), TODAY)).sefazUrl).toBe(v1(chave))
    expect(ok(parseNfceInput(v1(chave, `&cDest=${BUYER}`), TODAY)).sefazUrl).toBeUndefined()
  })

  it('v2OnlineAndOffline', () => {
    const online = `${chave}|2|1|000001|ABCDEF`
    const onlineParsed = ok(parseNfceInput(`https://sefaz.example.test/qr?p=${online}`, TODAY))
    expect(onlineParsed).toMatchObject({ version: 2, chave, p: online })
    expect(onlineParsed.issuedOn).toBeUndefined()

    const offline = `${chave}|2|1|09|10.00|abc|000001|ABCDEF`
    const offlineParsed = ok(
      parseNfceInput(`https://sefaz.example.test/qr?p=${encodeURIComponent(offline)}`, TODAY),
    )
    expect(offlineParsed).toMatchObject({ version: 2, chave, p: offline, issuedOn: '2026-11-09' })
    expect(lookupBody(offlineParsed)).toEqual({ chave, version: 2, p: offline })
  })

  it('v3OfflineKeepsNoBuyerDocument', () => {
    const offline = `${chave}|3|1|09|10.00|1|${BUYER}|c2lnbmF0dXJl`
    const parsed = ok(parseNfceInput(`https://sefaz.example.test/qr?p=${offline}`, TODAY))
    expect(parsed).toMatchObject({ version: 3, chave, p: `${chave}|3|1`, issuedOn: '2026-11-09' })
    expect(JSON.stringify(parsed)).not.toContain(BUYER)
    expect(JSON.stringify(lookupBody(parsed))).not.toContain(BUYER)
  })

  it('v3Online', () => {
    const parsed = ok(parseNfceInput(`https://sefaz.example.test/qr?p=${chave}|3|1`, TODAY))
    expect(parsed).toMatchObject({ version: 3, p: `${chave}|3|1` })
    expect(parsed.sefazUrl).toBe(`https://sefaz.example.test/qr?p=${chave}|3|1`)
  })

  it('homologationRefused', () => {
    expect(parseNfceInput(v1(chave, '', 2), TODAY)).toEqual({ error: 'homologation' })
    expect(parseNfceInput(`https://x.test/qr?p=${chave}|2|2|000001|ABCDEF`, TODAY)).toEqual({
      error: 'homologation',
    })
    expect(parseNfceInput(`https://x.test/qr?p=${chave}|3|2`, TODAY)).toEqual({
      error: 'homologation',
    })
  })

  it('typedKey', () => {
    const spaced = chave.replace(/(.{4})/g, '$1 ').trim()
    const parsed = ok(parseNfceInput(spaced, TODAY))
    expect(parsed).toEqual({ chave, version: 0 })
    expect(lookupBody(parsed)).toEqual({ chave, version: 0 })
  })

  it('typedKeyWithLettersInTheCnpj', () => {
    const lettered = makeChave({ cnpjBase: 'AB12CD34EF56' })
    expect(ok(parseNfceInput(lettered, TODAY))).toEqual({ chave: lettered, version: 0 })
  })

  it('badCheckDigitAndModel', () => {
    expect(parseNfceInput(withWrongCheckDigit(chave), TODAY)).toEqual({ error: 'check-digit' })
    expect(parseNfceInput(makeChave({ model: '55' }), TODAY)).toEqual({ error: 'model' })
    expect(parseNfceInput('https://example.test/anything?x=1', TODAY)).toEqual({
      error: 'not-nfce',
    })
    expect(parseNfceInput('hello world', TODAY)).toEqual({ error: 'not-nfce' })
  })

  it('wrongShapeIsFormat', () => {
    expect(parseNfceInput(chave.slice(1), TODAY)).toEqual({ error: 'format' })
    expect(parseNfceInput(makeChave({ uf: '99' }), TODAY)).toEqual({ error: 'format' })
    expect(parseNfceInput(makeChave({ aamm: '2613' }), TODAY)).toEqual({ error: 'format' })
    expect(parseNfceInput(makeChave({ aamm: '2612' }), TODAY)).toEqual({ error: 'format' })
    expect(parseNfceInput(makeChave({ aamm: '0912' }), TODAY)).toEqual({ error: 'format' })
    expect(parseNfceInput(makeChave({ cnpjBase: '000000000000' }), TODAY)).toEqual({
      error: 'format',
    })
  })

  it('sefazUrlOnlyHttp', () => {
    const parsed = ok(
      parseNfceInput(`javascript:alert(1)//?chNFe=${chave}&nVersao=100&tpAmb=1`, TODAY),
    )
    expect(parsed.version).toBe(1)
    expect(parsed.sefazUrl).toBeUndefined()
  })

  it('defaultDate', () => {
    expect(defaultIssuedOn({ chave, version: 0 }, TODAY)).toBe('2026-11-25')
    const old = makeChave({ aamm: '2610' })
    expect(defaultIssuedOn({ chave: old, version: 0 }, TODAY)).toBeUndefined()
    expect(defaultIssuedOn({ chave, version: 1, issuedOn: '2026-11-03' }, TODAY)).toBe('2026-11-03')
  })
})
