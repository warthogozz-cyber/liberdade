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
    expectTypeOf<Schemas['TransactionView']['categoryId']>().toEqualTypeOf<string | null>()
    expectTypeOf<Schemas['TransactionView']['toAccountId']>().toEqualTypeOf<string | null>()
    expectTypeOf<Schemas['TransactionView']['accountId']>().toEqualTypeOf<string>()
    expectTypeOf<Schemas['TransactionView']['version']>().toEqualTypeOf<number>()
    expectTypeOf<Schemas['CategoryView']['icon']>().toEqualTypeOf<string | null>()
    expectTypeOf<Me['user']['passkeyCreatedAt']>().toEqualTypeOf<string | null>()
  })

  it('edits with every field but the destination, the category and the description', () => {
    type Update = Schemas['UpdateTransactionRequest']
    expectTypeOf<Update['version']>().toEqualTypeOf<number>()
    expectTypeOf<Update['accountId']>().toEqualTypeOf<string>()
    expectTypeOf<Update['occurredOn']>().toEqualTypeOf<string>()
    expectTypeOf<Update['toAccountId']>().toEqualTypeOf<string | null | undefined>()
    expectTypeOf<Update['categoryId']>().toEqualTypeOf<string | null | undefined>()
    expectTypeOf<Update['description']>().toEqualTypeOf<string | null | undefined>()
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
