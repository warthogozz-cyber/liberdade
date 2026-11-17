import { createIntl } from 'react-intl'
import { describe, expect, it } from 'vitest'
import { appMessages } from '@/shared/i18n'
import type { ActivityItem } from '@/shared/api/privacy'
import { activityPhrase } from './activityPhrase'
import './register'

function phrase(action: string, byMe = true): string {
  const item: ActivityItem = {
    id: '0192f3a4-0000-7000-8000-0000000000c9',
    action,
    occurredAt: '2026-11-16T12:00:00Z',
    workspaceId: null,
    actorName: 'Ana Teste',
    byMe,
    metadata: {},
  }
  return activityPhrase(createIntl({ locale: 'pt-BR', messages: appMessages() }), item)
}

describe('activityPhrase', () => {
  it('documentsAccepted', () => {
    expect(phrase('documents.accepted')).toBe('Aceitou os termos e a política de privacidade')
  })

  it('passkeyRemoved', () => {
    expect(phrase('passkey.removed')).toBe('Removeu uma passkey')
  })

  it('keepsTheGenericLineForAnUnknownAction', () => {
    expect(phrase('something.new')).toBe('Alteração na conta')
  })
})
