import { download, nativeRefreshToken, request, upload } from './client'
import type {
  Activity, ActivityRequest, AppNotification, ApplyVerificationRequest, ClubEventStats, EventStatsRow, VerificationDocument, CheckInResponse, Club, ClubEvent,
  BlockedUser, ChatMessage, ChatMute, ChatReport, ChatReportStatus, ClubLeaderboard, ClubPhoto, ClubRequest,
  Conversation, CreatePostRequest, EventCard, EventRegistrationRow, EventRequest, LeaderboardMetric,
  LeaderboardPeriod, LocalDateTime, MemberParticipation, Membership, MonthlyLeaderboard, Page, Participation,
  Payment, Post, PostComment, PostPreview, RegisterResponse, Review, ReviewPage, StravaConnection, Ticket, TokenResponse, UpdateUserRequest, User,
  UserEventRegistration, UUID, VerificationAudit, VerificationRequest, VerificationStatus,
} from './types'

type Paging = { page?: number; size?: number }

export const authApi = {
  login: (email: string, password: string) =>
    request<TokenResponse>('POST', '/api/v1/auth/login', { body: { email, password }, anonymous: true }),
  /** Revokes this session; the refresh token travels in the HttpOnly cookie (browser) or the body (native apps). */
  logout: () => {
    const refreshToken = nativeRefreshToken()
    return request<void>('POST', '/api/v1/auth/logout', { anonymous: true, ...(refreshToken && { body: { refreshToken } }) })
  },
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
  /** clubOwner: "I run a club" (owner access still needs an approved club application) */
  register: (name: string, email: string, password: string, clubOwner = false) =>
    request<User>('POST', '/api/v1/users/register', { body: { name, email, password, clubOwner }, anonymous: true }),
  me: () => request<User>('GET', '/api/v1/users/me'),
  get: (id: UUID) => request<User>('GET', `/api/v1/users/${id}`),
  update: (id: UUID, body: UpdateUserRequest) => request<User>('PUT', `/api/v1/users/${id}`, { body }),
  uploadPhoto: (file: File | Blob) => upload<User>('/api/v1/users/me/photo', file),
  removePhoto: () => request<User>('DELETE', '/api/v1/users/me/photo'),
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
  nearby: (lat: number, lng: number, radiusKm?: number, limit?: number) =>
    request<Club[]>('GET', '/api/v1/clubs/nearby', { query: { lat, lng, radiusKm, limit } }),
  popular: (limit?: number) => request<Club[]>('GET', '/api/v1/clubs/popular', { query: { limit } }),
  following: () => request<Club[]>('GET', '/api/v1/clubs/me/following'),
  follow: (id: UUID) => request<{ following: boolean }>('POST', `/api/v1/clubs/${id}/follow`),
  unfollow: (id: UUID) => request<void>('DELETE', `/api/v1/clubs/${id}/follow`),
  photos: (id: UUID) => request<ClubPhoto[]>('GET', `/api/v1/clubs/${id}/photos`),
  addPhoto: (id: UUID, file: File | Blob, caption?: string) =>
    upload<ClubPhoto>(`/api/v1/clubs/${id}/photos`, file, caption ? { caption } : {}),
  removePhoto: (id: UUID, photoId: UUID) => request<void>('DELETE', `/api/v1/clubs/${id}/photos/${photoId}`),
  uploadLogo: (id: UUID, file: File | Blob) => upload<Club>(`/api/v1/clubs/${id}/logo`, file),
  uploadCover: (id: UUID, file: File | Blob) => upload<Club>(`/api/v1/clubs/${id}/cover`, file),
  removeCover: (id: UUID) => request<Club>('DELETE', `/api/v1/clubs/${id}/cover`),
  leaderboard: (id: UUID, metric: LeaderboardMetric, period: LeaderboardPeriod) =>
    request<ClubLeaderboard>('GET', `/api/v1/clubs/${id}/leaderboard`, { query: { metric, period } }),
}

export const postsApi = {
  feed: (clubId: UUID, q: Paging = {}) => request<Page<Post>>('GET', `/api/v1/clubs/${clubId}/posts`, { query: q }),
  get: (clubId: UUID, postId: UUID) => request<Post>('GET', `/api/v1/clubs/${clubId}/posts/${postId}`),
  /** no sign-in needed: what a shared link shows */
  preview: (clubId: UUID, postId: UUID) =>
    request<PostPreview>('GET', `/api/v1/clubs/${clubId}/posts/${postId}/preview`, { anonymous: true }),
  create: (clubId: UUID, body: CreatePostRequest) => request<Post>('POST', `/api/v1/clubs/${clubId}/posts`, { body }),
  /** a photo for a post about to be written; put the returned url in photoUrls */
  uploadPhoto: (clubId: UUID, file: File | Blob) => upload<{ url: string }>(`/api/v1/clubs/${clubId}/posts/photos`, file),
  /** owner: share another club's post (postId) or an event (eventId) into this club's feed */
  reshare: (clubId: UUID, body: { postId?: UUID; eventId?: UUID; comment?: string }) =>
    request<Post>('POST', `/api/v1/clubs/${clubId}/posts/reshare`, { body }),
  update: (clubId: UUID, postId: UUID, body: Partial<Pick<CreatePostRequest, 'title' | 'content' | 'photoUrls'>>) =>
    request<Post>('PUT', `/api/v1/clubs/${clubId}/posts/${postId}`, { body }),
  remove: (clubId: UUID, postId: UUID) => request<void>('DELETE', `/api/v1/clubs/${clubId}/posts/${postId}`),
  like: (clubId: UUID, postId: UUID) =>
    request<{ liked: boolean; likeCount: number }>('PUT', `/api/v1/clubs/${clubId}/posts/${postId}/like`),
  unlike: (clubId: UUID, postId: UUID) =>
    request<{ liked: boolean; likeCount: number }>('DELETE', `/api/v1/clubs/${clubId}/posts/${postId}/like`),
  comments: (clubId: UUID, postId: UUID, q: Paging = {}) =>
    request<Page<PostComment>>('GET', `/api/v1/clubs/${clubId}/posts/${postId}/comments`, { query: q }),
  addComment: (clubId: UUID, postId: UUID, content: string) =>
    request<PostComment>('POST', `/api/v1/clubs/${clubId}/posts/${postId}/comments`, { body: { content } }),
  removeComment: (clubId: UUID, postId: UUID, commentId: UUID) =>
    request<void>('DELETE', `/api/v1/clubs/${clubId}/posts/${postId}/comments/${commentId}`),
}

const CHAT = '/api/v1/chat'
export const chatApi = {
  inbox: () => request<Conversation[]>('GET', `${CHAT}/inbox`),
  /** the club's group chat (created the first time it's opened) */
  club: (clubId: UUID) => request<Conversation>('GET', `${CHAT}/clubs/${clubId}`),
  /** opens or starts a direct conversation with someone who shares a club with you */
  direct: (userId: UUID) => request<Conversation>('POST', `${CHAT}/direct`, { body: { userId } }),
  get: (id: UUID) => request<Conversation>('GET', `${CHAT}/conversations/${id}`),
  /** oldest first; `before` (a message ID) pages back */
  messages: (id: UUID, q: { before?: UUID; limit?: number } = {}) =>
    request<ChatMessage[]>('GET', `${CHAT}/conversations/${id}/messages`, { query: q }),
  send: (id: UUID, body: { body?: string; photoUrl?: string }) =>
    request<ChatMessage>('POST', `${CHAT}/conversations/${id}/messages`, { body }),
  markRead: (id: UUID) => request<void>('POST', `${CHAT}/conversations/${id}/read`),
  uploadPhoto: (file: File | Blob) => upload<{ url: string }>(`${CHAT}/photos`, file),
  removeMessage: (messageId: UUID) => request<void>('DELETE', `${CHAT}/messages/${messageId}`),
  report: (messageId: UUID, reason?: string) =>
    request<void>('POST', `${CHAT}/messages/${messageId}/report`, { body: { reason: reason || undefined } }),
  blocked: () => request<BlockedUser[]>('GET', `${CHAT}/blocks`),
  block: (userId: UUID) => request<void>('PUT', `${CHAT}/blocks/${userId}`),
  unblock: (userId: UUID) => request<void>('DELETE', `${CHAT}/blocks/${userId}`),
  mutes: (clubId: UUID) => request<ChatMute[]>('GET', `${CHAT}/clubs/${clubId}/mutes`),
  /** without hours: until unmuted */
  mute: (clubId: UUID, userId: UUID, hours?: number) =>
    request<ChatMute>('PUT', `${CHAT}/clubs/${clubId}/mutes/${userId}`, { body: hours ? { hours } : {} }),
  unmute: (clubId: UUID, userId: UUID) => request<void>('DELETE', `${CHAT}/clubs/${clubId}/mutes/${userId}`),
  /** admins */
  reports: (q: Paging & { status?: ChatReportStatus } = {}) => request<Page<ChatReport>>('GET', `${CHAT}/reports`, { query: q }),
  resolveReport: (reportId: UUID, removeMessage: boolean) =>
    request<void>('POST', `${CHAT}/reports/${reportId}/resolve`, { body: { removeMessage } }),
}

export const reviewsApi = {
  list: (clubId: UUID, q: Paging = {}) => request<ReviewPage>('GET', `/api/v1/clubs/${clubId}/reviews`, { query: q }),
  save: (clubId: UUID, rating: number, comment?: string) =>
    request<Review>('PUT', `/api/v1/clubs/${clubId}/reviews/me`, { body: { rating, comment: comment || undefined } }),
  removeMine: (clubId: UUID) => request<void>('DELETE', `/api/v1/clubs/${clubId}/reviews/me`),
  reply: (clubId: UUID, reviewId: UUID, reply: string) =>
    request<Review>('PUT', `/api/v1/clubs/${clubId}/reviews/${reviewId}/reply`, { body: { reply } }),
  removeReply: (clubId: UUID, reviewId: UUID) => request<Review>('DELETE', `/api/v1/clubs/${clubId}/reviews/${reviewId}/reply`),
  /** admins only */
  remove: (clubId: UUID, reviewId: UUID) => request<void>('DELETE', `/api/v1/clubs/${clubId}/reviews/${reviewId}`),
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
  /** creates the club (clubPayload.name = its official, verified name) and links it to the applicant */
  approve: (id: UUID, clubPayload?: ClubRequest) =>
    request<VerificationRequest>('POST', `${OV}/requests/${id}/approve`, { body: { clubPayload } }),
  reject: (id: UUID, reason: string) => request<VerificationRequest>('POST', `${OV}/requests/${id}/reject`, { body: { reason } }),
  requestInfo: (id: UUID, message: string) =>
    request<VerificationRequest>('POST', `${OV}/requests/${id}/request-info`, { body: { message } }),
  uploadProof: (id: UUID, proofFiles: string[], note?: string) =>
    request<VerificationRequest>('POST', `${OV}/requests/${id}/upload-proof`, { body: { proofFiles, note } }),
  /** the applicant corrects the application (pending, or after changes were requested) */
  update: (id: UUID, body: ApplyVerificationRequest) => request<VerificationRequest>('PUT', `${OV}/requests/${id}`, { body }),
  /** back to the review queue after making the requested changes */
  resubmit: (id: UUID, note?: string) =>
    request<VerificationRequest>('POST', `${OV}/requests/${id}/resubmit`, { body: { note: note || undefined } }),
  /** a private proof document: PDF, JPEG, PNG or WebP */
  addDocument: (id: UUID, file: File | Blob) => upload<VerificationDocument>(`${OV}/requests/${id}/documents`, file),
  removeDocument: (id: UUID, documentId: UUID) => request<void>('DELETE', `${OV}/requests/${id}/documents/${documentId}`),
  /** downloads with the session's token (the files are private) */
  downloadDocument: (id: UUID, documentId: UUID) => download(`${OV}/requests/${id}/documents/${documentId}`),
}

export const eventsApi = {
  create: (body: EventRequest) => request<ClubEvent>('POST', '/api/v1/events', { body }),
  get: (id: UUID) => request<ClubEvent>('GET', `/api/v1/events/${id}`),
  update: (id: UUID, body: EventRequest) => request<ClubEvent>('PUT', `/api/v1/events/${id}`, { body }),
  setRegistrationWindow: (id: UUID, registrationOpensAt: LocalDateTime, registrationClosesAt?: LocalDateTime | null) =>
    request<ClubEvent>('PATCH', `/api/v1/events/${id}/registration-window`, { body: { registrationOpensAt, registrationClosesAt } }),
  cancel: (id: UUID) => request<ClubEvent>('POST', `/api/v1/events/${id}/cancel`),
  publish: (id: UUID) => request<ClubEvent>('POST', `/api/v1/events/${id}/publish`),
  complete: (id: UUID) => request<ClubEvent>('POST', `/api/v1/events/${id}/complete`),
  stats: (id: UUID) => request<EventStatsRow>('GET', `/api/v1/events/${id}/stats`),
  clubStats: (clubId: UUID) => request<ClubEventStats>('GET', `/api/v1/events/club/${clubId}/stats`),
  byClub: (clubId: UUID, q: Paging & { upcoming?: boolean } = {}) =>
    request<Page<ClubEvent>>('GET', `/api/v1/events/club/${clubId}`, { query: q }),
  /** followed and subscribed clubs, plus clubs near the point when given */
  upcoming: (q: { lat?: number; lng?: number; radiusKm?: number; limit?: number } = {}) =>
    request<EventCard[]>('GET', '/api/v1/events/upcoming', { query: q }),
  uploadImage: (id: UUID, file: File | Blob) => upload<ClubEvent>(`/api/v1/events/${id}/image`, file),
  removeImage: (id: UUID) => request<ClubEvent>('DELETE', `/api/v1/events/${id}/image`),
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
