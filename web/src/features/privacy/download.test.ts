import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { downloadZip } from './download'

let names: string[] = []

beforeEach(() => {
  names = []
  URL.createObjectURL = vi.fn(() => 'blob:test')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    names.push(this.download)
  })
})

afterEach(() => vi.restoreAllMocks())

function respondWith(disposition: string | null) {
  server.use(
    http.post(`${API_URL}/v1/me/export`, () =>
      HttpResponse.text('zip', {
        headers: disposition === null ? {} : { 'Content-Disposition': disposition },
      }),
    ),
  )
}

describe('downloadZip', () => {
  it.each([
    ['attachment; filename="dados.zip"', 'dados.zip'],
    ['attachment; filename=dados.zip', 'dados.zip'],
    ["attachment; filename*=UTF-8''dados%20fam%C3%ADlia.zip", 'dados família.zip'],
    ['attachment; filename="../../etc/passwd"', 'passwd'],
    ['attachment; filename="..\\dados.zip"', 'dados.zip'],
    ['attachment; filename=".."', 'fallback.zip'],
    ["attachment; filename*=UTF-8''%E0%A4%A", 'fallback.zip'],
    ['attachment', 'fallback.zip'],
  ])('names the file for %s', async (disposition, expected) => {
    respondWith(disposition)
    await downloadZip('/v1/me/export', 'fallback.zip')
    expect(names).toEqual([expected])
  })

  it('usesTheFallbackWithoutTheHeader', async () => {
    respondWith(null)
    await downloadZip('/v1/me/export', 'fallback.zip')
    expect(names).toEqual(['fallback.zip'])
  })
})
