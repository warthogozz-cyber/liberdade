import { readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

export const CORE = [
  'auth',
  'onboarding',
  'transactions',
  'accounts',
  'categories',
  'dashboard',
  'members',
  'modules',
  'privacy',
  'billing',
  'portrait',
  'settings',
  'import',
]
export const OPTIONAL = ['planning', 'recurrences', 'indicators', 'gamification', 'ventures']

const ALL = [...CORE, ...OPTIONAL]

// An optional feature is cut off from the other features, with these exceptions: the recurring
// rules form is the transaction entry form's sibling, so it reuses that feature's fields.
const BUILDS_ON = { recurrences: ['transactions'] }

// Fails closed: every directory under src/features counts, listed or not, so a feature nobody added
// to CORE or OPTIONAL (or one that does not exist yet) is blocked for an optional feature too.
const ON_DISK = readdirSync(fileURLToPath(new URL('../src/features', import.meta.url)), {
  withFileTypes: true,
})
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name)

function otherFeatures(name) {
  const allowed = [name, ...(BUILDS_ON[name] ?? [])]
  return [...new Set([...ALL, ...ON_DISK])].filter((other) => !allowed.includes(other))
}

// Blocks relative imports that climb out of the current area into one of the
// given top-level folders. Known false positive: a sub-folder inside a feature
// named like another feature (e.g. features/planning/sub/a.ts importing
// ../ventures/x).
function relativeEscape(names) {
  return {
    regex: String.raw`^(\.\./)+(${names.join('|')})(/|$)`,
    message: 'Use the @/ alias to import across areas instead of climbing with ../',
  }
}

const AREAS = ['features', 'app', 'shared', 'mocks']

const FEATURES = ['@/features', '@/features/**']
const APP = ['@/app', '@/app/**']

const MOCKS = {
  group: ['@/mocks', '@/mocks/**'],
  message: 'Only main.tsx and tests may import the MSW mocks.',
}

function noRestrictedImports(patterns) {
  return { 'no-restricted-imports': ['error', { patterns }] }
}

// Flat config does not merge rule options across objects, so every file group
// carries its own complete list of patterns. A group's tests keep its patterns
// but may also import the MSW mocks.
function restrict(files, patterns) {
  const tests = files.map((glob) => glob.replace('*.{ts,tsx}', '*.test.{ts,tsx}'))
  return [
    { files, ignores: ['**/*.test.{ts,tsx}'], rules: noRestrictedImports([...patterns, MOCKS]) },
    { files: tests, rules: noRestrictedImports(patterns) },
  ]
}

export function boundaryConfigs() {
  return [
    restrict(
      ['src/shared/**/*.{ts,tsx}'],
      [
        {
          group: [...FEATURES, ...APP],
          message: 'Shared code cannot depend on features or the app layer.',
        },
        relativeEscape([...AREAS, ...ALL]),
      ],
    ),
    ...CORE.map((name) =>
      restrict(
        [`src/features/${name}/**/*.{ts,tsx}`],
        [
          {
            group: [
              ...OPTIONAL.flatMap((other) => [`@/features/${other}`, `@/features/${other}/**`]),
              ...APP,
            ],
            message: 'Core features cannot depend on optional features or the app layer.',
          },
          relativeEscape([...AREAS, ...ALL.filter((other) => other !== name)]),
        ],
      ),
    ),
    ...OPTIONAL.map((name) =>
      restrict(
        [`src/features/${name}/**/*.{ts,tsx}`],
        [
          {
            group: [
              ...otherFeatures(name).flatMap((other) => [
                `@/features/${other}`,
                `@/features/${other}/**`,
              ]),
              ...APP,
            ],
            message: `Optional feature "${name}" may only use @/shared and itself.`,
          },
          {
            regex: String.raw`^@/features/?$`,
            message: `Optional feature "${name}" may only use @/shared and itself.`,
          },
          relativeEscape([...AREAS, ...ALL.filter((other) => other !== name)]),
        ],
      ),
    ),
    restrict(['src/app/**/*.{ts,tsx}'], [relativeEscape([...AREAS, ...ALL])]),
    restrict(
      ['src/mocks/**/*.{ts,tsx}'],
      [
        {
          group: [...FEATURES, ...APP],
          message: 'MSW mocks cannot depend on features or the app layer.',
        },
        relativeEscape([...AREAS, ...ALL]),
      ],
    ),
  ].flat()
}
