import { Navigate, Route, Routes } from 'react-router'
import AppLayout from './components/layout/AppLayout.jsx'
import { ProtectedRoute, PublicOnlyRoute } from './components/ProtectedRoute.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { ToastProvider } from './context/ToastContext.jsx'
import Dashboard from './pages/Dashboard.jsx'
import ForgotPassword from './pages/ForgotPassword.jsx'
import Login from './pages/Login.jsx'
import NotFound from './pages/NotFound.jsx'
import Notifications from './pages/Notifications.jsx'
import Profile from './pages/Profile.jsx'
import Register from './pages/Register.jsx'
import ResetPassword from './pages/ResetPassword.jsx'
import SubscriptionDetails from './pages/SubscriptionDetails.jsx'
import SubscriptionForm from './pages/SubscriptionForm.jsx'
import Subscriptions from './pages/Subscriptions.jsx'
import VerifyEmail from './pages/VerifyEmail.jsx'

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
      <Routes>
        <Route element={<PublicOnlyRoute />}>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
        </Route>

        {/* Opened from email links; work whether or not the user is logged in */}
        <Route path="/verify-email" element={<VerifyEmail />} />
        <Route path="/reset-password" element={<ResetPassword />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/subscriptions" element={<Subscriptions />} />
            <Route path="/subscriptions/new" element={<SubscriptionForm />} />
            <Route path="/subscriptions/:id" element={<SubscriptionDetails />} />
            <Route path="/subscriptions/:id/edit" element={<SubscriptionForm />} />
            <Route path="/notifications" element={<Notifications />} />
            <Route path="/profile" element={<Profile />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
      </ToastProvider>
    </AuthProvider>
  )
}
