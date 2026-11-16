import { FormattedMessage, useIntl } from 'react-intl'
import { PasskeyButton } from '@/features/auth/PasskeyButton'
import { SignOutButton } from '@/features/auth/SignOutButton'
import { useCurrentWorkspace, useMe } from '@/shared/api'
import { useDocumentTitle } from '@/shared/ui'

/** The person, their family, the passkey and sign-out. */
export function MorePage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'nav.more' }))
  const displayName = useMe().data?.user.displayName
  const workspace = useCurrentWorkspace()
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="nav.more" />
      </h1>
      <div>
        <p className="font-medium">{displayName}</p>
        <p className="text-text-muted">{workspace?.name}</p>
      </div>
      <PasskeyButton />
      <SignOutButton />
    </main>
  )
}
