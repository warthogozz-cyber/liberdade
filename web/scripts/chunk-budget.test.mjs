import { describe, expect, it } from 'vitest'
import { checkBudget, initialFiles } from './chunk-budget.mjs'

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
