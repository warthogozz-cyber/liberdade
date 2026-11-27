import type { RouteObject } from 'react-router'
import { describe, expect, it } from 'vitest'
import { marketsRoutePaths } from '@/features/markets/MarketsRoutes'
import { venturesRoutePaths } from '@/features/ventures/VenturesRoutes'
import { messages as baseMessages } from '@/shared/i18n/messages.pt-BR'
import { routeHelp } from '@/shared/help/catalog'
import { routes } from './routes'

const BANNED_LOSS = /perd|perca|resta|expir|quebr|corra|última chance|não deixe|falta/i
const INLINE_LIMIT = 280

// Screens that hold their own inner router: the app routes name them with a trailing `/*`.
const innerPaths: Record<string, readonly string[]> = {
  '/mais/projetos': venturesRoutePaths,
  '/mais/feira': marketsRoutePaths,
}

function collect(list: RouteObject[], out: string[] = []): string[] {
  for (const route of list) {
    if (route.path !== undefined) {
      if (route.path.endsWith('/*') && route.path !== '*') {
        const base = route.path.slice(0, -2)
        const inner = innerPaths[base]
        if (inner === undefined) throw new Error(`No inner paths declared for ${route.path}`)
        out.push(base, ...inner.map((path) => `${base}/${path}`))
      } else {
        out.push(route.path)
      }
    }
    if (route.children) collect(route.children, out)
  }
  return out
}

const allPaths = collect(routes)

const sources = import.meta.glob('/src/features/**/*.tsx', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>
const screenSources = Object.entries(sources).filter(([file]) => !/\.test\.tsx$/.test(file))

const bundles = import.meta.glob('/src/features/**/messages.pt-BR.ts', {
  eager: true,
}) as Record<string, Record<string, unknown>>
const registered: Record<string, string> = { ...(baseMessages as Record<string, string>) }
for (const module of Object.values(bundles)) {
  for (const value of Object.values(module)) {
    if (typeof value === 'object' && value !== null) {
      for (const [id, text] of Object.entries(value)) {
        if (typeof text === 'string') registered[id] = text
      }
    }
  }
}

describe('help coverage', () => {
  it('everyRouteHasHelp', () => {
    expect(allPaths.length).toBeGreaterThan(30)
    const missing = allPaths.filter((path) => !(path in routeHelp))
    expect(missing, `Routes without an entry in routeHelp:\n${missing.join('\n')}`).toEqual([])
  })

  it('noStaleEntries', () => {
    const known = new Set(allPaths)
    const stale = Object.keys(routeHelp).filter((path) => !known.has(path))
    expect(stale, `routeHelp keys that are not routes:\n${stale.join('\n')}`).toEqual([])
  })

  it('inheritsPointToHelpedRoutes', () => {
    for (const [path, help] of Object.entries(routeHelp)) {
      if (!('inherits' in help)) continue
      const target = routeHelp[help.inherits]
      expect(target, `${path} inherits ${help.inherits}, which has no entry`).toBeDefined()
      const helped =
        target !== undefined && ('tip' in target || 'tour' in target || 'inline' in target)
      expect(helped, `${path} inherits ${help.inherits}, which has no tip, tour or inline`).toBe(
        true,
      )
    }
  })

  it('tipsAndToursRendered', () => {
    for (const [path, help] of Object.entries(routeHelp)) {
      const needle =
        'tip' in help
          ? `<PageTip id="${help.tip}"`
          : 'tour' in help
            ? `<PageTour id="${help.tour}"`
            : null
      if (needle === null) continue
      const found = screenSources.some(([, source]) => source.includes(needle))
      expect(found, `${path}: no screen renders ${needle}`).toBe(true)
    }
  })

  it('noScreenWithTipAndTour', () => {
    const both = screenSources
      .filter(([, source]) => source.includes('<PageTip') && source.includes('<PageTour'))
      .map(([file]) => file)
    expect(both, `Files that render a tip and a tour:\n${both.join('\n')}`).toEqual([])
  })

  it('inlineIdsExistAndFit', () => {
    for (const [path, help] of Object.entries(routeHelp)) {
      if (!('inline' in help)) continue
      const text = registered[help.inline]
      expect(text, `${path}: message ${help.inline} is not registered`).toBeDefined()
      expect(text?.length, `${help.inline} is over ${INLINE_LIMIT} characters`).toBeLessThanOrEqual(
        INLINE_LIMIT,
      )
      expect(text, `${help.inline} reads as loss`).not.toMatch(BANNED_LOSS)
    }
  })
})
