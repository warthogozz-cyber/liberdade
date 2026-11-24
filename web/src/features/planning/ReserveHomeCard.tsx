import { FormattedMessage } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import { useReserve } from '@/shared/api/planning'
import { ReserveSummary } from './ReserveSummary'
import './register'

/** The emergency reserve on Início: months covered, the bar and a link to the screen. */
export default function ReserveHomeCard() {
  const workspace = useCurrentWorkspace()
  const reserve = useReserve(workspace?.id ?? '')
  if (!reserve.data) return null

  return (
    <section className="flex flex-col gap-3 rounded-card bg-surface p-4">
      <h2 className="text-lg font-semibold">
        <FormattedMessage id="planning.reserve.homeTitle" />
      </h2>
      {reserve.data.months !== null ? (
        <ReserveSummary reserve={reserve.data} />
      ) : (
        <p className="text-text-muted">
          <FormattedMessage id="planning.reserve.askCost" />
        </p>
      )}
      {!reserve.data.hasReserveAccounts && (
        <p className="text-text-muted">
          <FormattedMessage id="planning.reserve.noAccounts" />
        </p>
      )}
      <Link
        to="/planejar/reserva"
        className="flex min-h-12 items-center font-medium text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="planning.reserve.open" />
      </Link>
    </section>
  )
}
