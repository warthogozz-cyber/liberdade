import type { RouteObject } from 'react-router'
import { NotFoundPage } from '@/app/NotFoundPage'
import { SignInPage } from '@/features/auth/SignInPage'

export const routes: RouteObject[] = [
  { path: '/', element: <SignInPage /> },
  { path: '*', element: <NotFoundPage /> },
]
