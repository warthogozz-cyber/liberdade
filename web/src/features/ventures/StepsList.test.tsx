import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, prdExampleVenture, seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { server } from '@/mocks/server'
import type { MeWorkspace } from '@/shared/api'
import { useVenture } from '@/shared/api/ventures'
import { renderWithProviders } from '@/shared/testing'
import { StepsList } from './StepsList'

afterEach(cleanup)

function Harness({
  workspace,
  id,
  canWrite,
}: {
  workspace: MeWorkspace
  id: string
  canWrite: boolean
}) {
  const venture = useVenture(workspace.id, id)
  if (!venture.data) return null
  return <StepsList workspace={workspace} venture={venture.data} canWrite={canWrite} />
}

function render(canWrite = true) {
  const workspace = seedMockFamily()
  const venture = prdExampleVenture()
  seedVentureFixture(workspace, venture)
  renderWithProviders(<Harness workspace={workspace} id={venture.id} canWrite={canWrite} />)
  return { workspace, venture }
}

function patches() {
  const bodies: unknown[] = []
  server.events.on('request:start', async ({ request }) => {
    if (request.method === 'PATCH') bodies.push(await request.clone().json())
  })
  return bodies
}

describe('StepsList', () => {
  it('helpText', async () => {
    render()

    expect(
      await screen.findByText('Liste os próximos passos. Marque como Feito ao concluir.'),
    ).toBeInTheDocument()
  })

  it('moveWithButtons', async () => {
    const bodies = patches()
    render()

    fireEvent.click(
      await screen.findByRole('button', { name: 'Mover para cima: Definir os preços' }),
    )

    await waitFor(() => expect(bodies).toEqual([{ position: 0 }]))
    await waitFor(() =>
      expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
        expect.stringContaining('Definir os preços'),
        expect.stringContaining('Testar a receita'),
        expect.stringContaining('Fazer a primeira venda'),
      ]),
    )
  })

  it('keyboardOnly', async () => {
    const bodies = patches()
    render()

    const check = await screen.findByRole('checkbox', { name: 'Testar a receita' })
    check.focus()
    fireEvent.click(check)
    await waitFor(() => expect(bodies).toEqual([{ done: true }]))

    const rename = screen.getByRole('button', { name: 'Renomear Definir os preços' })
    rename.focus()
    fireEvent.click(rename)
    const field = screen.getByRole('textbox', { name: 'Nome do passo' })
    expect(field).toHaveFocus()
    fireEvent.change(field, { target: { value: 'Calcular os preços' } })
    fireEvent.submit(field)
    await waitFor(() => expect(bodies).toContainEqual({ title: 'Calcular os preços' }))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Renomear Calcular os preços' })).toHaveFocus(),
    )

    const down = screen.getByRole('button', { name: 'Mover para baixo: Calcular os preços' })
    down.focus()
    fireEvent.click(down)
    await waitFor(() => expect(bodies).toContainEqual({ position: 2 }))
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Mover para baixo: Calcular os preços' }),
      ).toHaveFocus(),
    )
    const items = screen.getAllByRole('listitem')
    expect(within(items[2] as HTMLElement).getByText('Calcular os preços')).toBeVisible()
  })

  it('removeMovesFocusToTheNextStep', async () => {
    render()

    fireEvent.click(await screen.findByRole('button', { name: 'Remover passo: Testar a receita' }))

    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: 'Definir os preços' })).toHaveFocus(),
    )
  })

  it('removingTheLastStepFocusesTheNewStepField', async () => {
    const { workspace, venture } = render()
    const stored = mockState.ventures[workspace.id]?.find((each) => each.id === venture.id)
    if (stored) stored.steps = stored.steps.slice(0, 1)
    fireEvent.click(await screen.findByRole('button', { name: 'Remover passo: Testar a receita' }))

    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Novo passo' })).toHaveFocus())
  })

  it('addsAStepFromTheKeyboard', async () => {
    render()

    const field = await screen.findByRole('textbox', { name: 'Novo passo' })
    fireEvent.change(field, { target: { value: 'Divulgar' } })
    fireEvent.submit(field)

    expect(await screen.findByRole('checkbox', { name: 'Divulgar' })).toBeVisible()
    expect(field).toHaveValue('')
  })

  it('viewerSeesDoneAsText', async () => {
    render(false)

    expect(await screen.findByText('Testar a receita')).toBeVisible()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getAllByText('A fazer')).toHaveLength(3)
  })
})
