import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { IntlProvider } from 'react-intl'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, ReauthCancelledError } from '@/shared/api'
import { locale, useAppMessages } from '@/shared/i18n'
import { createTestQueryClient } from '@/shared/testing'
import { ReauthProvider } from './ReauthProvider'
import { useReauth } from './useReauth'

const dialog = vi.hoisted(() => ({ broken: true }))

// A dialog that fails as the chunk would: the lazy component throws instead of rendering.
vi.mock('./ReauthDialog', () => ({
  ReauthDialog: () => {
    if (dialog.broken) throw new Error('chunk failed to load')
    return null
  },
}))

afterEach(() => {
  cleanup()
  dialog.broken = true
})

function Providers({ children }: { children: ReactNode }) {
  return (
    <IntlProvider locale={locale} messages={useAppMessages()}>
      <QueryClientProvider client={createTestQueryClient()}>
        <ReauthProvider>{children}</ReauthProvider>
      </QueryClientProvider>
    </IntlProvider>
  )
}

function setup() {
  let reauth!: ReturnType<typeof useReauth>
  function Probe() {
    // eslint-disable-next-line react-hooks/globals -- the test reads the hook from outside
    reauth = useReauth()
    return <p>app still here</p>
  }
  const view = render(<Probe />, { wrapper: Providers })
  return {
    view,
    run: () => reauth(() => Promise.reject(new ApiError(403, 'auth.reauthentication-required'))),
  }
}

describe('ReauthProvider', () => {
  it('rejects the action and keeps the app up when the dialog cannot load', async () => {
    const errors = vi.spyOn(console, 'error').mockImplementation(() => {})
    const { view, run } = setup()

    await expect(run()).rejects.toThrow('chunk failed to load')

    expect(view.getByText('app still here')).toBeInTheDocument()
    errors.mockRestore()
  })

  it('rejects a waiting request with ReauthCancelledError when it unmounts', async () => {
    dialog.broken = false
    const { view, run } = setup()
    const rejected = expect(run()).rejects.toBeInstanceOf(ReauthCancelledError)
    // The request is made once the action's first refusal has been handled.
    await new Promise((resolve) => setTimeout(resolve, 0))

    view.unmount()

    await rejected
  })
})
