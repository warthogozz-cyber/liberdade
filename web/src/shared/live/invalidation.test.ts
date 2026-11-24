import { describe, expect, it } from 'vitest'
import { invalidationPrefixes } from './invalidation'

describe('invalidationPrefixes', () => {
  it.each([
    ['transaction', [['transactions'], ['dashboard'], ['accounts'], ['tags'], ['planning']]],
    ['account', [['accounts'], ['transactions'], ['dashboard'], ['planning']]],
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
