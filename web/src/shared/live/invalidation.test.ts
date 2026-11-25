import { describe, expect, it } from 'vitest'
import { invalidatesMe, invalidationPrefixes } from './invalidation'

describe('invalidationPrefixes', () => {
  it.each([
    [
      'transaction',
      [['transactions'], ['dashboard'], ['accounts'], ['tags'], ['planning'], ['gamification']],
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
    ['modules', [['modules'], ['planning']]],
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
