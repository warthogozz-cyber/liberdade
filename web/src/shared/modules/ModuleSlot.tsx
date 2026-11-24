import { Component, Suspense, type ReactNode } from 'react'
import { useCurrentWorkspace } from '@/shared/api'
import { listSlot, type SlotEntry, type SlotName } from './slots'
import { useModuleEnabled } from './useModuleEnabled'

/** Renders nothing when its child throws, so one broken card never takes the page down. */
class SilentBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: unknown) {
    console.error('A module slot entry failed', error)
  }

  render() {
    return this.state.failed ? null : this.props.children
  }
}

function SlotItem({ entry }: { entry: SlotEntry }) {
  const workspace = useCurrentWorkspace()
  const enabled = useModuleEnabled(entry.module)
  if (!enabled || !workspace || !entry.roles.includes(workspace.role)) return null
  const { Component: Entry } = entry
  return (
    <SilentBoundary>
      <Suspense fallback={null}>
        <Entry />
      </Suspense>
    </SilentBoundary>
  )
}

/**
 * The entries modules registered for `name`, each shown only while its module is on for the family
 * and the person's role is one it lists. Entries load on demand and fail on their own.
 */
export function ModuleSlot({ name }: { name: SlotName }) {
  return (
    <>
      {listSlot(name).map((entry) => (
        <SlotItem key={entry.key} entry={entry} />
      ))}
    </>
  )
}
