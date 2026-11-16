import { Suspense, lazy } from 'react'
import { FormattedMessage } from 'react-intl'
import { useSearchParams } from 'react-router'
import { useCurrentWorkspace, useMe, type TransactionKind } from '@/shared/api'
import { useOutbox } from '@/shared/offline'
import { PageHeader, TransactionForm } from './TransactionForm'

// Editing needs the connection (ADR-0007) and is rarer than recording: it loads on demand.
const EditTransaction = lazy(() =>
  import('./EditTransaction').then(({ EditTransaction: page }) => ({ default: page })),
)

const KIND_PARAMS: Record<string, TransactionKind> = {
  gasto: 'EXPENSE',
  receita: 'INCOME',
  transferencia: 'TRANSFER',
}

/**
 * The new transaction, full screen (wireframe 3): Gasto, Receita or Transferência. `?tipo=receita` or
 * `?tipo=transferencia` opens on that tab and `?para=<account>` fills in where a transfer goes. With
 * `?corrigir=<id>`, it fixes a refused one.
 */
export function NewTransactionPage() {
  const userId = useMe().data?.user.id
  const workspace = useCurrentWorkspace()
  const [params] = useSearchParams()
  const fixId = params.get('corrigir')
  const editId = params.get('editar')
  const outbox = useOutbox(userId, workspace?.id)
  const fixing = outbox?.find((item) => item.id === fixId && item.status === 'needs-attention')
  // A fix waits for the queue, so the form opens filled in rather than filling in later.
  const ready = userId !== undefined && workspace && (fixId === null || outbox !== undefined)
  const initialKind = KIND_PARAMS[params.get('tipo') ?? ''] ?? 'EXPENSE'

  return (
    <main className="mx-auto flex min-h-screen max-w-lg flex-col gap-4 p-4">
      {editId !== null ? (
        workspace && userId ? (
          <Suspense fallback={<EditingFallback />}>
            <EditTransaction
              id={editId}
              month={params.get('mes')}
              userId={userId}
              workspace={workspace}
            />
          </Suspense>
        ) : (
          <EditingFallback />
        )
      ) : ready ? (
        <TransactionForm
          key={fixing?.id ?? 'new'}
          userId={userId}
          workspace={workspace}
          fixing={fixing}
          initialKind={initialKind}
          initialToAccountId={params.get('para')}
        />
      ) : (
        <>
          <PageHeader kind={initialKind} />
          <p>
            <FormattedMessage id="common.loading" />
          </p>
        </>
      )}
    </main>
  )
}

function EditingFallback() {
  return (
    <>
      <PageHeader kind="EXPENSE" editMonth="" />
      <p>
        <FormattedMessage id="common.loading" />
      </p>
    </>
  )
}
