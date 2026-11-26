import { FormattedMessage, useIntl } from 'react-intl'
import { NavLink, Outlet } from 'react-router'
import { useModuleEnabled } from '@/shared/modules'

const tab = 'min-w-max flex-1'

function linkClass({ isActive }: { isActive: boolean }) {
  return `flex min-h-14 flex-col items-center justify-center px-1 text-center text-xs leading-tight sm:text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
    isActive ? 'font-semibold text-primary' : 'text-text-muted'
  }`
}

/** The screens with the bottom navigation: Início, Lançamentos, (+) Novo gasto, Planejar (while the planning module is on) and Mais (D4, D21). */
export function AppShell() {
  const intl = useIntl()
  const planning = useModuleEnabled('planning')
  return (
    <div className="min-h-screen pb-20">
      <Outlet />
      <nav
        aria-label={intl.formatMessage({ id: 'nav.label' })}
        className="fixed inset-x-0 bottom-0 border-t border-text-muted/20 bg-surface"
      >
        {/* Equal tabs, except that a long label takes the room it needs instead of leaving its tab. */}
        <ul className="mx-auto flex max-w-lg">
          <li className={tab}>
            <NavLink to="/inicio" className={linkClass}>
              <FormattedMessage id="nav.home" />
            </NavLink>
          </li>
          <li className={tab}>
            <NavLink to="/lancamentos" className={linkClass}>
              <FormattedMessage id="nav.transactions" />
            </NavLink>
          </li>
          <li className={tab}>
            <NavLink to="/lancar" className={linkClass}>
              <span aria-hidden="true" className="text-xl leading-none">
                +
              </span>
              <FormattedMessage id="nav.newExpense" />
            </NavLink>
          </li>
          {planning && (
            <li className={tab}>
              <NavLink to="/planejar" className={linkClass}>
                <FormattedMessage id="nav.planning" />
              </NavLink>
            </li>
          )}
          <li className={tab}>
            <NavLink to="/mais" className={linkClass}>
              <FormattedMessage id="nav.more" />
            </NavLink>
          </li>
        </ul>
      </nav>
    </div>
  )
}
