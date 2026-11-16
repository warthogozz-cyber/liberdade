import { describe, expect, it } from 'vitest'
import { checkBudget, composition, groupOf, initialFiles } from './chunk-budget.mjs'

const KB = 1024

function sizes(entries) {
  const map = new Map(Object.entries(entries).map(([file, kb]) => [file, kb * KB]))
  return (file) => map.get(file) ?? 0
}

describe('chunk budget', () => {
  it('followsStaticImportsOnly', () => {
    const manifest = {
      'index.html': {
        file: 'assets/index.js',
        isEntry: true,
        imports: ['a.js'],
        dynamicImports: ['c.js'],
        css: ['assets/index.css'],
      },
      'a.js': { file: 'assets/a.js', imports: ['b.js'] },
      'b.js': { file: 'assets/b.js' },
      'c.js': { file: 'assets/c.js', isDynamicEntry: true },
    }

    expect(initialFiles(manifest).sort()).toEqual(
      ['assets/index.js', 'assets/a.js', 'assets/b.js', 'assets/index.css'].sort(),
    )
  })

  it('sumsInitialLoad', () => {
    const manifest = {
      'index.html': { file: 'assets/index.js', isEntry: true, imports: ['a.js'] },
      'a.js': { file: 'assets/a.js' },
    }

    const result = checkBudget(manifest, sizes({ 'assets/index.js': 100, 'assets/a.js': 80 }))

    expect(result.ok).toBe(true)
    expect(result.rows).toEqual([{ file: 'initial load', kb: 180, limitKb: 200, initial: true }])
  })

  it('failsOverInitialLimit', () => {
    const manifest = {
      'index.html': { file: 'assets/index.js', isEntry: true, imports: ['a.js'] },
      'a.js': { file: 'assets/a.js' },
    }

    const result = checkBudget(manifest, sizes({ 'assets/index.js': 160, 'assets/a.js': 50 }))

    expect(result.ok).toBe(false)
  })

  it('failsOverChunkLimit', () => {
    const manifest = {
      'index.html': { file: 'assets/index.js', isEntry: true, dynamicImports: ['c.js'] },
      'c.js': { file: 'assets/c.js', isDynamicEntry: true },
    }

    const result = checkBudget(manifest, sizes({ 'assets/index.js': 10, 'assets/c.js': 270 }))

    expect(result.ok).toBe(false)
    expect(result.rows.find((row) => row.file === 'assets/c.js')).toEqual({
      file: 'assets/c.js',
      kb: 270,
      limitKb: 260,
      initial: false,
    })
  })

  it('ignoresSharedImportTwice', () => {
    const manifest = {
      'index.html': { file: 'assets/index.js', isEntry: true, imports: ['a.js', 'b.js'] },
      'a.js': { file: 'assets/a.js', imports: ['x.js'] },
      'b.js': { file: 'assets/b.js', imports: ['x.js'] },
      'x.js': { file: 'assets/x.js' },
    }

    const files = initialFiles(manifest)

    expect(files.filter((file) => file === 'assets/x.js')).toHaveLength(1)
    expect(files).toHaveLength(4)
  })
})

describe('composition', () => {
  it('groupsPnpmPackagesScopedPackagesAndSrcFolders', () => {
    expect(groupOf('C:/x/node_modules/.pnpm/react@19/node_modules/react/index.js')).toBe('react')
    expect(
      groupOf(String.raw`C:\x\node_modules\.pnpm\a@1\node_modules\@radix-ui\react-dialog\i.js`),
    ).toBe('@radix-ui/react-dialog')
    expect(groupOf('C:/x/web/src/features/ledger/Page.tsx')).toBe('src/features')
    expect(groupOf('C:/x/web/src/main.tsx')).toBe('src')
    expect(groupOf('vite/preload-helper.js')).toBe('(other)')
  })

  it('splitsGzipByRenderedSizeAndSkipsLazyChunks', () => {
    const manifest = {
      'index.html': { file: 'assets/index.js', isEntry: true, imports: ['v.js'] },
      'v.js': { file: 'assets/v.js' },
      'l.js': { file: 'assets/l.js', isDynamicEntry: true },
    }
    const modules = {
      'assets/index.js': { 'C:/w/src/app/App.tsx': 1024 },
      'assets/v.js': {
        'C:/n/node_modules/react/a.js': 3 * 1024,
        'C:/n/node_modules/dexie/b.js': 1024,
      },
      'assets/l.js': { 'C:/n/node_modules/echarts/c.js': 99 * 1024 },
    }

    const rows = composition(manifest, modules, sizes({ 'assets/index.js': 10, 'assets/v.js': 40 }))

    expect(rows).toEqual([
      { group: 'react', kind: 'package', renderedKb: 3, gzipKb: 30 },
      { group: 'dexie', kind: 'package', renderedKb: 1, gzipKb: 10 },
      { group: 'src/app', kind: 'src', renderedKb: 1, gzipKb: 10 },
    ])
  })
})
