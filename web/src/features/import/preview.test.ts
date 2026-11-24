import { describe, expect, it } from 'vitest'
import type { TransactionView } from '@/shared/api'
import { plannedView, transactionView } from '@/shared/testing'
import type { KindedLine } from './kinds'
import { buildPreview, previewRange } from './preview'

const ACCOUNT = '0192f5a0-0000-7000-8000-000000000001'
const CATEGORY = '0192f5a0-0000-7000-8000-0000000000c1'

const line = (over: Partial<KindedLine> = {}): KindedLine => ({
  occurredOn: '2026-11-03',
  cents: -4590n,
  description: 'Padaria Pão Quente',
  kind: 'EXPENSE',
  absCents: 4590n,
  billPayment: false,
  ...over,
})

let counter = 0
const newId = () => `id-${++counter}`

function confirmed(
  over: Partial<TransactionView> = {},
  key: string | null = null,
): TransactionView {
  return {
    ...transactionView({
      id: `c-${++counter}`,
      accountId: ACCOUNT,
      kind: 'EXPENSE',
      amount: { amount: '45.90', currency: 'BRL' },
      occurredOn: '2026-11-03',
      categoryId: CATEGORY,
      description: 'PADARIA PAO QUENTE',
    }),
    importKey: key,
    ...over,
  }
}

function planned(date: string, over: Partial<TransactionView> = {}): TransactionView {
  return plannedView(
    {
      id: `p-${++counter}`,
      accountId: ACCOUNT,
      kind: 'EXPENSE',
      amount: { amount: '45.90', currency: 'BRL' },
      occurredOn: date,
      categoryId: CATEGORY,
    },
    date,
    over,
  )
}

describe('buildPreview', () => {
  it('alreadyImportedWins', () => {
    const p = planned('2026-11-03')
    const [out] = buildPreview([line()], ['k1'], [confirmed({}, 'k1'), p], true, newId)
    expect(out.state).toBe('alreadyImported')
    expect(out.checked).toBe(false)
    expect(out.confirmsPlannedId).toBeNull()
  })

  it('confirmsTheClosestPlanned', () => {
    const p = planned('2026-11-10')
    const out = buildPreview(
      [line({ occurredOn: '2026-11-08' }), line({ occurredOn: '2026-11-11' })],
      ['a', 'b'],
      [p],
      true,
      newId,
    )
    expect(out.map((l) => l.state)).toEqual(['normal', 'confirmPlanned'])
    expect(out[1].confirmsPlannedId).toBe(p.id)
    expect(out[0].confirmsPlannedId).toBeNull()
    expect(out.map((l) => l.checked)).toEqual([true, true])
  })

  it('aTiePicksTheEarlierLine', () => {
    const p = planned('2026-11-10')
    const out = buildPreview(
      [line({ occurredOn: '2026-11-09' }), line({ occurredOn: '2026-11-11' })],
      ['a', 'b'],
      [p],
      true,
      newId,
    )
    expect(out.map((l) => l.state)).toEqual(['confirmPlanned', 'normal'])
  })

  it('eachPlannedIsConfirmedByOneLineOnly', () => {
    const p1 = planned('2026-11-10')
    const p2 = planned('2026-11-12')
    const out = buildPreview(
      [line({ occurredOn: '2026-11-11' }), line({ occurredOn: '2026-11-11' })],
      ['a', 'b'],
      [p1, p2],
      true,
      newId,
    )
    expect(out.map((l) => l.state)).toEqual(['confirmPlanned', 'confirmPlanned'])
    expect(new Set(out.map((l) => l.confirmsPlannedId)).size).toBe(2)
  })

  it('plannedOnlyWithinFiveDays', () => {
    const p = planned('2026-11-10')
    const out = buildPreview(
      [line({ occurredOn: '2026-11-16' }), line({ occurredOn: '2026-11-15' })],
      ['a', 'b'],
      [p],
      true,
      newId,
    )
    expect(out.map((l) => l.state)).toEqual(['normal', 'confirmPlanned'])
  })

  it('plannedNeedsSameKindAndAmount', () => {
    const p = planned('2026-11-03')
    const out = buildPreview(
      [line({ absCents: 4591n }), line({ kind: 'INCOME' })],
      ['a', 'b'],
      [p],
      true,
      newId,
    )
    expect(out.map((l) => l.state)).toEqual(['normal', 'normal'])
  })

  it('noPlannedMatchWithPlanningOff', () => {
    const out = buildPreview([line()], ['a'], [planned('2026-11-03')], false, newId)
    expect(out[0].state).toBe('normal')
  })

  it('possibleRepeatOnlyWithoutKey', () => {
    const [manual] = buildPreview([line()], ['a'], [confirmed()], true, newId)
    expect(manual.state).toBe('possibleRepeat')
    expect(manual.checked).toBe(false)

    const [keyed] = buildPreview([line()], ['a'], [confirmed({}, 'other')], true, newId)
    expect(keyed.state).toBe('normal')
    expect(keyed.checked).toBe(true)
  })

  it('possibleRepeatNeedsTheSameDateAmountKindAndDescription', () => {
    const out = buildPreview(
      [
        line({ occurredOn: '2026-11-04' }),
        line({ absCents: 100n }),
        line({ kind: 'INCOME' }),
        line({ description: 'Outra loja' }),
      ],
      ['a', 'b', 'c', 'd'],
      [confirmed()],
      true,
      newId,
    )
    expect(out.map((l) => l.state)).toEqual(['normal', 'normal', 'normal', 'normal'])
  })

  it('billPaymentUnchecked', () => {
    const [out] = buildPreview(
      [line({ kind: 'INCOME', billPayment: true })],
      ['a'],
      [],
      true,
      newId,
    )
    expect(out.state).toBe('billPayment')
    expect(out.checked).toBe(false)
  })

  it('plannedOutranksBillPayment', () => {
    const p = planned('2026-11-03', { kind: 'INCOME' })
    const [out] = buildPreview(
      [line({ kind: 'INCOME', billPayment: true })],
      ['a'],
      [p],
      true,
      newId,
    )
    expect(out.state).toBe('confirmPlanned')
  })

  it('balanceLinesComeUnchecked', () => {
    const out = buildPreview(
      [
        line({ description: 'SALDO ANTERIOR', kind: 'INCOME' }),
        line({ description: 'Saldo do dia' }),
        line({ description: 'S A L D O' }),
        line({ description: 'PAGTO SALDO DEVEDOR' }),
      ],
      ['a', 'b', 'c', 'd'],
      [planned('2026-11-03')],
      true,
      newId,
    )
    expect(out.map((l) => l.state)).toEqual(['balance', 'balance', 'balance', 'confirmPlanned'])
    expect(out.map((l) => l.checked)).toEqual([false, false, false, true])
  })

  it('alreadyImportedOutranksBalance', () => {
    const [out] = buildPreview(
      [line({ description: 'SALDO ANTERIOR' })],
      ['k1'],
      [confirmed({}, 'k1')],
      true,
      newId,
    )
    expect(out.state).toBe('alreadyImported')
  })

  it('confirmPlannedCarriesThePlannedCategory', () => {
    const p = planned('2026-11-03')
    const [out] = buildPreview([line()], ['a'], [p], true, newId)
    expect(out).toMatchObject({
      state: 'confirmPlanned',
      categoryId: CATEGORY,
      confirmsPlannedId: p.id,
    })
  })

  it('plannedWithoutCategoryIsNoCandidate', () => {
    const [out] = buildPreview(
      [line()],
      ['a'],
      [planned('2026-11-03', { categoryId: null })],
      true,
      newId,
    )
    expect(out.state).toBe('normal')
  })

  it('carriesKeyIdAndEmptyCategory', () => {
    const [out] = buildPreview([line()], ['key-x'], [], true, () => 'fresh-id')
    expect(out).toMatchObject({
      id: 'fresh-id',
      importKey: 'key-x',
      categoryId: null,
      confirmsPlannedId: null,
      checked: true,
      state: 'normal',
    })
  })
})

describe('previewRange', () => {
  it('rangeIsFiveDaysWide', () => {
    expect(
      previewRange([
        line({ occurredOn: '2026-11-28' }),
        line({ occurredOn: '2026-11-03' }),
        line({ occurredOn: '2026-11-15' }),
      ]),
    ).toEqual({ from: '2026-10-29', to: '2026-12-03' })
  })

  it('crossesTheYear', () => {
    expect(
      previewRange([line({ occurredOn: '2026-01-02' }), line({ occurredOn: '2026-12-30' })]),
    ).toEqual({ from: '2025-12-28', to: '2027-01-04' })
  })
})
