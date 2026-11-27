import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { TagInput } from './TagInput'

afterEach(cleanup)

function Harness({ initial = [] }: { initial?: string[] }) {
  const [names, setNames] = useState(initial)
  return (
    <>
      <TagInput value={names} onChange={setNames} />
      <div data-testid="names">{JSON.stringify(names)}</div>
    </>
  )
}

const names = () => JSON.parse(screen.getByTestId('names').textContent ?? '[]') as string[]
const field = () => screen.getByRole('combobox', { name: 'Tags' })
const type = (text: string) => fireEvent.change(field(), { target: { value: text } })
const enter = () => fireEvent.keyDown(field(), { key: 'Enter' })

function renderField(initial?: string[], tags: string[] = ['Escola', 'Viagem']) {
  seedMockFamily()
  for (const name of tags) mockState.tags.push({ id: crypto.randomUUID(), name, archivedAt: null })
  return renderWithProviders(<Harness initial={initial} />)
}

describe('TagInput', () => {
  it('helpText', () => {
    renderField()

    const help = screen.getByText(
      'Tags são etiquetas livres. Use para achar gastos parecidos depois.',
    )
    expect(field()).toHaveAttribute('aria-describedby', help.id)
  })

  it('suggests the family tags that start with what is typed, ignoring case', async () => {
    renderField()

    type('esc')
    expect(await screen.findByRole('option', { name: 'Escola' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Viagem' })).not.toBeInTheDocument()
  })

  it('adds the typed name on Enter, and the suggestion with its own casing', async () => {
    renderField()

    type('  casa  ')
    enter()
    expect(names()).toEqual(['casa'])

    type('ESC')
    fireEvent.click(await screen.findByRole('option', { name: 'Escola' }))
    expect(names()).toEqual(['casa', 'Escola'])
    expect(field()).toHaveValue('')
  })

  it('adds on a comma and ignores empty and repeated names', () => {
    renderField(['Escola'], [])

    type('escola,')
    type(' ,')
    type('Casa,Lazer')
    expect(names()).toEqual(['Escola', 'Casa'])
    expect(field()).toHaveValue('Lazer')
  })

  it('refuses a name over 30 characters', () => {
    renderField()

    type('a'.repeat(31))
    enter()

    expect(names()).toEqual([])
    expect(screen.getByRole('status')).toHaveTextContent('A tag pode ter até 30 caracteres')
  })

  it('refuses the sixth name with "Até 5 tags"', () => {
    renderField(['a', 'b', 'c', 'd', 'e'], [])
    expect(screen.getByRole('status')).toHaveTextContent('Até 5 tags')

    type('f')
    enter()

    expect(names()).toEqual(['a', 'b', 'c', 'd', 'e'])
    expect(screen.getByRole('status')).toHaveTextContent('Até 5 tags')
  })

  it('removes a tag with its button', async () => {
    renderField(['Escola'])

    fireEvent.click(screen.getByRole('button', { name: 'Remover tag Escola' }))

    await waitFor(() => expect(names()).toEqual([]))
  })

  it('adds the pending text when the field loses focus', async () => {
    renderField(undefined, ['Escola'])

    type('escola')
    await screen.findByRole('option', { name: 'Escola' })
    fireEvent.blur(field())

    expect(names()).toEqual(['Escola'])
    expect(field()).toHaveValue('')
  })

  it('says why when the pending text is refused on blur', () => {
    renderField(['a', 'b', 'c', 'd', 'e'], [])

    type('f')
    fireEvent.blur(field())

    expect(names()).toEqual(['a', 'b', 'c', 'd', 'e'])
    expect(screen.getByRole('status')).toHaveTextContent('Até 5 tags')
    expect(field()).toHaveValue('f')
  })

  it('says why when a too long pending text is refused on blur', () => {
    renderField()

    type('a'.repeat(31))
    fireEvent.blur(field())

    expect(names()).toEqual([])
    expect(screen.getByRole('status')).toHaveTextContent('A tag pode ter até 30 caracteres')
  })
})
