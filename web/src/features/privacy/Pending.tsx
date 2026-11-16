import type { MeWorkspace } from '@/shared/api'
import { PendingDeletionPage } from './PendingDeletionPage'
import { PendingFamilyCard } from './PendingFamilyCard'

/** The family's card when given a family, otherwise the account's page. */
export default function Pending({ workspace }: { workspace?: MeWorkspace }) {
  return workspace ? <PendingFamilyCard workspace={workspace} /> : <PendingDeletionPage />
}
