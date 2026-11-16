import { useRef, useState, type ReactNode } from 'react'
import { useIntl } from 'react-intl'
import { invitationLink } from './invitationToken'
import './register'

interface InvitationLinkProps {
  token: string
  /** What to tell about the link's life, such as "valid for 7 days, once". */
  hint: ReactNode
}

const button =
  'flex min-h-12 items-center justify-center rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

/** The link to hand over, with Copy and (where the browser has it) Share. */
export function InvitationLink({ token, hint }: InvitationLinkProps) {
  const intl = useIntl()
  const input = useRef<HTMLInputElement>(null)
  const [copy, setCopy] = useState<'copied' | 'by-hand'>()
  const link = invitationLink(token)
  const canShare = typeof navigator.share === 'function'

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(link)
      setCopy('copied')
    } catch {
      // No clipboard, or the browser refused: the person copies it by hand.
      input.current?.select()
      setCopy('by-hand')
    }
  }

  async function share() {
    try {
      await navigator.share({ url: link })
    } catch {
      // Closing the share sheet rejects; that is the person's choice, not an error.
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <input
        ref={input}
        readOnly
        value={link}
        aria-label={intl.formatMessage({ id: 'invite.link' })}
        onFocus={(event) => event.currentTarget.select()}
        className="min-h-12 w-full rounded-field border border-border bg-bg px-3 text-sm"
      />
      <p className="text-sm text-text-muted">{hint}</p>
      <div className="flex flex-col gap-2">
        <button
          type="button"
          onClick={() => void copyLink()}
          className={`${button} bg-primary text-on-primary`}
        >
          {intl.formatMessage({ id: 'invite.copy' })}
        </button>
        {canShare && (
          <button
            type="button"
            onClick={() => void share()}
            className={`${button} border border-primary text-primary`}
          >
            {intl.formatMessage({ id: 'invite.share' })}
          </button>
        )}
      </div>
      <p role="status" className="text-sm">
        {copy === 'copied' && intl.formatMessage({ id: 'invite.copied' })}
        {copy === 'by-hand' && intl.formatMessage({ id: 'invite.copyByHand' })}
      </p>
    </div>
  )
}
