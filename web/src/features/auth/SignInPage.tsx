import { FormattedMessage } from 'react-intl'

export function SignInPage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-2 p-6 text-center">
      <h1 className="text-[32px] font-semibold">
        <FormattedMessage id="home.title" />
      </h1>
      <p className="text-text-muted">
        <FormattedMessage id="home.tagline" />
      </p>
    </main>
  )
}
