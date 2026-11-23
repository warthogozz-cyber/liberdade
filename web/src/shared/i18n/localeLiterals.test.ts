import { describe, expect, it } from 'vitest'

// The locale and the currency come from the family: only the defaults may be written as literals.
const files = import.meta.glob('/src/**/*.{ts,tsx}', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

const EXCLUDED = [
  /\.test\./,
  /^\/src\/mocks\//,
  /^\/src\/shared\/testing\//,
  /\/shared\/api\/schema\.ts$/,
]
const ALLOWED = ['/src/shared/i18n/index.ts', '/src/shared/money/money.ts']
const LITERAL = /(['"])(pt-BR|BRL)\1/

describe('locale and currency literals', () => {
  it('are only in the files that hold the defaults', () => {
    const offenders = Object.entries(files)
      .filter(([path]) => !EXCLUDED.some((rule) => rule.test(path)) && !ALLOWED.includes(path))
      .filter(([, source]) => LITERAL.test(source))
      .map(([path]) => path)
    expect(offenders).toEqual([])
  })
})
