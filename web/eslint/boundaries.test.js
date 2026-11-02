// @vitest-environment node
import { fileURLToPath } from 'node:url'
import { ESLint } from 'eslint'
import tseslint from 'typescript-eslint'
import { describe, expect, it } from 'vitest'
import { boundaryConfigs } from './boundaries.js'

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

  it('lets the app layer import any feature', async () => {
    expect(await lint('src/app/routes.tsx', '@/features/planning')).toBe(0)
  })

  it('blocks relative imports that climb across areas', async () => {
    expect(await lint('src/features/planning/a.ts', '../../features/ventures/b')).toBe(1)
  })

  it('lets a feature import shared code', async () => {
    expect(await lint('src/features/ventures/a.ts', '@/shared/money')).toBe(0)
  })
})
