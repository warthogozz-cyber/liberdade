import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Disclaimer } from '@/shared/ui/Disclaimer'

afterEach(cleanup)

describe('Disclaimer', () => {
  it('isANote', () => {
    render(<Disclaimer>Conteúdo educativo.</Disclaimer>)
    expect(screen.getByRole('note').textContent).toBe('Conteúdo educativo.')
  })
})
