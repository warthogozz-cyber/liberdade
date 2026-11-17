import { useIntl } from 'react-intl'
import { Link } from 'react-router'
import './labels.register'

const link =
  'flex min-h-12 items-center font-medium text-primary underline underline-offset-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The two legal pages as in-app links: the sign-in footer, More and the privacy center. */
export function LegalLinks({ className }: { className?: string }) {
  const intl = useIntl()
  return (
    <nav aria-label={intl.formatMessage({ id: 'legal.nav' })} className={className}>
      <Link to="/termos" className={link}>
        {intl.formatMessage({ id: 'legal.terms' })}
      </Link>
      <Link to="/privacidade" className={link}>
        {intl.formatMessage({ id: 'legal.privacy' })}
      </Link>
    </nav>
  )
}
