import { useIntl } from 'react-intl'
import { legalConfig } from './legalConfig'
import { LegalLayout, Sections, type Section } from './LegalLayout'

const SECTIONS: Section[] = [
  { title: 'terms.what.title', blocks: [{ p: 'terms.what.p1' }] },
  { title: 'terms.free.title', blocks: [{ p: 'terms.free.p1' }] },
  {
    title: 'terms.education.title',
    blocks: [{ p: 'terms.education.p1' }, { p: 'terms.education.p2' }, { p: 'terms.education.p3' }],
  },
  {
    title: 'terms.responsibility.title',
    blocks: [{ p: 'terms.responsibility.p1' }, { p: 'terms.responsibility.p2' }],
  },
  { title: 'terms.asIs.title', blocks: [{ p: 'terms.asIs.p1' }] },
  { title: 'terms.delete.title', blocks: [{ p: 'terms.delete.p1' }] },
  { title: 'terms.law.title', blocks: [{ p: 'terms.law.p1' }, { p: 'terms.law.p2' }] },
]

/** The terms of use, public. */
export function TermsPage() {
  const intl = useIntl()
  return (
    <LegalLayout
      title={intl.formatMessage({ id: 'terms.title' })}
      version={legalConfig.versions.terms}
      intro={intl.formatMessage({ id: 'terms.intro' })}
    >
      <Sections sections={SECTIONS} values={{ email: legalConfig.contactEmail }} />
    </LegalLayout>
  )
}
