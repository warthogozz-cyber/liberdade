import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { gamificationMessages } from '@/features/gamification/messages.pt-BR'
import { indicatorsMessages } from '@/features/indicators/messages.pt-BR'
import { venturesMessages } from '@/features/ventures/messages.pt-BR'
import { LegalLinks } from '@/features/legal/LegalLinks'
import { WorkspaceSwitcher } from '@/features/members/WorkspaceSwitcher'
import { PasskeyButton } from '@/features/auth/PasskeyButton'
import { SignOutButton } from '@/features/auth/SignOutButton'
import { useCurrentWorkspace, useMe } from '@/shared/api'
import { registerMessages } from '@/shared/i18n'
import { useModuleEnabled } from '@/shared/modules'
import { useDocumentTitle } from '@/shared/ui'

// The Mais screen is lazy, so the entry's label loads here and not with the app.
registerMessages(indicatorsMessages)
registerMessages(gamificationMessages)
registerMessages(venturesMessages)

/** The person, their family, the passkey and sign-out. */
export function MorePage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'nav.more' }))
  const displayName = useMe().data?.user.displayName
  const workspace = useCurrentWorkspace()
  // A Youth sees only their own accounts, and the categories are the adults' to arrange.
  const youth = workspace?.role === 'YOUTH'
  const indicatorsOn = useModuleEnabled('indicators')
  const gamificationOn = useModuleEnabled('gamification')
  const venturesOn = useModuleEnabled('ventures')
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="nav.more" />
      </h1>
      <div>
        <p className="font-medium">{displayName}</p>
        <p className="text-text-muted">{workspace?.name}</p>
      </div>
      {!youth && (
        <>
          <Link
            to="/mais/contas"
            className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <FormattedMessage id="more.accounts" />
          </Link>
          <Link
            to="/mais/categorias"
            className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <FormattedMessage id="more.categories" />
          </Link>
        </>
      )}
      <Link
        to="/mais/familia"
        className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="members.title" />
      </Link>
      <Link
        to="/mais/modulos"
        className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="more.modules" />
      </Link>
      <Link
        to="/mais/plano"
        className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="more.plan" />
      </Link>
      {indicatorsOn && (
        <Link
          to="/mais/indicadores"
          className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <FormattedMessage id="more.indicators" />
        </Link>
      )}
      {gamificationOn && (
        <Link
          to="/mais/conquistas"
          className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <FormattedMessage id="gamification.title" />
        </Link>
      )}
      {venturesOn && !youth && (
        <Link
          to="/mais/projetos"
          className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <FormattedMessage id="more.ventures" />
        </Link>
      )}
      <Link
        to="/mais/privacidade"
        className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="more.privacy" />
      </Link>
      <Link
        to="/mais/ajustes"
        className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="more.settings" />
      </Link>
      <Link
        to="/painel"
        className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="more.dashboard" />
      </Link>
      <WorkspaceSwitcher />
      <PasskeyButton />
      <LegalLinks className="flex flex-col" />
      <SignOutButton />
    </main>
  )
}
