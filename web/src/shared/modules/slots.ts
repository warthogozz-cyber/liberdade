import type { ComponentType, LazyExoticComponent } from 'react'
import type { MemberRole, ModuleKey } from '@/shared/api'

/** Where a module may put a piece of its UI inside the core's screens. */
export type SlotName = 'home' | 'workspace'

export type SlotEntry = {
  key: string
  module: ModuleKey
  roles: readonly MemberRole[]
  Component: LazyExoticComponent<ComponentType>
}

const slots = new Map<SlotName, Map<string, SlotEntry>>()

/** Adds an entry to a slot; a second one with the same key replaces the first in place. */
export function registerSlot(slot: SlotName, entry: SlotEntry): void {
  const entries = slots.get(slot) ?? new Map<string, SlotEntry>()
  entries.set(entry.key, entry)
  slots.set(slot, entries)
}

/** The slot's entries, in the order they were registered. */
export function listSlot(slot: SlotName): SlotEntry[] {
  return [...(slots.get(slot)?.values() ?? [])]
}

/** Empties every slot. For tests. */
export function resetSlots(): void {
  slots.clear()
}
