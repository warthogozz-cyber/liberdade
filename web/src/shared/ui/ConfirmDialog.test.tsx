import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmDialog } from '@/shared/ui'

// Unmounting closes the dialog before the next test.
afterEach(cleanup)

function renderDialog() {
  const onConfirm = vi.fn<() => void>()
  const onCancel = vi.fn<() => void>()
  render(
    <ConfirmDialog
      open
      title="Sair deste aparelho?"
      body="Há 1 gasto aguardando sinal."
      confirmLabel="Sair mesmo assim"
      cancelLabel="Cancelar"
      onConfirm={onConfirm}
      onCancel={onCancel}
    />,
  )
  return { onConfirm, onCancel }
}

describe('ConfirmDialog', () => {
  it('opens with the focus on the cancel button', () => {
    renderDialog()

    expect(screen.getByRole('alertdialog', { name: 'Sair deste aparelho?' })).toHaveTextContent(
      'Há 1 gasto aguardando sinal.',
    )
    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus()
  })

  it('cancels on Escape', () => {
    const { onConfirm, onCancel } = renderDialog()

    fireEvent.keyDown(screen.getByRole('alertdialog'), { key: 'Escape' })

    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('confirms with the confirm button, without also cancelling', () => {
    const { onConfirm, onCancel } = renderDialog()

    fireEvent.click(screen.getByRole('button', { name: 'Sair mesmo assim' }))

    expect(onConfirm).toHaveBeenCalledTimes(1)
    expect(onCancel).not.toHaveBeenCalled()
  })
})
