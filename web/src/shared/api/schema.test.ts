import { describe, expectTypeOf, it } from 'vitest'
import type { components, paths } from './schema'

// Type-level checks: tsc -b fails when they break, and Vitest only runs the file. A field that
// became optional by mistake turns into `… | undefined` and breaks the exact match.
type Schemas = components['schemas']
type Me = paths['/v1/me']['get']['responses'][200]['content']['application/json']
type Transactions = paths['/v1/workspaces/{ws}/transactions']

describe('generated API types', () => {
  it('keeps money amounts as text', () => {
    expectTypeOf<Schemas['Money']['amount']>().toEqualTypeOf<string>()
  })

  it('marks only the nullable fields as null', () => {
    expectTypeOf<Schemas['TransactionView']['description']>().toEqualTypeOf<string | null>()
    expectTypeOf<Schemas['TransactionView']['categoryId']>().toEqualTypeOf<string>()
    expectTypeOf<Schemas['CategoryView']['icon']>().toEqualTypeOf<string | null>()
    expectTypeOf<Me['user']['passkeyCreatedAt']>().toEqualTypeOf<string | null>()
  })

  it('takes the family id in the path', () => {
    type Accounts = paths['/v1/workspaces/{ws}/accounts']['get']
    expectTypeOf<Accounts['parameters']['path']>().toEqualTypeOf<{ ws: string }>()
    expectTypeOf<Transactions['post']['parameters']['path']>().toEqualTypeOf<{ ws: string }>()
  })

  it('answers a recorded transaction with 201, and a resend with 200', () => {
    type Responses = Transactions['post']['responses']
    expectTypeOf<Responses[201]['content']['application/json']>().toEqualTypeOf<
      Schemas['TransactionView']
    >()
    expectTypeOf<Responses[200]['content']['application/json']>().toEqualTypeOf<
      Schemas['TransactionView']
    >()
  })
})
