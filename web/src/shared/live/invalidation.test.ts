import { describe, expect, it } from 'vitest'
import { invalidatesMe, invalidationPrefixes } from './invalidation'
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
