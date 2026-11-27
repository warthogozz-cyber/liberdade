export default {
  extends: ['@commitlint/config-conventional'],
  // Dependabot writes "chore(deps): Bump …" and its subject case cannot be configured.
  ignores: [(message) => /^chore\(deps(-dev)?\): Bump /.test(message)],
}
