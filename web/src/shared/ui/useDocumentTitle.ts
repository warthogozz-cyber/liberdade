import { useEffect } from 'react'
import { useIntl } from 'react-intl'

/** Shows `"<title> · Liberdade"` as the page title while the screen is open. */
export function useDocumentTitle(title: string): void {
  const appName = useIntl().formatMessage({ id: 'app.name' })
  useEffect(() => {
    const previous = document.title
    document.title = `${title} · ${appName}`
    return () => {
      document.title = previous
    }
  }, [title, appName])
}
