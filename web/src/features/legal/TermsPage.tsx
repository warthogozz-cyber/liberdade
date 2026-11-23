import { useIntl } from 'react-intl'
import { useLegalVersions } from '@/shared/api/legal'
import { legalConfig, legalText } from './legalConfig'
import { LegalLayout, Sections } from './LegalLayout'

/** The terms of use, public: the version the API says is in force, or the latest one offline. */
export function TermsPage() {
  const intl = useIntl()
  const { data } = useLegalVersions()
  const { version, text } = legalText(
    data?.terms ?? legalConfig.latest.terms,
    legalConfig.latest.terms,
  )
  return (
    <LegalLayout
      title={intl.formatMessage({ id: 'terms.title' })}
      version={version}
      date={text.updatedAt}
      intro={intl.formatMessage({ id: text.intro })}
    >
      <Sections
        sections={text.sections}
        values={{
          controller: legalConfig.controllerName,
          document: legalConfig.controllerDocument,
          address: legalConfig.controllerAddress,
          email: legalConfig.contactEmail,
        }}
      />
    </LegalLayout>
  )
}
