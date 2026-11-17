import { useIntl } from 'react-intl'
import { legalConfig } from './legalConfig'
import { LegalLayout, Sections, type Section } from './LegalLayout'

const SECTIONS: Section[] = [
  {
    title: 'policy.who.title',
    blocks: [{ p: 'policy.who.p1' }, { p: 'policy.who.p2' }],
  },
  {
    title: 'policy.data.title',
    blocks: [
      { p: 'policy.data.intro' },
      {
        list: [
          'policy.data.name',
          'policy.data.email',
          'policy.data.passkey',
          'policy.data.ledger',
          'policy.data.trail',
          'policy.data.consents',
        ],
      },
      { p: 'policy.data.cookie' },
    ],
  },
  {
    title: 'policy.basis.title',
    blocks: [{ list: ['policy.basis.contract', 'policy.basis.consent', 'policy.basis.legal'] }],
  },
  {
    title: 'policy.youth.title',
    blocks: [{ p: 'policy.youth.p1' }, { p: 'policy.youth.p2' }, { p: 'policy.youth.p3' }],
  },
  {
    title: 'policy.partners.title',
    blocks: [
      { p: 'policy.partners.intro' },
      { list: ['policy.partners.aws', 'policy.partners.cloudflare', 'policy.partners.google'] },
      { p: 'policy.partners.transfer' },
    ],
  },
  {
    title: 'policy.retention.title',
    blocks: [
      { p: 'policy.retention.intro' },
      {
        list: [
          'policy.retention.deletion',
          'policy.retention.transaction',
          'policy.retention.invitations',
          'policy.retention.trail',
          'policy.retention.backups',
          'policy.retention.logs',
        ],
      },
    ],
  },
  {
    title: 'policy.rights.title',
    blocks: [
      { p: 'policy.rights.intro' },
      {
        list: [
          'policy.rights.export',
          'policy.rights.correct',
          'policy.rights.revoke',
          'policy.rights.other',
        ],
      },
    ],
  },
  {
    title: 'policy.security.title',
    blocks: [
      {
        list: [
          'policy.security.https',
          'policy.security.database',
          'policy.security.passwordless',
          'policy.security.leastPrivilege',
        ],
      },
    ],
  },
  {
    title: 'policy.changes.title',
    blocks: [{ p: 'policy.changes.p1' }, { p: 'policy.changes.p2' }],
  },
]

/** The privacy policy, public: the person reads it before they have an account (LGPD art. 9). */
export function PrivacyPolicyPage() {
  const intl = useIntl()
  return (
    <LegalLayout
      title={intl.formatMessage({ id: 'policy.title' })}
      version={legalConfig.versions.privacy}
      intro={intl.formatMessage({ id: 'policy.intro' })}
    >
      <Sections
        sections={SECTIONS}
        values={{ controller: legalConfig.controllerName, email: legalConfig.contactEmail }}
      />
    </LegalLayout>
  )
}
