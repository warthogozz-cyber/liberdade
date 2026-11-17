import type { ReactNode } from 'react'
import { useIntl } from 'react-intl'

const style =
  'font-medium text-primary underline underline-offset-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/**
 * A link to the terms or the policy that opens in a new tab, so a screen with something half done
 * (an invitation, the acceptance) is not left behind. The hint is for screen readers.
 */
export function LegalLink({
  to,
  children,
}: {
  to: '/termos' | '/privacidade'
  children: ReactNode
}) {
  const intl = useIntl()
  return (
    <a href={to} target="_blank" rel="noopener noreferrer" className={style}>
      {children}
      <span className="sr-only"> {intl.formatMessage({ id: 'legal.opensInNewTab' })}</span>
    </a>
  )
}

/** The "by accepting you agree to the terms and the policy" sentence, with both links. */
export function AgreeNotice({ className }: { className?: string }) {
  const intl = useIntl()
  return (
    <p className={className}>
      {intl.formatMessage(
        { id: 'legal.agree' },
        {
          terms: (chunks) => <LegalLink to="/termos">{chunks}</LegalLink>,
          privacy: (chunks) => <LegalLink to="/privacidade">{chunks}</LegalLink>,
        },
      )}
    </p>
  )
}
