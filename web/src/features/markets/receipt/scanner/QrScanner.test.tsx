import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import { IntlProvider } from 'react-intl'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { locale, useAppMessages } from '@/shared/i18n'
import QrScanner from './QrScanner'

const scan = vi.fn<() => Promise<string | null>>()
const dispose = vi.fn()

vi.mock('./detectQr', () => ({
  pickDetector: () => Promise.resolve('native'),
  createReader: () => ({ scan, dispose }),
}))

function Wrapped(props: Parameters<typeof QrScanner>[0]) {
  return (
    <IntlProvider locale={locale} messages={useAppMessages()}>
      <QrScanner {...props} />
    </IntlProvider>
  )
}

const stops = [vi.fn(), vi.fn()]
const getUserMedia = vi.fn()

beforeEach(() => {
  stops.forEach((stop) => stop.mockClear())
  scan.mockReset().mockResolvedValue(null)
  dispose.mockClear()
  getUserMedia.mockReset().mockResolvedValue({
    getTracks: () => stops.map((stop) => ({ stop })),
  })
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia },
  })
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
  vi.spyOn(HTMLMediaElement.prototype, 'readyState', 'get').mockReturnValue(4)
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

const mount = (onResult = vi.fn(), onUnavailable = vi.fn()) => {
  const view = render(<Wrapped onResult={onResult} onUnavailable={onUnavailable} />)
  return { ...view, onResult, onUnavailable }
}

describe('QrScanner', () => {
  it('asksForTheBackCamera', async () => {
    mount()
    await waitFor(() => expect(getUserMedia).toHaveBeenCalledTimes(1))
    expect(getUserMedia).toHaveBeenCalledWith({
      video: { facingMode: { ideal: 'environment' } },
      audio: false,
    })
  })

  it('stopsTracksOnResult', async () => {
    scan.mockResolvedValue('https://example.test/nota?p=chave-de-teste')
    const { onResult } = mount()
    await waitFor(() =>
      expect(onResult).toHaveBeenCalledWith('https://example.test/nota?p=chave-de-teste'),
    )
    stops.forEach((stop) => expect(stop).toHaveBeenCalled())
    expect(dispose).toHaveBeenCalled()
  })

  it('stopsTracksOnUnmount', async () => {
    const { unmount } = mount()
    await waitFor(() => expect(scan).toHaveBeenCalled())
    unmount()
    stops.forEach((stop) => expect(stop).toHaveBeenCalled())
  })

  it('stopsWhenHidden', async () => {
    mount()
    await waitFor(() => expect(scan).toHaveBeenCalled())
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'))
    })
    stops.forEach((stop) => expect(stop).toHaveBeenCalled())
    expect(screen.getByRole('alert')).toHaveTextContent(/câmera foi desligada/)
  })

  it('sendsOneFrameAtATime', async () => {
    vi.useFakeTimers()
    try {
      let release: (value: string | null) => void = () => undefined
      scan.mockImplementation(() => new Promise((resolve) => (release = resolve)))
      mount()
      await vi.advanceTimersByTimeAsync(1000)
      expect(scan).toHaveBeenCalledTimes(1)
      release(null)
      await vi.advanceTimersByTimeAsync(200)
      expect(scan).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('deniedShowsAlternatives', async () => {
    getUserMedia.mockRejectedValue(new DOMException('denied', 'NotAllowedError'))
    const { onUnavailable, onResult } = mount()
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Cole o link da nota ou digite a chave/,
    )
    expect(onUnavailable).toHaveBeenCalledTimes(1)
    expect(onResult).not.toHaveBeenCalled()
  })

  it('noCameraShowsAlternatives', async () => {
    getUserMedia.mockRejectedValue(new DOMException('none', 'NotFoundError'))
    const { onUnavailable } = mount()
    await screen.findByRole('alert')
    expect(onUnavailable).toHaveBeenCalledTimes(1)
  })
})
