import { describe, expect, it } from 'vitest'
import { decode } from './decode'
import { fixtureBytes } from './fixtures.test-util'
import { readOfx } from './ofx'
import { StatementReadError } from './types'

const wrap = (body: string) => `<OFX><BANKTRANLIST>${body}</BANKTRANLIST></OFX>`
const trn = (fields: string) => `<STMTTRN>${fields}</STMTTRN>`

function reasonOf(fn: () => unknown): string | undefined {
  try {
    fn()
  } catch (e) {
    return e instanceof StatementReadError ? e.reason : 'other'
  }
  return undefined
}

describe('readOfx', () => {
  it('readsSgml', async () => {
    const lines = readOfx(decode(await fixtureBytes('ofx-sgml-1252.ofx'), 'ofx'))
    expect(lines).toEqual([
      { occurredOn: '2026-11-03', cents: -4590n, description: 'PADARIA PAO QUENTE' },
      { occurredOn: '2026-11-04', cents: -1250n, description: 'MERCADO CENTRAL' },
      { occurredOn: '2026-11-04', cents: -1250n, description: 'MERCADO CENTRAL' },
      { occurredOn: '2026-11-05', cents: 10000n, description: 'Doação mensal' },
    ])
  })

  it('readsXml', async () => {
    const lines = readOfx(decode(await fixtureBytes('ofx-xml-utf8.ofx'), 'ofx'))
    expect(lines.map((l) => l.description)).toEqual([
      'PADARIA PAO QUENTE - COMPRA NO DEBITO',
      'MERCADO CENTRAL',
      'DOAÇÃO MENSAL',
    ])
  })

  it('readsCardSigns', async () => {
    const lines = readOfx(decode(await fixtureBytes('ofx-card-negative-purchases.ofx'), 'ofx'))
    expect(lines.map((l) => l.cents)).toEqual([-4590n, -12000n, -3340n, 19930n])
  })

  it('commaAmountAndEntities', async () => {
    const lines = readOfx(
      wrap(
        trn(
          '<DTPOSTED>20261103<TRNAMT>-12,34<NAME>BAR &amp; GRILL &lt;1&gt; &quot;X&quot; &apos;Y&apos;',
        ) + trn('<DTPOSTED>20261104<TRNAMT>+0.5<MEMO>X'),
      ),
    )
    expect(lines[0]).toEqual({
      occurredOn: '2026-11-03',
      cents: -1234n,
      description: 'BAR & GRILL <1> "X" \'Y\'',
    })
    expect(lines[1]?.cents).toBe(50n)
  })

  it('notOfxIsUnreadable', async () => {
    const csv = decode(await fixtureBytes('csv-semicolon-comma-decimal.csv'), 'csv')
    expect(reasonOf(() => readOfx(csv))).toBe('unreadable')
  })

  it('brokenTransactionIsUnreadable', async () => {
    expect(reasonOf(() => readOfx(wrap(trn('<DTPOSTED>2026<TRNAMT>1.00'))))).toBe('unreadable')
    expect(reasonOf(() => readOfx(wrap(trn('<DTPOSTED>20261103<TRNAMT>abc'))))).toBe('unreadable')
  })

  it('emptyAndTooLarge', async () => {
    expect(reasonOf(() => readOfx(wrap('')))).toBe('empty')
    const one = trn('<DTPOSTED>20261103<TRNAMT>-1.00<MEMO>X')
    expect(readOfx(wrap(one.repeat(2000)))).toHaveLength(2000)
    expect(reasonOf(() => readOfx(wrap(one.repeat(2001))))).toBe('tooLarge')
  })
})
