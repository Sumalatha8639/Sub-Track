import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../context/AuthContext.jsx'
import FullPageLoader from './layout/FullPageLoader.jsx'

/** Only logged-in users; others go to /login and come back afterwards. */
export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <FullPageLoader />
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location }} />
  return <Outlet />
}

/** Login/register pages: a logged-in user goes straight to the dashboard. */
export function PublicOnlyRoute() {
  const { status } = useAuth()
  if (status === 'loading') return <FullPageLoader />
  if (status === 'authenticated') return <Navigate to="/dashboard" replace />
  return <Outlet />
}
