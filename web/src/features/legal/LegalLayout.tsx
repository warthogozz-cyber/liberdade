import type { ReactNode } from 'react'
import { useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useDocumentTitle } from '@/shared/ui'
import { legalConfig } from './legalConfig'
import './register'

/** The frame of a public legal page: title, version and date, then its sections. */
export function LegalLayout({
  title,
  version,
  intro,
  children,
}: {
  title: string
  version: string
  intro: string
  children: ReactNode
}) {
  const intl = useIntl()
  useDocumentTitle(title)
  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-4 p-6">
      <Link
        to="/"
        className="min-h-12 self-start py-3 font-medium text-primary underline underline-offset-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        {intl.formatMessage({ id: 'legal.back' })}
      </Link>
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p className="text-text-muted">
        {intl.formatMessage({ id: 'legal.version' }, { version, date: legalConfig.updatedAt })}
      </p>
      <p>{intro}</p>
      {children}
    </main>
  )
}

type MessageId = FormatjsIntl.Message['ids']

/** One block inside a section: a paragraph, or a list. */
export type Block = { p: MessageId } | { list: MessageId[] }

export interface Section {
  title: MessageId
  blocks: Block[]
}

/** The sections of a legal page, each with a heading, in order. */
export function Sections({
  sections,
  values,
}: {
  sections: Section[]
  values: Record<string, string>
}) {
  const intl = useIntl()
  return (
    <>
      {sections.map((section) => (
        <section key={section.title} className="flex flex-col gap-2">
          <h2 className="mt-2 text-lg font-semibold">
            {intl.formatMessage({ id: section.title })}
          </h2>
          {section.blocks.map((block, index) =>
            'p' in block ? (
              <p key={index}>{intl.formatMessage({ id: block.p }, values)}</p>
            ) : (
              <ul key={index} className="flex list-disc flex-col gap-1 pl-6">
                {block.list.map((id) => (
                  <li key={id}>{intl.formatMessage({ id }, values)}</li>
                ))}
              </ul>
            ),
          )}
        </section>
      ))}
    </>
  )
}
