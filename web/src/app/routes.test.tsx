import { render, screen } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { describe, expect, it } from 'vitest'
import { AppProviders } from '@/app/providers'
import { routes } from '@/app/routes'

describe('routes', () => {
  it('shows the not-found page with a link home for unknown paths', () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/nao-existe'] })

    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    )

    expect(screen.getByRole('heading', { name: 'Página não encontrada.' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Voltar ao início' })).toHaveAttribute('href', '/')
  })

  it('shows the home page at the root path', () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/'] })

    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    )

    expect(screen.getByRole('heading', { level: 1, name: 'Liberdade' })).toBeInTheDocument()
  })
})
