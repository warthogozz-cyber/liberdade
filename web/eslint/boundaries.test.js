// @vitest-environment node
import { readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { ESLint } from 'eslint'
import tseslint from 'typescript-eslint'
import { describe, expect, it } from 'vitest'
import { boundaryConfigs, CORE, OPTIONAL } from './boundaries.js'

const web = fileURLToPath(new URL('..', import.meta.url))

const eslint = new ESLint({
  cwd: web,
  overrideConfigFile: true,
  overrideConfig: [{ languageOptions: { parser: tseslint.parser } }, ...boundaryConfigs()],
})

async function lint(file, spec) {
  const [result] = await eslint.lintText(`import x from '${spec}'\nexport default x\n`, {
    filePath: `${web}${file}`,
  })
  return result.messages.filter((m) => m.ruleId === 'no-restricted-imports').length
}

describe('module boundaries', () => {
  it('blocks an optional feature importing another optional feature', async () => {
    expect(await lint('src/features/planning/a.ts', '@/features/ventures/b')).toBe(1)
  })

  it('blocks a core feature importing an optional feature', async () => {
    expect(await lint('src/features/transactions/a.ts', '@/features/planning/b')).toBe(1)
  })

  it('lets an optional feature import the shared API', async () => {
    expect(await lint('src/features/planning/a.ts', '@/shared/api/transactions')).toBe(0)
  })

  it('blocks shared code importing a feature', async () => {
    expect(await lint('src/shared/ui/a.ts', '@/features/transactions/b')).toBe(1)
  })

  it('blocks an optional feature importing a feature that is in neither list, and the barrel', async () => {
    expect(await lint('src/features/planning/a.ts', '@/features/legal/x')).toBe(1)
    expect(await lint('src/features/planning/a.ts', '@/features')).toBe(1)
    expect(await lint('src/features/recurrences/a.ts', '@/features/accounts/b')).toBe(1)
  })

  it('has every src/features directory in CORE or OPTIONAL, except legal', () => {
    const known = new Set([...CORE, ...OPTIONAL, 'legal'])
    const dirs = readdirSync(`${web}src/features`, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
    expect(dirs.filter((dir) => !known.has(dir))).toEqual([])
  })

  it('lets the recurrences feature reuse the transaction fields, and nothing else', async () => {
    expect(await lint('src/features/recurrences/a.ts', '@/features/transactions/KindTabs')).toBe(0)
    expect(await lint('src/features/recurrences/a.ts', '@/features/accounts/b')).toBe(1)
    expect(await lint('src/features/transactions/a.ts', '@/features/recurrences/b')).toBe(1)
  })

  it('lets the app layer import any feature', async () => {
    expect(await lint('src/app/routes.tsx', '@/features/planning')).toBe(0)
  })

  it('blocks relative imports that climb across areas', async () => {
    expect(await lint('src/features/planning/a.ts', '../../features/ventures/b')).toBe(1)
  })

  it('lets a feature import shared code', async () => {
    expect(await lint('src/features/ventures/a.ts', '@/shared/money')).toBe(0)
  })

  it.each([
    ['src/features/transactions/a.ts', '../planning/b', 1],
    ['src/features/transactions/a.ts', '../accounts/b', 1],
    ['src/features/planning/a.ts', '../ventures/b', 1],
    ['src/features/planning/a.ts', '../ventures', 1],
    ['src/features/planning/sub/a.ts', '../../ventures/b', 1],
    ['src/features/planning/sub/a.ts', '../../planning/b', 0],
    ['src/features/planning/sub/a.ts', '../b', 0],
    ['src/features/planning/a.ts', '@a/shared/x', 0],
    ['src/shared/ui/a.ts', '../../features/auth/b', 1],
  ])('relative imports: %s importing %s yields %i', async (file, spec, expected) => {
    expect(await lint(file, spec)).toBe(expected)
  })

  it.each([
    ['src/features/transactions/a.ts', '@/mocks/server', 1],
    ['src/features/transactions/a.test.ts', '@/mocks/server', 0],
    ['src/features/transactions/a.test.ts', '@/features/planning/b', 1],
    ['src/shared/api/a.ts', '@/mocks/data', 1],
    ['src/app/a.tsx', '@/mocks/browser', 1],
    ['src/main.tsx', '@/mocks/browser', 0],
    ['src/mocks/handlers.ts', '@/features/auth', 1],
  ])('mocks and tests: %s importing %s yields %i', async (file, spec, expected) => {
    expect(await lint(file, spec)).toBe(expected)
  })
})
