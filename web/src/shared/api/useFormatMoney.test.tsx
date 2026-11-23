import { waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { renderHookWithProviders } from '@/shared/testing'
import { useFormatMoney, useLocale } from './useFormatMoney'

const NBSP = ' '

describe('useFormatMoney', () => {
  it('without a family, writes in pt-BR and BRL', () => {
    const { result } = renderHookWithProviders(() => ({
      format: useFormatMoney(),
      locale: useLocale(),
    }))
    expect(result.current.format(1000n)).toBe(`R$${NBSP}10,00`)
    expect(result.current.locale).toBe('pt-BR')
  })

  it('with a pt-BR/BRL family, writes the same', async () => {
    seedMockFamily()
    const { result } = renderHookWithProviders(() => ({
      format: useFormatMoney(),
      locale: useLocale(),
    }))
    await waitFor(() => expect(result.current.locale).toBe('pt-BR'))
    expect(result.current.format(1000n)).toBe(`R$${NBSP}10,00`)
  })
})
