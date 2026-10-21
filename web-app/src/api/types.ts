// Mirrors the backend DTOs (see docs/SERVICE_CONTRACTS.md). Dates are ISO LocalDateTime strings
// without a zone ("2026-10-18T06:00:00"), interpreted in the platform time zone.

export type UUID = string
export type LocalDateTime = string

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
}

// ---------- users / auth
export type Role = 'USER' | 'CLUB_OWNER' | 'ADMIN'

export interface User {
  id: UUID
  name: string
  email?: string
  roles: Role[]
  bio?: string | null
  city?: string | null
  profilePicUrl?: string | null
  active?: boolean
  /** only present on your own profile (or for admins) */
  emailVerified?: boolean
  createdAt: LocalDateTime
  /** approximate (~100 m); only on your own profile */
  latitude?: number | null
  longitude?: number | null
  /** false until the profile setup after the first login is finished or skipped; only on your own profile */
  profileComplete?: boolean
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export interface UpdateUserRequest {
  name?: string
  bio?: string
  city?: string
  profilePicUrl?: string
  /** send both, or neither */
  latitude?: number
  longitude?: number
  clearLocation?: boolean
  profileComplete?: boolean
}

// ---------- activities / strava
export type ActivityType = 'RUN' | 'RIDE' | 'WALK' | 'HIKE' | 'SWIM' | 'WORKOUT' | 'YOGA' | 'OTHER'
export const ACTIVITY_TYPES: ActivityType[] = ['RUN', 'RIDE', 'WALK', 'HIKE', 'SWIM', 'WORKOUT', 'YOGA', 'OTHER']
export type ActivitySource = 'MANUAL' | 'STRAVA'

export interface Activity {
  id: UUID
  userId: UUID
  title: string
  type: ActivityType
  /** kilometres */
  distance: number
  /** seconds */
  duration: number
  calories?: number | null
  startTime: LocalDateTime
  endTime: LocalDateTime
  source: ActivitySource
  externalId?: string | null
  createdAt: LocalDateTime
}

export interface ActivityRequest {
  title: string
  type: ActivityType
  distance: number
  duration: number
  calories?: number | null
  startTime: LocalDateTime
}

export interface StravaConnection {
  connected: boolean
  athleteId?: number | null
  connectedAt?: LocalDateTime | null
}

// ---------- clubs
export type ClubType = 'RUNNING' | 'CYCLING' | 'TRIATHLON' | 'SWIMMING' | 'FITNESS' | 'OTHER'
export const CLUB_TYPES: ClubType[] = ['RUNNING', 'CYCLING', 'TRIATHLON', 'SWIMMING', 'FITNESS', 'OTHER']
export type MembershipRole = 'OWNER' | 'MEMBER'

export interface Club {
  id: UUID
  name: string
  description?: string | null
  city?: string | null
  clubType?: ClubType | null
  logoUrl?: string | null
  ownerId: UUID
  /** subscribers (members, including the owner) */
  memberCount: number
  /** your subscription: null when you don't subscribe */
  myRole: MembershipRole | null
  createdAt: LocalDateTime
  latitude: number | null
  longitude: number | null
  address: string | null
  subscriptionsEnabled: boolean
  subscriptionBenefits: string | null
  followerCount: number
  following: boolean
  /** 1–5, one decimal; null without reviews */
  ratingAverage: number | null
  ratingCount: number
  /** only in nearby results */
  distanceKm: number | null
}

export interface ClubRequest {
  name?: string
  description?: string
  city?: string
  clubType?: ClubType
  logoUrl?: string
  /** send both, or neither */
  latitude?: number
  longitude?: number
  clearLocation?: boolean
  address?: string
  subscriptionsEnabled?: boolean
  subscriptionBenefits?: string
}

export interface ClubPhoto {
  id: UUID
  url: string
  caption: string | null
  createdAt: LocalDateTime
}

export interface Review {
  id: UUID
  userId: UUID
  userName: string | null
  rating: number
  comment: string | null
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
  ownerReply: string | null
  ownerReplyAt: LocalDateTime | null
}

export interface ReviewSummary {
  average: number | null
  count: number
  /** star rating ("1"–"5") → number of reviews */
  byRating: Record<string, number>
}

export interface ReviewPage {
  summary: ReviewSummary
  reviews: Page<Review>
  /** subscribers and event attendees may review; never the owner */
  canReview: boolean
  mine: Review | null
}

export interface Membership {
  clubId: UUID
  userId: UUID
  name: string | null
  role: MembershipRole
  joinedAt: LocalDateTime
}

export type PostType = 'ANNOUNCEMENT' | 'EVENT_RECAP'

export interface Post {
  id: UUID
  clubId: UUID
  authorId: UUID
  type: PostType
  title: string
  content: string
  photoUrls: string[]
  eventId: UUID | null
  activity?: {
    id: UUID
    title: string | null
    type: string | null
    distanceKm: number | null
    durationSeconds: number | null
    startTime: LocalDateTime | null
  }
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}

export interface CreatePostRequest {
  type?: PostType
  title: string
  content: string
  photoUrls?: string[]
  eventId?: UUID
  activityId?: UUID
}

export type LeaderboardMetric = 'DISTANCE' | 'TIME' | 'SESSIONS'
export type LeaderboardPeriod = 'WEEK' | 'MONTH' | 'YEAR' | 'ALL'

export interface ClubLeaderboard {
  clubId: UUID
  metric: LeaderboardMetric
  period: LeaderboardPeriod
  from: LocalDateTime | null
  to: LocalDateTime | null
  entries: {
    rank: number
    userId: UUID
    name: string | null
    totalDistanceKm: number
    totalDurationSeconds: number
    sessions: number
  }[]
}

// ---------- owner verification
export type VerificationStatus = 'PENDING' | 'NEEDS_INFO' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED'

export interface VerificationRequest {
  id: UUID
  userId: UUID
  clubName: string
  clubType: ClubType | null
  city: string | null
  socialLinks: Record<string, string>
  proofFiles: string[]
  description: string | null
  contactPhone: string | null
  status: VerificationStatus
  adminNotes: string | null
  reviewedBy: UUID | null
  reviewedAt: LocalDateTime | null
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
  createdClubId?: UUID
}

export interface ApplyVerificationRequest {
  clubName: string
  clubType?: ClubType
  city?: string
  socialLinks?: Record<string, string>
  proofFiles?: string[]
  description?: string
  contactPhone?: string
}

export interface VerificationAudit {
  id: UUID
  requestId: UUID
  actorId: UUID
  action: string
  note: string | null
  createdAt: LocalDateTime
}

// ---------- events / payments / participation
export type EventStatus = 'SCHEDULED' | 'CANCELLED' | 'COMPLETED'
export type RegistrationStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELLED' | 'CHECKED_IN'
export type PaymentProvider = 'MOCK' | 'RAZORPAY'
export type PaymentStatus = 'CREATED' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED' | 'REFUND_PENDING' | 'REFUNDED'

export interface ClubEvent {
  id: UUID
  clubId: UUID
  createdBy: UUID
  title: string
  description: string | null
  location: string | null
  startTime: LocalDateTime
  registrationOpensAt: LocalDateTime
  registrationClosesAt: LocalDateTime
  capacity: number | null
  /** decimal amount, 0 = free */
  price: number
  currency: string
  points: number
  status: EventStatus
  registeredCount: number
  spotsLeft: number | null
  registrationOpen: boolean
  myRegistration: RegistrationStatus | null
  createdAt: LocalDateTime
  imageUrl: string | null
  rules: string | null
  /** only the club's subscribers may register */
  subscribersOnly: boolean
  /** whether registration is possible now, or why not */
  registrationState: RegistrationState
}

export type RegistrationState = 'OPEN' | 'NOT_YET_OPEN' | 'CLOSED' | 'FULL' | 'PAST' | 'CANCELLED'

export type FeedSource = 'FOLLOWING' | 'SUBSCRIBED' | 'NEARBY'

/** An event in the upcoming-events feed. */
export interface EventCard {
  event: ClubEvent
  clubName: string | null
  clubLogoUrl: string | null
  sources: FeedSource[]
  /** to the organising club, when the feed was asked for a location */
  distanceKm: number | null
}

export interface EventRequest {
  clubId?: UUID
  title?: string
  description?: string
  location?: string
  startTime?: LocalDateTime
  registrationOpensAt?: LocalDateTime
  registrationClosesAt?: LocalDateTime | null
  capacity?: number | null
  price?: number
  currency?: string
  points?: number
  rules?: string
  subscribersOnly?: boolean
}

export interface CheckoutInfo {
  paymentId: UUID
  provider: PaymentProvider
  providerOrderId: string
  amount: number
  currency: string
  providerKeyId: string | null
}

export interface RegisterResponse {
  registrationId: UUID
  status: RegistrationStatus
  payment: CheckoutInfo | null
}

export interface Payment {
  id: UUID
  registrationId: UUID
  eventId: UUID
  amount: number
  currency: string
  provider: PaymentProvider
  providerOrderId: string
  status: PaymentStatus
  registrationStatus: RegistrationStatus
  createdAt: LocalDateTime
  refundedAt: LocalDateTime | null
  refundReason: string | null
}

export interface UserEventRegistration {
  registrationId: UUID
  status: RegistrationStatus
  registeredAt: LocalDateTime
  checkedInAt: LocalDateTime | null
  eventId: UUID
  clubId: UUID
  eventTitle: string
  startTime: LocalDateTime
  location: string | null
  eventStatus: EventStatus
}

export interface EventRegistrationRow {
  registrationId: UUID
  userId: UUID
  name: string | null
  status: RegistrationStatus
  registeredAt: LocalDateTime
  checkedInAt: LocalDateTime | null
  /** latest payment (null for free events) */
  paymentId: UUID | null
  paymentStatus: PaymentStatus | null
}

export interface Ticket {
  registrationId: UUID
  eventId: UUID
  eventTitle: string
  startTime: LocalDateTime
  location: string | null
  status: RegistrationStatus
  ticketCode: string
  /** PNG, base64 without data: prefix */
  qrCodeBase64: string
}

export interface CheckInResponse {
  registrationId: UUID
  userId: UUID
  name: string | null
  checkedInAt: LocalDateTime
  pointsAwarded: number
}

export interface Participation {
  userId: UUID
  clubId: UUID | null
  eventsAttended: number
  totalPoints: number
  stars: number
}

export interface MemberParticipation {
  userId: UUID
  name: string | null
  eventsAttended: number
  totalPoints: number
  stars: number
}

export interface MonthlyLeaderboard {
  month: string
  topIndividuals: { rank: number; userId: UUID; name: string | null; points: number; eventsAttended: number }[]
  topClubs: { rank: number; clubId: UUID; name: string | null; points: number; participations: number }[]
}

// ---------- notifications
export interface AppNotification {
  id: UUID
  userId: UUID
  type: string
  message: string
  isRead: boolean
  createdAt: LocalDateTime
}
