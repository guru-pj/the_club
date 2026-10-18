import { request } from './client'
import type {
  Activity, ActivityRequest, AppNotification, ApplyVerificationRequest, CheckInResponse, Club, ClubEvent,
  ClubLeaderboard, ClubRequest, CreatePostRequest, EventRegistrationRow, EventRequest, LeaderboardMetric,
  LeaderboardPeriod, LocalDateTime, MemberParticipation, Membership, MonthlyLeaderboard, Page, Participation,
  Payment, Post, RegisterResponse, StravaConnection, Ticket, TokenResponse, UpdateUserRequest, User,
  UserEventRegistration, UUID, VerificationAudit, VerificationRequest, VerificationStatus,
} from './types'

type Paging = { page?: number; size?: number }

export const authApi = {
  login: (email: string, password: string) =>
    request<TokenResponse>('POST', '/api/v1/auth/login', { body: { email, password }, anonymous: true }),
  /** Revokes this browser's session; the refresh token travels in the HttpOnly cookie. */
  logout: () => request<void>('POST', '/api/v1/auth/logout', { anonymous: true }),
  /** Revokes every session of the signed-in user (all devices). */
  logoutAll: () => request<void>('POST', '/api/v1/auth/logout-all'),
}

export const accountApi = {
  verifyEmail: (token: string) =>
    request<void>('POST', '/api/v1/users/verify-email', { body: { token }, anonymous: true }),
  resendVerification: (email: string) =>
    request<void>('POST', '/api/v1/users/verify-email/resend', { body: { email }, anonymous: true }),
  requestPasswordReset: (email: string) =>
    request<void>('POST', '/api/v1/users/password-reset/request', { body: { email }, anonymous: true }),
  resetPassword: (token: string, newPassword: string) =>
    request<void>('POST', '/api/v1/users/password-reset/confirm', { body: { token, newPassword }, anonymous: true }),
  /** Also signs out every session, so the caller must log in again. */
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('PUT', '/api/v1/users/me/password', { body: { currentPassword, newPassword } }),
}

export const usersApi = {
  register: (name: string, email: string, password: string) =>
    request<User>('POST', '/api/v1/users/register', { body: { name, email, password }, anonymous: true }),
  me: () => request<User>('GET', '/api/v1/users/me'),
  get: (id: UUID) => request<User>('GET', `/api/v1/users/${id}`),
  update: (id: UUID, body: UpdateUserRequest) => request<User>('PUT', `/api/v1/users/${id}`, { body }),
}

export const activitiesApi = {
  create: (body: ActivityRequest) => request<Activity>('POST', '/api/v1/activities', { body }),
  mine: (q: Paging & { from?: LocalDateTime; to?: LocalDateTime } = {}) =>
    request<Page<Activity>>('GET', '/api/v1/activities/me', { query: q }),
  byUser: (userId: UUID, q: Paging = {}) =>
    request<Page<Activity>>('GET', `/api/v1/activities/user/${userId}`, { query: q }),
  get: (id: UUID) => request<Activity>('GET', `/api/v1/activities/${id}`),
  update: (id: UUID, body: ActivityRequest) => request<Activity>('PUT', `/api/v1/activities/${id}`, { body }),
  remove: (id: UUID) => request<void>('DELETE', `/api/v1/activities/${id}`),
}

export const stravaApi = {
  connect: () => request<{ authorizeUrl: string }>('GET', '/api/v1/strava/connect'),
  /** Completes OAuth: the Strava redirect lands on the web app, which forwards code + state here. */
  callback: (code: string, state: string) =>
    request<{ connected: boolean; athleteId: number }>('GET', '/api/v1/strava/callback', {
      query: { code, state },
      anonymous: true,
    }),
  connection: () => request<StravaConnection>('GET', '/api/v1/strava/connection'),
  disconnect: () => request<void>('DELETE', '/api/v1/strava/connection'),
  sync: () => request<{ imported: number; updated: number }>('POST', '/api/v1/strava/sync'),
  activities: (userId: UUID, q: Paging = {}) =>
    request<Page<Activity>>('GET', `/api/v1/strava/activities/${userId}`, { query: q }),
}

export const clubsApi = {
  create: (body: ClubRequest) => request<Club>('POST', '/api/v1/clubs', { body }),
  search: (q: Paging & { q?: string; city?: string } = {}) => request<Page<Club>>('GET', '/api/v1/clubs', { query: q }),
  mine: () => request<Club[]>('GET', '/api/v1/clubs/me'),
  ownedBy: (ownerId: UUID) => request<Club[]>('GET', `/api/v1/clubs/owner/${ownerId}`),
  get: (id: UUID) => request<Club>('GET', `/api/v1/clubs/${id}`),
  update: (id: UUID, body: ClubRequest) => request<Club>('PUT', `/api/v1/clubs/${id}`, { body }),
  join: (id: UUID) => request<Membership>('POST', `/api/v1/clubs/${id}/join`),
  leave: (id: UUID) => request<void>('DELETE', `/api/v1/clubs/${id}/members/me`),
  members: (id: UUID, q: Paging = {}) => request<Page<Membership>>('GET', `/api/v1/clubs/${id}/members`, { query: q }),
  removeMember: (id: UUID, userId: UUID) => request<void>('DELETE', `/api/v1/clubs/${id}/members/${userId}`),
  leaderboard: (id: UUID, metric: LeaderboardMetric, period: LeaderboardPeriod) =>
    request<ClubLeaderboard>('GET', `/api/v1/clubs/${id}/leaderboard`, { query: { metric, period } }),
}

export const postsApi = {
  feed: (clubId: UUID, q: Paging = {}) => request<Page<Post>>('GET', `/api/v1/clubs/${clubId}/posts`, { query: q }),
  create: (clubId: UUID, body: CreatePostRequest) => request<Post>('POST', `/api/v1/clubs/${clubId}/posts`, { body }),
  update: (clubId: UUID, postId: UUID, body: Partial<Pick<CreatePostRequest, 'title' | 'content' | 'photoUrls'>>) =>
    request<Post>('PUT', `/api/v1/clubs/${clubId}/posts/${postId}`, { body }),
  remove: (clubId: UUID, postId: UUID) => request<void>('DELETE', `/api/v1/clubs/${clubId}/posts/${postId}`),
}

const OV = '/api/v1/owner-verification'
export const verificationApi = {
  apply: (body: ApplyVerificationRequest) =>
    request<{ requestId: UUID; status: VerificationStatus }>('POST', `${OV}/apply`, { body }),
  mine: () => request<VerificationRequest[]>('GET', `${OV}/requests/me`),
  get: (id: UUID) => request<VerificationRequest>('GET', `${OV}/requests/${id}`),
  history: (id: UUID) => request<VerificationAudit[]>('GET', `${OV}/requests/${id}/history`),
  list: (q: Paging & { status?: VerificationStatus } = {}) =>
    request<Page<VerificationRequest>>('GET', `${OV}/requests`, { query: q }),
  approve: (id: UUID, createClub: boolean, clubPayload?: ClubRequest) =>
    request<VerificationRequest>('POST', `${OV}/requests/${id}/approve`, { body: { createClub, clubPayload } }),
  reject: (id: UUID, reason: string) => request<VerificationRequest>('POST', `${OV}/requests/${id}/reject`, { body: { reason } }),
  requestInfo: (id: UUID, message: string) =>
    request<VerificationRequest>('POST', `${OV}/requests/${id}/request-info`, { body: { message } }),
  uploadProof: (id: UUID, proofFiles: string[], note?: string) =>
    request<VerificationRequest>('POST', `${OV}/requests/${id}/upload-proof`, { body: { proofFiles, note } }),
}

export const eventsApi = {
  create: (body: EventRequest) => request<ClubEvent>('POST', '/api/v1/events', { body }),
  get: (id: UUID) => request<ClubEvent>('GET', `/api/v1/events/${id}`),
  update: (id: UUID, body: EventRequest) => request<ClubEvent>('PUT', `/api/v1/events/${id}`, { body }),
  setRegistrationWindow: (id: UUID, registrationOpensAt: LocalDateTime, registrationClosesAt?: LocalDateTime | null) =>
    request<ClubEvent>('PATCH', `/api/v1/events/${id}/registration-window`, { body: { registrationOpensAt, registrationClosesAt } }),
  cancel: (id: UUID) => request<ClubEvent>('POST', `/api/v1/events/${id}/cancel`),
  byClub: (clubId: UUID, q: Paging & { upcoming?: boolean } = {}) =>
    request<Page<ClubEvent>>('GET', `/api/v1/events/club/${clubId}`, { query: q }),
  myRegistrations: () => request<UserEventRegistration[]>('GET', '/api/v1/events/me/registrations'),
  register: (id: UUID) => request<RegisterResponse>('POST', `/api/v1/events/${id}/register`),
  cancelMyRegistration: (id: UUID) => request<void>('DELETE', `/api/v1/events/${id}/registrations/me`),
  registrations: (id: UUID) => request<EventRegistrationRow[]>('GET', `/api/v1/events/${id}/registrations`),
  ticket: (registrationId: UUID) => request<Ticket>('GET', `/api/v1/events/registrations/${registrationId}/ticket`),
  checkIn: (id: UUID, ticketCode: string) =>
    request<CheckInResponse>('POST', `/api/v1/events/${id}/check-in`, { body: { ticketCode } }),
}

export const paymentsApi = {
  get: (id: UUID) => request<Payment>('GET', `/api/v1/payments/${id}`),
  confirm: (id: UUID, providerPaymentId: string, signature: string) =>
    request<Payment>('POST', `/api/v1/payments/${id}/confirm`, { body: { providerPaymentId, signature } }),
  /** Club owner/admin: refunds a successful payment and cancels the registration. */
  refund: (id: UUID, reason?: string) =>
    request<Payment>('POST', `/api/v1/payments/${id}/refund`, { body: { reason: reason || undefined } }),
  /** Development only (PAYMENT_PROVIDER=mock): produces the signature a real gateway would return. */
  mockSignature: (id: UUID, providerPaymentId: string) =>
    request<{ providerOrderId: string; providerPaymentId: string; signature: string }>(
      'POST', `/api/v1/payments/${id}/mock-signature`, { body: { providerPaymentId } }),
}

export const participationApi = {
  me: (clubId?: UUID) => request<Participation>('GET', '/api/v1/participation/me', { query: { clubId } }),
  user: (userId: UUID, clubId?: UUID) =>
    request<Participation>('GET', `/api/v1/participation/users/${userId}`, { query: { clubId } }),
  club: (clubId: UUID) => request<MemberParticipation[]>('GET', `/api/v1/participation/clubs/${clubId}`),
}

export const leaderboardsApi = {
  monthly: (month?: string, limit = 10) =>
    request<MonthlyLeaderboard>('GET', '/api/v1/leaderboards/monthly', { query: { month, limit } }),
}

export const notificationsApi = {
  mine: (q: Paging = {}) => request<Page<AppNotification>>('GET', '/api/v1/notifications/me', { query: q }),
  unreadCount: () => request<{ unread: number }>('GET', '/api/v1/notifications/me/unread-count'),
  markRead: (id: UUID) => request<AppNotification>('PATCH', `/api/v1/notifications/${id}/read`),
  markAllRead: () => request<void>('POST', '/api/v1/notifications/me/read-all'),
}
