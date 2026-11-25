import { useState } from 'react'
import { useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import { useVentureTemplates, type TemplateView, type TrackView } from '@/shared/api/ventures'
import { registerMessages } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import { venturesMessages } from './messages.pt-BR'
import { NewVentureSheet } from './NewVentureSheet'

registerMessages(venturesMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const card = `flex min-h-12 w-full flex-col gap-1 rounded-card bg-surface p-4 text-left ${focus}`

/** Where a project starts: the five tracks, or a blank one. A track with two models asks which. */
export function StartPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'ventures.start' }))
  const workspace = useCurrentWorkspace()
  const templates = useVentureTemplates(workspace?.id ?? '')
  const [expanded, setExpanded] = useState<string>()
  // `null` is the blank project, a template is a start from that model.
  const [starting, setStarting] = useState<TemplateView | null>()

  const tracks = templates.data?.tracks

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{intl.formatMessage({ id: 'ventures.start' })}</h1>
      <p className="text-text-muted">{intl.formatMessage({ id: 'ventures.start.heading' })}</p>
      {tracks ? (
        <>
          <ul
            aria-label={intl.formatMessage({ id: 'ventures.start.tracks' })}
            className="flex flex-col gap-3"
          >
            {tracks.map((track) => (
              <li key={track.key} className="flex flex-col gap-2">
                <TrackCard
                  track={track}
                  expanded={expanded === track.key}
                  onToggle={() =>
                    setExpanded((current) => (current === track.key ? undefined : track.key))
                  }
                  onStart={(template) => setStarting(template)}
                />
                {expanded === track.key && track.templates.length > 1 && (
                  <ul
                    aria-label={intl.formatMessage(
                      { id: 'ventures.start.templatesOf' },
                      { track: track.name },
                    )}
                    className="flex flex-col gap-2 pl-4"
                  >
                    {track.templates.map((template) => (
                      <li key={template.key}>
                        <button
                          type="button"
                          onClick={() => setStarting(template)}
                          className={`${card} border border-primary`}
                        >
                          {template.name}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => setStarting(null)}
            className={`${card} border border-primary`}
          >
            <span className="font-medium">
              {intl.formatMessage({ id: 'ventures.start.blank' })}
            </span>
            <span className="text-sm text-text-muted">
              {intl.formatMessage({ id: 'ventures.start.blankHint' })}
            </span>
          </button>
        </>
      ) : templates.isError ? (
        <div className="flex flex-col items-start gap-2">
          <p>{intl.formatMessage({ id: 'common.error' })}</p>
          <button
            type="button"
            onClick={() => void templates.refetch()}
            className={`min-h-12 rounded-field border border-primary px-4 font-medium text-primary ${focus}`}
          >
            {intl.formatMessage({ id: 'common.retry' })}
          </button>
        </div>
      ) : (
        <ul
          aria-busy="true"
          aria-label={intl.formatMessage({ id: 'ventures.start.loading' })}
          className="flex flex-col gap-3"
        >
          {[0, 1, 2].map((slot) => (
            <li key={slot} className="h-20 rounded-card bg-surface" />
          ))}
        </ul>
      )}
      {workspace && starting !== undefined && (
        <NewVentureSheet
          workspaceId={workspace.id}
          {...(starting ? { template: starting } : {})}
          onClose={() => setStarting(undefined)}
        />
      )}
    </main>
  )
}

interface TrackCardProps {
  track: TrackView
  expanded: boolean
  onToggle: () => void
  onStart: (template: TemplateView) => void
}

function TrackCard({ track, expanded, onToggle, onStart }: TrackCardProps) {
  const body = (
    <>
      <span className="font-medium">{track.name}</span>
      <span className="text-sm text-text-muted">{track.description}</span>
    </>
  )
  const only = track.templates.length === 1 ? track.templates[0] : undefined
  if (track.educational) {
    return (
      <Link to="/mais/projetos/investimentos" className={card}>
        {body}
      </Link>
    )
  }
  if (only) {
    return (
      <button type="button" onClick={() => onStart(only)} className={card}>
        {body}
      </button>
    )
  }
  return (
    <button type="button" aria-expanded={expanded} onClick={onToggle} className={card}>
      {body}
    </button>
  )
}
