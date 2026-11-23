import { describe, expect, it } from 'vitest'
import type { DocumentStatus } from '@/shared/api'
import { documentsToAccept, policyUpdate, versionsToAccept } from './useDocuments'

const listed = (purpose: DocumentStatus['purpose'], currentVersion: string): DocumentStatus => ({
  purpose,
  currentVersion,
  acceptedVersion: null,
  acceptedAt: null,
  acceptanceRequired: true,
})

const keptPolicy: DocumentStatus = {
  purpose: 'PRIVACY',
  currentVersion: 'privacy-2026-12.1',
  acceptedVersion: 'privacy-2026-12',
  acceptedAt: '2026-11-20T10:00:00Z',
  acceptanceRequired: false,
}

describe('versionsToAccept', () => {
  it('takesTheListedCurrentVersions', () => {
    expect(
      versionsToAccept([listed('TERMS', 'terms-2026-11'), listed('PRIVACY', 'privacy-2026-11')]),
    ).toEqual({ terms: 'terms-2026-11', privacy: 'privacy-2026-11' })
  })

  it('throwsWithoutAVersion', () => {
    expect(() => versionsToAccept([listed('TERMS', 'terms-2026-12')])).toThrow(
      'documents: no current version for PRIVACY',
    )
    expect(() => versionsToAccept([listed('TERMS', ''), listed('PRIVACY', 'p')])).toThrow(
      'documents: no current version for TERMS',
    )
  })
})

describe('documentsToAccept', () => {
  it('doesNotBlockWhenAcceptanceIsNotRequired', () => {
    // The accepted version differs from the current one, but the API says it still counts.
    expect(documentsToAccept([keptPolicy])).toEqual([])
  })

  it('blocksWhenAcceptanceIsRequired', () => {
    const terms = listed('TERMS', 'terms-2026-12')
    expect(documentsToAccept([terms, keptPolicy])).toEqual([terms])
    expect(documentsToAccept([{ ...keptPolicy, acceptanceRequired: true }])).toHaveLength(1)
  })

  it('ignoresTheParentalConsent', () => {
    expect(documentsToAccept([listed('PARENTAL', 'x')])).toEqual([])
  })
})

describe('policyUpdate', () => {
  it('namesThePolicyThatChangedWithoutAskingAgain', () => {
    expect(policyUpdate([keptPolicy])).toBe('privacy-2026-12.1')
  })

  it('isEmptyWhenTheAcceptedVersionIsTheCurrentOne', () => {
    expect(policyUpdate([{ ...keptPolicy, acceptedVersion: 'privacy-2026-12.1' }])).toBeUndefined()
  })

  it('isEmptyWhenTheAcceptIsRequired', () => {
    expect(policyUpdate([{ ...keptPolicy, acceptanceRequired: true }])).toBeUndefined()
    expect(policyUpdate([listed('PRIVACY', 'privacy-2026-12.1')])).toBeUndefined()
  })
})
