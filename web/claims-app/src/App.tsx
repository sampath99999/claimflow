import { Navigate, Route, Routes } from 'react-router'
import { Layout } from './components/Layout'
import { useAuth } from './lib/auth-context'
import { ClaimDetailPage } from './pages/ClaimDetailPage'
import { ClaimsPage } from './pages/ClaimsPage'
import { LoginPage } from './pages/LoginPage'
import { NewClaimPage } from './pages/NewClaimPage'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user } = useAuth()
  return user ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/claims" element={<ClaimsPage />} />
        <Route path="/claims/new" element={<NewClaimPage />} />
        <Route path="/claims/:id" element={<ClaimDetailPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/claims" replace />} />
    </Routes>
  )
}
