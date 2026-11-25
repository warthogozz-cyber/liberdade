import { useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useId, useRef, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Navigate } from 'react-router'
import { v7 } from 'uuid'
import {
  useAccounts,
  useCategories,
  useCurrentWorkspace,
  type AccountView,
  type CategoryView,
  type MeWorkspace,
  type TransactionView,
} from '@/shared/api'
import { usePlan } from '@/shared/api/billing'
import { categoryHints } from '@/shared/api/imports'
import { categoriesQuery, transactionsQuery } from '@/shared/api/hooks'
import { addMonths, maxMonth, MIN_MONTH } from '@/shared/i18n'
import { useModuleEnabled } from '@/shared/modules'
import { useOnline } from '@/shared/offline'
import { PageTip } from '@/shared/tips'
import { useDocumentTitle } from '@/shared/ui'
import { ColumnMapping } from './ColumnMapping'
import { detectCsv, linesFromCsv, type CsvDetection, type CsvMapping } from './csvDetect'
import { applyStoredMapping, loadMapping, saveMapping } from './csvMappingStore'
import { ImportPreview } from './ImportPreview'
import { ImportResult } from './ImportResult'
import { importKeys } from './importKey'
import { assignKinds, type KindedLine } from './kinds'
import { normalizeDescription } from './normalize'
import { buildPreview, previewRange, type PreviewLine } from './preview'
import { parseCsv } from './readers/csv'
import { decode } from './readers/decode'
import { readOfx } from './readers/ofx'
import { StatementReadError, type StatementLine, type StatementReadReason } from './readers/types'
import { useImport, type ImportTotals } from './useImport'
import './register'

/** The largest file read (D11). */
const MAX_FILE_BYTES = 5 * 1024 * 1024
/** How much of the decoded file is looked at to tell an OFX from a CSV. */
const SNIFF_CHARS = 4096
/** The API takes at most this many hints per call and keys of at most this length. */
const HINT_BATCH = 500
const MAX_DESCRIPTION_LENGTH = 140

const field =
  'min-h-12 rounded-field border border-border bg-bg px-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

type FailReason = StatementReadReason | 'existing'

/** The account's existing transactions could not be fetched, so the preview could not tell repeats apart. */
class ExistingLoadError extends Error {}

/** A CSV in the preview: its rows, the mapping that read them and how many rows that mapping skipped. */
type CsvSource = { rows: string[][]; signature: string; mapping: CsvMapping; skipped: number }

type Step =
  | { kind: 'idle' }
  | { kind: 'reading' }
  | { kind: 'error'; reason: FailReason }
  | {
      kind: 'columns'
      rows: string[][]
      signature: string
      initial: CsvMapping
      fileKey: string
      /** Set when the person came back from this preview to adjust the columns; cancelling returns to it. */
      back: CsvSource | null
    }
  | { kind: 'preview'; csv: CsvSource | null }
  | { kind: 'result'; sent: PreviewLine[]; totals: ImportTotals }

/** The statement import (D14): account and file, the columns when needed, the preview and the result. */
export function ImportPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'import.title' })
  useDocumentTitle(title)
  const workspace = useCurrentWorkspace()
  const viewer = workspace?.role === 'VIEWER'
  // A Viewer never imports, so the plan is not even asked for.
  const plan = usePlan(workspace && !viewer ? workspace.id : undefined)

  if (workspace && (viewer || plan.isError || plan.data?.importAllowed === false)) {
    return <Navigate to="/lancamentos" replace />
  }
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      <PageTip id="import" />
      {workspace && plan.data ? (
        <ImportFlow workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

function ImportFlow({ workspace }: { workspace: MeWorkspace }) {
  const ws = workspace.id
  const queryClient = useQueryClient()
  const online = useOnline()
  const planningOn = useModuleEnabled('planning')
  const accountFieldId = useId()
  const fileFieldId = useId()
  // Loaded from the start, so the preview has its categories as soon as the file is read.
  const expense = useCategories(ws, 'EXPENSE').data
  const income = useCategories(ws, 'INCOME').data
  const accounts = (useAccounts(ws).data ?? []).filter((a) => a.archivedAt === null)
  const [chosenId, setChosenId] = useState<string | null>(null)
  const account = accounts.find((a) => a.id === chosenId) ?? accounts[0]
  const [file, setFile] = useState<File | null>(null)
  const [step, setStep] = useState<Step>({ kind: 'idle' })
  const [lines, setLines] = useState<PreviewLine[]>([])
  const [sending, setSending] = useState(false)
  // Remounts the file field, so the same file can be chosen again after an import.
  const [formKey, setFormKey] = useState(0)
  // Only the latest read may change the screen; an older one finishing late is ignored.
  const readCount = useRef(0)
  const { send } = useImport(ws, account?.id ?? '')

  async function preview(statement: StatementLine[], target: AccountView, csv: CsvSource | null) {
    const kinded = assignKinds(statement, target.type === 'CREDIT_CARD')
    if (kinded.length === 0) throw new StatementReadError('empty')
    const keys = await importKeys(target.id, kinded)
    const existing = await loadExisting(queryClient, workspace, target.id, kinded)
    const hints = await loadHints(ws, kinded)
    const usable = await usableCategories(queryClient, ws)
    const built = buildPreview(kinded, keys, existing, planningOn, v7).map((line) => {
      // A line that confirms a planned one keeps the planned category, which the API keeps too.
      if (line.confirmsPlannedId !== null) return line
      const hinted = hints.get(hintId(line.kind, hintKey(line.description))) ?? null
      // A hint for a category that is gone, archived or of the other kind is no suggestion at all.
      const categoryId = hinted !== null && usable.get(hinted) === line.kind ? hinted : null
      return { ...line, categoryId }
    })
    return { built, csv }
  }

  function previewCsv(
    rows: string[][],
    signature: string,
    mapping: CsvMapping,
    target: AccountView,
  ) {
    const { lines: statement, skipped } = linesFromCsv(rows, mapping)
    return preview(statement, target, { rows, signature, mapping, skipped })
  }

  async function run(work: () => Promise<Step | { built: PreviewLine[]; csv: CsvSource | null }>) {
    const mine = ++readCount.current
    setStep({ kind: 'reading' })
    try {
      const outcome = await work()
      if (mine !== readCount.current) return
      if ('built' in outcome) {
        setLines(outcome.built)
        setStep({ kind: 'preview', csv: outcome.csv })
      } else {
        setStep(outcome)
      }
    } catch (error) {
      if (mine !== readCount.current) return
      setStep({ kind: 'error', reason: failReason(error) })
    }
  }

  function read(chosen: File, target: AccountView) {
    void run(async () => {
      const source = await readStatement(chosen)
      if (source.kind === 'ofx') return preview(source.lines, target, null)
      const { rows, detection } = source
      const { signature } = detection
      const stored = loadMapping(target.id, signature)
      if (stored) {
        return previewCsv(rows, signature, applyStoredMapping(detection.mapping, stored), target)
      }
      if (!detection.certain) {
        const fileKey = `${chosen.name}-${chosen.size}-${chosen.lastModified}`
        return { kind: 'columns', rows, signature, initial: detection.mapping, fileKey, back: null }
      }
      return previewCsv(rows, signature, detection.mapping, target)
    })
  }

  function confirmColumns(rows: string[][], signature: string, mapping: CsvMapping) {
    if (!account) return
    const target = account
    // The mapping is saved for the account when the import starts, as for one detected or stored.
    void run(() => previewCsv(rows, signature, mapping, target))
  }

  /** Back from the preview to the columns, with the mapping in use; nothing changes until they confirm. */
  function adjustColumns(csv: CsvSource) {
    readCount.current++
    setStep({
      kind: 'columns',
      rows: csv.rows,
      signature: csv.signature,
      initial: csv.mapping,
      fileKey: `adjust-${readCount.current}`,
      back: csv,
    })
  }

  function chooseAccount(id: string) {
    setChosenId(id)
    const target = accounts.find((a) => a.id === id)
    if (file && target) read(file, target)
  }

  function chooseFile(chosen: File | undefined) {
    setFile(chosen ?? null)
    if (!chosen) {
      readCount.current++
      setStep({ kind: 'idle' })
      return
    }
    if (account) read(chosen, account)
  }

  async function startImport(csv: CsvSource | null) {
    if (!account) return
    if (csv) saveMapping(account.id, csv.signature, csv.mapping)
    const sent = lines.filter((line) => line.checked)
    setSending(true)
    try {
      const totals = await send(sent)
      setStep({ kind: 'result', sent, totals })
    } finally {
      setSending(false)
    }
  }

  async function retry(sent: PreviewLine[], before: ImportTotals) {
    const failedAfter = before.failedAfter ?? 0
    setSending(true)
    try {
      const again = await send(sent.slice(failedAfter))
      setStep({
        kind: 'result',
        sent,
        totals: {
          created: before.created + again.created,
          confirmed: before.confirmed + again.confirmed,
          skipped: before.skipped + again.skipped,
          failedAfter: again.failedAfter === null ? null : failedAfter + again.failedAfter,
          failure: again.failure,
        },
      })
    } finally {
      setSending(false)
    }
  }

  function another() {
    readCount.current++
    setFile(null)
    setLines([])
    setStep({ kind: 'idle' })
    setFormKey((key) => key + 1)
  }

  if (step.kind === 'result') {
    return (
      <ImportResult
        totals={step.totals}
        total={step.sent.length}
        online={online}
        retrying={sending}
        onRetry={() => void retry(step.sent, step.totals)}
        onAnother={another}
      />
    )
  }

  const formDisabled = !online || !account || sending
  const csvInPreview = step.kind === 'preview' ? step.csv : null
  return (
    <>
      <p>
        <FormattedMessage id="import.intro" />
      </p>
      <div key={formKey} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor={accountFieldId} className="font-medium">
            <FormattedMessage id="import.account" />
          </label>
          <select
            id={accountFieldId}
            value={account?.id ?? ''}
            disabled={formDisabled}
            onChange={(event) => chooseAccount(event.target.value)}
            className={field}
          >
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor={fileFieldId} className="font-medium">
            <FormattedMessage id="import.file" />
          </label>
          <input
            id={fileFieldId}
            type="file"
            accept=".ofx,.csv,.txt"
            disabled={formDisabled}
            onChange={(event) => chooseFile(event.target.files?.[0])}
            className={`${field} py-2`}
          />
        </div>
      </div>
      {!online && step.kind !== 'preview' && (
        <p role="status" className="rounded-card bg-surface p-4">
          <FormattedMessage id="import.offline" />
        </p>
      )}
      {step.kind === 'reading' && (
        <p role="status">
          <FormattedMessage id="import.reading" />
        </p>
      )}
      {step.kind === 'error' && (
        <p role="alert" className="text-negative">
          <FormattedMessage id={`import.error.${step.reason}`} />
        </p>
      )}
      {step.kind === 'columns' && (
        <ColumnMapping
          key={step.fileKey}
          rows={step.rows}
          initial={step.initial}
          onConfirm={(mapping) => confirmColumns(step.rows, step.signature, mapping)}
          onCancel={step.back ? () => setStep({ kind: 'preview', csv: step.back }) : undefined}
        />
      )}
      {step.kind === 'preview' && (!expense || !income) && (
        <p role="status">
          <FormattedMessage id="import.reading" />
        </p>
      )}
      {step.kind === 'preview' && expense && income && (
        <ImportPreview
          expense={expense}
          income={income}
          lines={lines}
          setLines={setLines}
          online={online}
          sending={sending}
          skipped={step.csv?.skipped ?? 0}
          onAdjustColumns={csvInPreview ? () => adjustColumns(csvInPreview) : undefined}
          onImport={() => void startImport(step.csv)}
        />
      )}
    </>
  )
}

type StatementSource =
  | { kind: 'ofx'; lines: StatementLine[] }
  | { kind: 'csv'; rows: string[][]; detection: CsvDetection }

/** Reads the file on the device: OFX when its head says so, CSV otherwise. Nothing of it is logged. */
async function readStatement(file: File): Promise<StatementSource> {
  if (file.size > MAX_FILE_BYTES) throw new StatementReadError('tooLarge')
  // Decoded first, so a UTF-16 file (whose bytes are full of NULs) is read as text.
  const text = decode(new Uint8Array(await file.arrayBuffer()))
  // Text statements never hold a NUL character; images, PDFs and spreadsheets do.
  if (text.includes('\0')) throw new StatementReadError('unreadable')
  if (/OFXHEADER|<OFX[\s>]/i.test(text.slice(0, SNIFF_CHARS))) {
    return { kind: 'ofx', lines: readOfx(text) }
  }

  const parsed = parseCsv(text)
  if (parsed.rows.length === 0) throw new StatementReadError('empty')
  if (parsed.rows.every((row) => row.length < 3)) throw new StatementReadError('unreadable')
  return { kind: 'csv', rows: parsed.rows, detection: detectCsv(parsed) }
}

/**
 * The account's transactions around the file's dates, planned ones included, through the same cache as the
 * list. Months outside what the API accepts are left out: nothing can exist there.
 */
async function loadExisting(
  queryClient: QueryClient,
  workspace: MeWorkspace,
  accountId: string,
  lines: KindedLine[],
): Promise<TransactionView[]> {
  const range = previewRange(lines)
  const last = maxMonth(new Date(), workspace.timezone)
  const from = range.from.slice(0, 7) < MIN_MONTH ? MIN_MONTH : range.from.slice(0, 7)
  const to = range.to.slice(0, 7) > last ? last : range.to.slice(0, 7)
  const months: string[] = []
  for (let month = from; month <= to; month = addMonths(month, 1)) months.push(month)
  try {
    const found = await Promise.all(
      months.map((month) =>
        queryClient.fetchQuery(transactionsQuery(workspace.id, month, { accountId })),
      ),
    )
    return found.flat()
  } catch {
    throw new ExistingLoadError()
  }
}

/**
 * The kind of every category a line may take: the family's active ones. When they cannot be fetched no hint is
 * kept, and the person picks every category.
 */
async function usableCategories(
  queryClient: QueryClient,
  ws: string,
): Promise<Map<string, CategoryView['kind']>> {
  const usable = new Map<string, CategoryView['kind']>()
  try {
    const lists = await Promise.all([
      queryClient.fetchQuery(categoriesQuery(ws, 'EXPENSE')),
      queryClient.fetchQuery(categoriesQuery(ws, 'INCOME')),
    ])
    for (const category of lists.flat()) {
      if (category.archivedAt === null) usable.set(category.id, category.kind)
    }
  } catch {
    // Without the list no hint can be trusted.
  }
  return usable
}

function hintKey(description: string): string {
  return normalizeDescription(description.trim().slice(0, MAX_DESCRIPTION_LENGTH))
}

function hintId(kind: string, key: string): string {
  return `${kind}|${key}`
}

/**
 * The category the family last used for each (kind, description). A suggestion only: when it cannot be
 * fetched the lines simply come without one.
 */
async function loadHints(ws: string, lines: KindedLine[]): Promise<Map<string, string>> {
  const items = new Map<string, { kind: KindedLine['kind']; key: string }>()
  for (const line of lines) {
    const key = hintKey(line.description)
    if (key !== '') items.set(hintId(line.kind, key), { kind: line.kind, key })
  }
  const unique = [...items.values()]
  const found = new Map<string, string>()
  try {
    for (let start = 0; start < unique.length; start += HINT_BATCH) {
      const batch = unique.slice(start, start + HINT_BATCH)
      const hints = await categoryHints(ws, batch)
      batch.forEach((item, i) => {
        const categoryId = hints[i]?.categoryId
        if (categoryId) found.set(hintId(item.kind, item.key), categoryId)
      })
    }
  } catch {
    // Without hints every line starts without a category.
  }
  return found
}

function failReason(error: unknown): FailReason {
  if (error instanceof StatementReadError) return error.reason
  if (error instanceof ExistingLoadError) return 'existing'
  return 'unreadable'
}
