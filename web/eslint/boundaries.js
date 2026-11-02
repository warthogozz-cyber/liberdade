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
]
export const OPTIONAL = ['planning', 'indicators', 'gamification', 'ventures']

const ALL = [...CORE, ...OPTIONAL]

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

const AREAS = ['features', 'app', 'shared']

const FEATURES = ['@/features', '@/features/**']
const APP = ['@/app', '@/app/**']

// Flat config does not merge rule options across objects, so every file group
// carries its own complete list of patterns.
function restrict(files, patterns) {
  return { files, rules: { 'no-restricted-imports': ['error', { patterns }] } }
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
            group: [...FEATURES, `!@/features/${name}`, `!@/features/${name}/**`, ...APP],
            message: `Optional feature "${name}" may only use @/shared and itself.`,
          },
          relativeEscape([...AREAS, ...ALL.filter((other) => other !== name)]),
        ],
      ),
    ),
    restrict(['src/app/**/*.{ts,tsx}'], [relativeEscape([...AREAS, ...ALL])]),
  ]
}
