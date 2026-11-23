import { useIntl } from 'react-intl'
import { useLegalVersions } from '@/shared/api/legal'
import { legalConfig, legalText } from './legalConfig'
import { LegalLayout, Sections } from './LegalLayout'

/**
 * The privacy policy, public: the person reads it before they have an account (LGPD art. 9). It
 * shows the version the API says is in force, or the latest one offline.
 */
export function PrivacyPolicyPage() {
  const intl = useIntl()
  const { data } = useLegalVersions()
  const { version, text } = legalText(
    data?.privacy ?? legalConfig.latest.privacy,
    legalConfig.latest.privacy,
  )
  return (
    <LegalLayout
      title={intl.formatMessage({ id: 'policy.title' })}
      version={version}
      date={text.updatedAt}
      intro={intl.formatMessage({ id: text.intro })}
    >
      <Sections
        sections={text.sections}
        values={{ controller: legalConfig.controllerName, email: legalConfig.contactEmail }}
      />
    </LegalLayout>
  )
}
