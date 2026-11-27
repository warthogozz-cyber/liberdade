import { describe, expect, it } from 'vitest'
import { marketsMessages } from './messages.pt-BR'

describe('marketsMessages', () => {
  it('neverPushesAPurchase', () => {
    const pushy = Object.entries(marketsMessages).filter(([, text]) =>
      /barato|economi|melhor|vencedor/i.test(text),
    )

    expect(pushy).toEqual([])
  })
})
