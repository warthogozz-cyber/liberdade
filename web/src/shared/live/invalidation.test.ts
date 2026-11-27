import { describe, expect, it } from 'vitest'
import { invalidatesMe, invalidationPrefixes } from './invalidation'
import { marketsKeys } from '@/shared/api/markets'
import { venturesKeys } from '@/shared/api/ventures'

describe('invalidationPrefixes', () => {
  it.each([
    [
      'transaction',
      [
        ['transactions'],
        ['dashboard'],
        ['accounts'],
        ['tags'],
        ['planning'],
        ['gamification'],
        ['ventures'],
      ],
    ],
    ['account', [['accounts'], ['transactions'], ['dashboard'], ['planning'], ['gamification']]],
    ['achievement', [['gamification']]],
    ['category', [['categories'], ['transactions'], ['dashboard'], ['planning']]],
    ['tag', [['tags'], ['transactions']]],
    ['rule', [['recurring-rules'], ['transactions'], ['planning']]],
    ['budget', [['planning', 'budgets']]],
    ['goal', [['planning', 'goals']]],
    [
      'reserve',
      [
        ['planning', 'reserve'],
        ['planning', 'projection'],
      ],
    ],
    ['members', [['members'], ['invitations'], ['accounts']]],
    ['modules', [['modules'], ['planning'], ['ventures']]],
    ['unknown', [[]]],
  ])('mapsEveryEntity: %s', (entity, expected) => {
    expect(invalidationPrefixes(entity)).toEqual(expected)
  })
})

describe('invalidatesMe', () => {
  it('refreshesTheSignedInMemberOnMembersOnly', () => {
    expect(invalidatesMe('members')).toBe(true)
    expect(invalidatesMe('transaction')).toBe(false)
    expect(invalidatesMe('unknown')).toBe(false)
  })
})

describe('ventures', () => {
  it('ventureInvalidatesVentures', () => {
    expect(invalidationPrefixes('venture')).toEqual([['ventures']])
    // The prefix is written out in invalidation.ts: it must stay the family-key tail of `all`.
    expect(venturesKeys.all('ws1').slice(2)).toEqual(['ventures'])
    expect(invalidationPrefixes('modules')).toContainEqual(['ventures'])
  })

  it('transactionInvalidatesVentures', () => {
    expect(invalidationPrefixes('transaction')).toContainEqual(['ventures'])
  })
})

describe('shoppingList', () => {
  it('shoppingListInvalidatesListCompareAndProducts', () => {
    expect(invalidationPrefixes('shopping-list')).toEqual([
      ['markets', 'list'],
      ['markets', 'compare'],
      ['markets', 'products'],
    ])
  })

  it('prefixesMatchTheRealMarketsKeys', () => {
    // The prefixes are written out in invalidation.ts: each must be the start of the real key, after `['ws', ws]`.
    const keys = [
      marketsKeys.list('ws1'),
      marketsKeys.compare('ws1', ['a', 'b']),
      marketsKeys.productsOverview('ws1'),
      marketsKeys.products('ws1', 'leite'),
    ]
    const prefixes = invalidationPrefixes('shopping-list')
    for (const key of keys) {
      const tail = key.slice(2)
      expect(prefixes.some((prefix) => prefix.every((part, i) => tail[i] === part))).toBe(true)
    }
    expect(marketsKeys.list('ws1')).toEqual(['ws', 'ws1', 'markets', 'list'])
    expect(marketsKeys.compare('ws1', ['a'])).toEqual(['ws', 'ws1', 'markets', 'compare', ['a']])
    // The overview sits under `products` but never equals a text search key.
    expect(marketsKeys.productsOverview('ws1')).toEqual([
      'ws',
      'ws1',
      'markets',
      'products',
      { view: 'overview' },
    ])
    expect(marketsKeys.productsOverview('ws1')).not.toEqual(marketsKeys.products('ws1', ''))
  })
})
