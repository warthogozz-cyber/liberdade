import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { WorkspaceSwitcher } from '@/features/members/WorkspaceSwitcher'
import { PasskeyButton } from '@/features/auth/PasskeyButton'
import { SignOutButton } from '@/features/auth/SignOutButton'
import { useCurrentWorkspace, useMe } from '@/shared/api'
import { useDocumentTitle } from '@/shared/ui'

/** The person, their family, the passkey and sign-out. */
export function MorePage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'nav.more' }))
  const displayName = useMe().data?.user.displayName
  const workspace = useCurrentWorkspace()
  // A Youth sees only their own accounts, and the categories are the adults' to arrange.
  const youth = workspace?.role === 'YOUTH'
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
        to="/mais/privacidade"
        className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="more.privacy" />
      </Link>
      <Link
        to="/painel"
        className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="more.dashboard" />
      </Link>
      <WorkspaceSwitcher />
      <PasskeyButton />
      <SignOutButton />
    </main>
  )
}
