import { act, cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/shared/testing'
import { ReceiptKeyStep } from './ReceiptKeyStep'
import { pickDetector, type DetectorKind } from './scanner/detectQr'

const mounts = vi.hoisted(() => ({ count: 0, broken: false }))

vi.mock('./scanner/detectQr', () => ({ pickDetector: vi.fn() }))
vi.mock('./scanner/QrScanner', async () => {
  const { useEffect } = await import('react')
  return {
    default: function FakeScanner() {
      useEffect(() => {
        mounts.count += 1
      }, [])
      // Stands for a reader chunk that failed to load or broke while opening.
      if (mounts.broken) throw new Error('reader chunk failed')
      return <p>Leitor aberto</p>
    },
  }
})

const pick = vi.mocked(pickDetector)

function renderStep() {
  return renderWithProviders(
    <ReceiptKeyStep pending={false} error={null} onParsed={() => undefined} />,
  )
}

beforeEach(() => {
  mounts.count = 0
  mounts.broken = false
})
afterEach(() => cleanup())

describe('ReceiptKeyStep reader', () => {
  it('hidesReadQrWhenNoDetector', async () => {
    let settle: (kind: DetectorKind) => void = () => undefined
    pick.mockReturnValue(new Promise((resolve) => (settle = resolve)))
    renderStep()

    // Not offered while the browser is still being asked.
    expect(screen.queryByRole('button', { name: 'Ler o QR' })).toBeNull()
    await act(async () => settle('none'))
    expect(screen.queryByRole('button', { name: 'Ler o QR' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Colar o link' })).toBeInTheDocument()
  })

  it('showsReadQrWithNativeDetector', async () => {
    pick.mockResolvedValue('native')
    renderStep()
    expect(await screen.findByRole('button', { name: 'Ler o QR' })).toBeInTheDocument()
  })

  it('mountsScannerOnlyAfterTap', async () => {
    pick.mockResolvedValue('native')
    renderStep()
    const button = await screen.findByRole('button', { name: 'Ler o QR' })
    expect(screen.queryByText('Leitor aberto')).toBeNull()
    expect(mounts.count).toBe(0)

    fireEvent.click(button)
    expect(await screen.findByText('Leitor aberto')).toBeInTheDocument()
    expect(mounts.count).toBe(1)
  })

  it('retapRemountsScannerAfterPause', async () => {
    pick.mockResolvedValue('worker')
    renderStep()
    fireEvent.click(await screen.findByRole('button', { name: 'Ler o QR' }))
    await screen.findByText('Leitor aberto')
    expect(mounts.count).toBe(1)

    fireEvent.click(screen.getByRole('button', { name: 'Ler o QR' }))
    await screen.findByText('Leitor aberto')
    expect(mounts.count).toBe(2)
  })
})

describe('ReceiptKeyStep broken reader', () => {
  it('brokenReaderFallsBackToPaste', async () => {
    pick.mockResolvedValue('native')
    mounts.broken = true
    const quiet = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    renderStep()

    fireEvent.click(await screen.findByRole('button', { name: 'Ler o QR' }))

    expect(
      await screen.findByText('Não deu para abrir o leitor. Cole o link ou digite a chave.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Link da nota')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Digitar a chave' })).toBeInTheDocument()
    quiet.mockRestore()
  })
})

describe('ReceiptKeyStep key field', () => {
  beforeEach(() => pick.mockResolvedValue('none'))

  it('keyFieldIsNumericByDefault', () => {
    renderStep()
    fireEvent.click(screen.getByRole('button', { name: 'Digitar a chave' }))

    expect(screen.getByLabelText('Chave de 44 números')).toHaveAttribute('inputmode', 'numeric')
  })

  it('lettersButtonSwitchesToTextKeepingValue', () => {
    renderStep()
    fireEvent.click(screen.getByRole('button', { name: 'Digitar a chave' }))
    const input = screen.getByLabelText('Chave de 44 números')
    fireEvent.change(input, { target: { value: '4326' } })

    fireEvent.click(screen.getByRole('button', { name: 'A chave tem letras' }))
    expect(input).toHaveAttribute('inputmode', 'text')
    expect(input).toHaveAttribute('autocapitalize', 'characters')
    expect(input).toHaveValue('4326')

    fireEvent.click(screen.getByRole('button', { name: 'Só números' }))
    expect(input).toHaveAttribute('inputmode', 'numeric')
    expect(input).toHaveValue('4326')
  })
})
