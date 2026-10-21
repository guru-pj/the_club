import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { GuestOnly, RequireAuth, RequireRole } from './components/guards'
import { Layout } from './components/Layout'
import { ToastProvider } from './components/Toast'
import { Loading } from './components/ui'

const LoginPage = lazy(() => import('./pages/LoginPage'))
const RegisterPage = lazy(() => import('./pages/RegisterPage'))
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage'))
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage'))
const VerifyEmailPage = lazy(() => import('./pages/VerifyEmailPage'))
const HomePage = lazy(() => import('./pages/HomePage'))
const ProfileSetupPage = lazy(() => import('./pages/ProfileSetupPage'))
const EventsPage = lazy(() => import('./pages/EventsPage'))
const ProfilePage = lazy(() => import('./pages/ProfilePage'))
const NotificationsPage = lazy(() => import('./pages/NotificationsPage'))
const ActivitiesPage = lazy(() => import('./pages/ActivitiesPage'))
const StravaCallbackPage = lazy(() => import('./pages/StravaCallbackPage'))
const ClubsPage = lazy(() => import('./pages/ClubsPage'))
const CreateClubPage = lazy(() => import('./pages/CreateClubPage'))
const ClubPage = lazy(() => import('./pages/ClubPage'))
const BecomeOwnerPage = lazy(() => import('./pages/BecomeOwnerPage'))
const AdminVerificationPage = lazy(() => import('./pages/AdminVerificationPage'))
const EventPage = lazy(() => import('./pages/EventPage'))
const EventFormPage = lazy(() => import('./pages/EventFormPage'))
const CheckInPage = lazy(() => import('./pages/CheckInPage'))
const MyTicketsPage = lazy(() => import('./pages/MyTicketsPage'))
const TicketPage = lazy(() => import('./pages/TicketPage'))
const LeaderboardPage = lazy(() => import('./pages/LeaderboardPage'))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'))

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Suspense fallback={<Loading />}>
            <Routes>
              <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
              <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
              {/* reachable signed in or out: links arrive by email */}
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />
              <Route path="/verify-email" element={<VerifyEmailPage />} />
              <Route element={<RequireAuth><Layout /></RequireAuth>}>
                <Route index element={<HomePage />} />
                <Route path="welcome" element={<ProfileSetupPage />} />
                <Route path="profile" element={<ProfilePage />} />
                <Route path="notifications" element={<NotificationsPage />} />
                <Route path="activities" element={<ActivitiesPage />} />
                <Route path="strava/callback" element={<StravaCallbackPage />} />
                <Route path="clubs" element={<ClubsPage />} />
                <Route path="clubs/new" element={<RequireRole role="CLUB_OWNER"><CreateClubPage /></RequireRole>} />
                <Route path="clubs/:clubId" element={<ClubPage />} />
                <Route path="clubs/:clubId/events/new" element={<EventFormPage />} />
                <Route path="become-owner" element={<BecomeOwnerPage />} />
                <Route path="admin/verification" element={<RequireRole role="ADMIN"><AdminVerificationPage /></RequireRole>} />
                <Route path="events" element={<EventsPage />} />
                <Route path="events/:eventId" element={<EventPage />} />
                <Route path="events/:eventId/edit" element={<EventFormPage />} />
                <Route path="events/:eventId/check-in" element={<CheckInPage />} />
                <Route path="tickets" element={<MyTicketsPage />} />
                <Route path="tickets/:registrationId" element={<TicketPage />} />
                <Route path="leaderboard" element={<LeaderboardPage />} />
                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Routes>
          </Suspense>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
