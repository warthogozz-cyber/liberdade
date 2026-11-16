import { useLocation } from 'react-router'
import { ActivityPage } from './ActivityPage'
import { PrivacyPage } from './PrivacyPage'

/** `/mais/privacidade` and `/mais/privacidade/atividade`. */
export function PrivacyRoutes() {
  return useLocation().pathname.replace(/\/$/, '').endsWith('/atividade') ? (
    <ActivityPage />
  ) : (
    <PrivacyPage />
  )
}
