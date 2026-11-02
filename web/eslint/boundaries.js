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

const RELATIVE_ESCAPE = {
  regex: '^(\.\./)+(features|app|shared)/',
  message: 'Use the @/ alias to import across areas instead of climbing with ../',
}

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
        RELATIVE_ESCAPE,
      ],
    ),
    restrict(
      CORE.map((name) => `src/features/${name}/**/*.{ts,tsx}`),
      [
        {
          group: [
            ...OPTIONAL.flatMap((name) => [`@/features/${name}`, `@/features/${name}/**`]),
            ...APP,
          ],
          message: 'Core features cannot depend on optional features or the app layer.',
        },
        RELATIVE_ESCAPE,
      ],
    ),
    ...OPTIONAL.map((name) =>
      restrict(
        [`src/features/${name}/**/*.{ts,tsx}`],
        [
          {
            group: [...FEATURES, `!@/features/${name}`, `!@/features/${name}/**`, ...APP],
            message: `Optional feature "${name}" may only use @/shared and itself.`,
          },
          RELATIVE_ESCAPE,
        ],
      ),
    ),
    restrict(['src/app/**/*.{ts,tsx}'], [RELATIVE_ESCAPE]),
  ]
}
