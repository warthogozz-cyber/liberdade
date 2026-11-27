import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/shared/testing'
import { ImportResult } from './ImportResult'
import type { ImportTotals } from './useImport'

afterEach(cleanup)

const totals = (over: Partial<ImportTotals>): ImportTotals => ({
  created: 0,
  confirmed: 0,
  skipped: 0,
  failedAfter: null,
  failure: null,
  ...over,
})

function show(t: ImportTotals) {
  renderWithProviders(
    <ImportResult
      totals={t}
      total={7}
      online
      retrying={false}
      onRetry={vi.fn()}
      onAnother={vi.fn()}
    />,
  )
}

describe('ImportResult', () => {
  it('threeLines', async () => {
    show(totals({ created: 5, confirmed: 2, skipped: 0 }))

    expect(await screen.findByText('Entraram 5.')).toBeInTheDocument()
    expect(screen.getByText('Confirmaram previstos: 2.')).toBeInTheDocument()
    expect(screen.queryByText(/Já existiam/)).not.toBeInTheDocument()
  })

  it('singularCreated', async () => {
    show(totals({ created: 1 }))

    expect(await screen.findByText('Entrou 1.')).toBeInTheDocument()
  })

  it('showsOnlyTheSkippedLine', async () => {
    show(totals({ skipped: 3 }))

    expect(await screen.findByText('Já existiam: 3.')).toBeInTheDocument()
    expect(screen.queryByText(/Entraram/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Confirmaram/)).not.toBeInTheDocument()
  })
})
