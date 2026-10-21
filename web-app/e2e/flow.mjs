// Browser end-to-end test of the main journeys: owner application → admin approval → club (location, logo, photos),
// feed, free + paid events (rules, cover image) → member sign-up with profile setup (photo, location), nearby
// discovery, follow + subscribe, events feed, activity, registration, mock payment, QR ticket → owner check-in →
// member review → owner reply. Uploads need the image store (SeaweedFS in docker compose).
// Needs the backend running with PAYMENT_PROVIDER=mock, the web app (npm run dev), and Chrome/Chromium.
//   APP_URL=http://localhost:5173 ADMIN_EMAIL=... ADMIN_PASSWORD=... CHROME_PATH=/usr/bin/google-chrome npm run e2e
import { randomInt } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright-core'

const DEFAULT_CHROME = {
  darwin: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  win32: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
}[process.platform] ?? '/usr/bin/google-chrome'
const BASE = process.env.APP_URL ?? 'http://localhost:5173'
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@theclub.local'
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? 'change-me-admin'
const SHOTS = new URL('./screenshots/', import.meta.url).pathname
mkdirSync(SHOTS, { recursive: true })
const RUN = Date.now()
const OWNER = { name: 'Olu Owner', email: `olu${RUN}@example.com`, password: 'Str0ng-pass!' }
const MEMBER = { name: 'Mia Member', email: `mia${RUN}@example.com`, password: 'Str0ng-pass!' }
const CLUB = `Marina Runners ${RUN % 100000}`
// Owner and member "devices" report the same spot, so the club is ~0 km from the member. Each run picks its own
// spot, so clubs left by earlier runs aren't "near" this one.
const HERE = { latitude: randomInt(-600_000, 600_000) / 1e4, longitude: randomInt(-1_700_000, 1_700_000) / 1e4 }
// A real 2×2 PNG, so the server's content check accepts it
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFklEQVR4nGNk+M/AwMDAxMDAwMDAAAAMAQEBnSg7CgAAAABJRU5ErkJggg==', 'base64')
const image = (name) => ({ name, mimeType: 'image/png', buffer: PNG })
const PDF = Buffer.from('%PDF-1.4\n1 0 obj << >> endobj\n%%EOF\n')
const problems = []

const pad = (n) => String(n).padStart(2, '0')
const local = (offsetMin) => {
  const d = new Date(Date.now() + offsetMin * 60000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH ?? DEFAULT_CHROME, headless: true })

async function newUser(label, viewport = { width: 1280, height: 860 }) {
  const ctx = await browser.newContext({ viewport, geolocation: HERE, permissions: ['geolocation'] })
  const page = await ctx.newPage()
  page.setDefaultTimeout(10000)
  page.on('console', (m) => { if (m.type() === 'error') problems.push(`[${label} console] ${m.text()}`) })
  page.on('pageerror', (e) => problems.push(`[${label} pageerror] ${e.message}`))
  page.on('response', (r) => {
    if (r.url().includes('/api/') && r.status() >= 500) problems.push(`[${label} ${r.status()}] ${r.request().method()} ${r.url()}`)
  })
  return page
}

let n = 0
async function shot(page, name) { await page.screenshot({ path: `${SHOTS}${String(++n).padStart(2, '0')}-${name}.png`, fullPage: true }) }
function step(msg) { console.log('•', msg) }

/** Signs up; new accounts land on profile setup, which is either completed (photo, location, bio) or skipped. */
async function register(page, u, { setup = false, clubOwner = false } = {}) {
  await page.goto(`${BASE}/register`)
  if (clubOwner) await page.getByRole('radio', { name: 'I run a club' }).click()
  await page.getByLabel('Name', { exact: true }).fill(u.name)
  await page.getByLabel('Email').fill(u.email)
  await page.getByLabel('Password', { exact: true }).fill(u.password)
  await page.getByLabel('Confirm password').fill(u.password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await page.waitForURL(/\/welcome$/)
  if (!setup) {
    await page.getByRole('button', { name: 'Skip for now' }).click()
    // club owner sign-ups continue to their club application
    await page.waitForURL(clubOwner ? /\/become-owner$/ : /\/clubs$/)
    return
  }
  await page.locator('input[type=file]').setInputFiles(image('me.png'))
  await page.locator('.image-picker-frame img[src*="/media/profiles/"]').waitFor()
  await page.getByRole('button', { name: 'Use my current location' }).click()
  await page.getByText('Your approximate location is saved').waitFor()
  await page.getByLabel('About you').fill('Morning runner, 5:30/km')
  await shot(page, 'profile-setup')
  await page.getByRole('button', { name: 'Finish and find clubs' }).click()
  await page.waitForURL(/\/clubs$/)
}

async function login(page, email, password) {
  await page.goto(`${BASE}/login`)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
  await page.waitForURL((url) => !url.pathname.startsWith('/login'))
}

async function logout(page) {
  await page.getByRole('button', { name: 'Account menu' }).click()
  await page.getByRole('button', { name: 'Log out' }).click()
  await page.waitForURL(/\/login/)
}

try {
  // ---------- owner signs up as a club owner and applies for the club
  const owner = await newUser('owner')
  step('owner signs up as a club owner'); await register(owner, OWNER, { clubOwner: true })
  step('owner submits the club details and a proof document')
  await owner.getByLabel('Club name').fill(CLUB)
  await owner.getByLabel('Category').selectOption('RUNNING')
  await owner.getByLabel('City').fill('Chennai')
  await owner.getByLabel('Address or meeting point').fill('Lighthouse, Marina Beach')
  await owner.getByLabel('Contact email').fill(`club${RUN}@example.com`)
  await owner.getByRole('button', { name: 'Submit application' }).click()
  await owner.getByText('Pending review').first().waitFor()
  await owner.getByLabel('Upload a document').setInputFiles({ name: 'club-registration.pdf', mimeType: 'application/pdf', buffer: PDF })
  await owner.getByText('club-registration.pdf').first().waitFor()
  await shot(owner, 'owner-application-submitted')
  // nothing to manage before an admin approves the club
  await owner.goto(`${BASE}/owner`)
  await owner.getByText('No verified clubs yet').waitFor()
  await logout(owner)

  step('admin asks for changes')
  await login(owner, ADMIN_EMAIL, ADMIN_PASSWORD)
  await owner.goto(`${BASE}/admin/verification`)
  await owner.getByText(CLUB).first().click()
  const [document] = await Promise.all([
    owner.waitForEvent('download'),
    owner.getByRole('button', { name: 'Download' }).first().click(),
  ])
  if (document.suggestedFilename() !== 'club-registration.pdf') throw new Error(`downloaded ${document.suggestedFilename()}`)
  await shot(owner, 'admin-review')
  await owner.getByRole('button', { name: 'Ask for changes' }).click()
  await owner.getByLabel('What should the applicant correct or add?').fill("Please add the club's Instagram page.")
  await owner.getByRole('button', { name: 'Send to the applicant' }).click()
  await owner.getByRole('dialog').waitFor({ state: 'detached' })
  await logout(owner)

  step('owner updates the application and resubmits')
  await login(owner, OWNER.email, OWNER.password)
  await owner.waitForURL(/\/become-owner$/)
  await owner.getByText("Please add the club's Instagram page.").waitFor()
  await owner.getByRole('button', { name: 'Update details' }).click()
  await owner.getByLabel('Instagram').fill('https://instagram.com/marinarunners')
  await owner.getByRole('button', { name: 'Save changes' }).click()
  await owner.getByText('Application updated').first().waitFor()
  await owner.getByRole('button', { name: 'Resubmit for review' }).click()
  await owner.getByText('Sent back for review').first().waitFor()
  await logout(owner)

  step('admin approves under the official name')
  await login(owner, ADMIN_EMAIL, ADMIN_PASSWORD)
  await owner.goto(`${BASE}/admin/verification`)
  await owner.getByText(CLUB).first().click()
  await owner.getByRole('button', { name: 'Approve', exact: true }).click()
  await owner.getByRole('dialog').getByRole('button', { name: 'Approve and create the club' }).click()
  await owner.getByRole('dialog').waitFor({ state: 'detached' })
  await logout(owner)

  // ---------- owner lands on the dashboard of the verified club
  step('owner opens the dashboard and completes the club profile')
  await login(owner, OWNER.email, OWNER.password)
  await owner.waitForURL(/\/owner$/)
  await owner.getByRole('heading', { name: CLUB }).waitFor()
  await owner.getByText('Complete your club profile').waitFor()
  await shot(owner, 'owner-dashboard')
  const clubId = await owner.getByRole('link', { name: 'Public page' }).getAttribute('href').then((h) => h.split('/').pop())
  const clubUrl = `${BASE}/clubs/${clubId}`
  await owner.getByRole('button', { name: 'Complete the profile' }).click()
  const edit = owner.getByRole('dialog')
  await edit.locator('input[type=file]').nth(0).setInputFiles(image('logo.png'))
  await owner.getByText('Logo updated').first().waitFor()
  await edit.locator('input[type=file]').nth(1).setInputFiles(image('cover.png'))
  await owner.getByText('Cover photo updated').first().waitFor()
  await edit.getByLabel('Description').fill('Sunday long runs and weekday tempo sessions on the Marina.')
  await edit.getByRole('button', { name: 'Use my current location' }).click()
  await owner.waitForFunction((lat) => document.querySelector('#club-lat')?.value.startsWith(String(lat).slice(0, 4)), HERE.latitude)
  await edit.getByLabel('Club rules').fill('Lights and reflective kit before sunrise. Run on the left.')
  await edit.getByLabel('What subscribers get').fill('Members-only long runs and pacers')
  await edit.getByRole('button', { name: 'Save profile' }).click()
  await owner.getByText('Profile saved').first().waitFor()
  await owner.goto(clubUrl)
  await owner.getByRole('heading', { level: 1, name: CLUB }).waitFor()
  await owner.getByText('Verified', { exact: true }).first().waitFor()
  await owner.locator('img.club-cover').waitFor()
  await owner.getByRole('link', { name: 'View on map' }).waitFor()
  await owner.locator(`.club-hero img[src*="/media/clubs/${clubId}/"]`).waitFor()
  await shot(owner, 'club-page-owner')

  step('owner adds a gallery photo')
  await owner.goto(`${clubUrl}?tab=photos`)
  await owner.getByLabel('Caption').fill('Sunrise at the lighthouse')
  await owner.locator('input[type=file]').setInputFiles(image('sunrise.png'))
  await owner.getByText('Photo added').first().waitFor()
  await owner.locator('.photo-tile img').first().waitFor()
  await shot(owner, 'club-photos')
  await owner.goto(clubUrl)

  step('owner posts an announcement')
  await owner.getByLabel('Title').fill('Welcome to the club!')
  await owner.getByLabel('Message', { exact: true }).fill('First group run this Sunday at 6am. All paces welcome.')
  await owner.getByRole('button', { name: 'Post', exact: true }).click()
  await owner.getByText('Welcome to the club!').nth(0).waitFor()

  step('owner creates a free event')
  await owner.goto(`${BASE}/clubs/${clubId}/events/new`)
  await owner.getByLabel('Title').fill('Sunday Long Run')
  await owner.getByLabel('Location').fill('Marina Beach lighthouse')
  await owner.getByLabel('Rules and instructions (optional)').fill('Bring water. Pace groups: 5, 6 and 7 min/km.')
  await owner.getByLabel('Starts at').fill(local(60))
  await owner.getByLabel('Opens at').fill(local(-5))
  await owner.getByLabel('Capacity (optional)').fill('30')
  await shot(owner, 'event-form')
  await owner.getByRole('button', { name: 'Save and review' }).click()
  await owner.waitForURL(/\/events\/[0-9a-f-]{36}$/)
  const freeEventUrl = owner.url()
  await owner.getByText('Pace groups: 5, 6 and 7 min/km.').waitFor()
  await owner.getByText('Draft: only you can see this event').waitFor()
  await shot(owner, 'event-draft-review')
  await owner.getByRole('button', { name: 'Publish event' }).click()
  await owner.getByText(/^Published/).first().waitFor()
  await shot(owner, 'event-page-owner')

  step('owner adds a cover image to the event')
  await owner.goto(`${freeEventUrl}/edit`)
  await owner.locator('input[type=file]').setInputFiles(image('cover.png'))
  await owner.getByText('Image updated').first().waitFor()
  await owner.goto(freeEventUrl)
  await owner.locator('img.event-cover').waitFor()

  step('owner creates a paid event')
  await owner.goto(`${BASE}/clubs/${clubId}/events/new`)
  await owner.getByLabel('Title').fill('Paid 10K Time Trial')
  await owner.getByLabel('Starts at').fill(local(90))
  await owner.getByLabel('Opens at').fill(local(-5))
  await owner.getByLabel('Price').fill('499')
  await owner.getByRole('button', { name: 'Save and review' }).click()
  await owner.waitForURL(/\/events\/[0-9a-f-]{36}$/)
  const paidEventUrl = owner.url()
  await owner.getByRole('button', { name: 'Publish event' }).click()
  await owner.getByText(/^Published/).first().waitFor()

  // ---------- member joins, logs activity, registers
  const member = await newUser('member')
  step('member signs up, sets up their profile and finds the club nearby')
  await register(member, MEMBER, { setup: true })
  const nearby = member.locator('section', { has: member.getByRole('heading', { name: 'Near you' }) })
  await nearby.getByText(CLUB).first().waitFor()
  await shot(member, 'discover')
  await nearby.getByText(CLUB).first().click()
  await member.getByRole('heading', { level: 1, name: CLUB }).waitFor()

  step('member follows, then subscribes')
  await member.getByRole('button', { name: 'Follow', exact: true }).click()
  await member.getByRole('button', { name: 'Following' }).waitFor()
  await member.getByRole('button', { name: 'Subscribe', exact: true }).click()
  await member.getByRole('dialog').getByText('Members-only long runs and pacers').waitFor()
  await member.getByRole('button', { name: 'Subscribe (free)' }).click()
  await member.getByRole('button', { name: 'Unsubscribe' }).waitFor()
  await shot(member, 'club-page-member')

  step('member sees the club\'s events in the upcoming feed')
  await member.goto(`${BASE}/events`)
  // earlier runs' clubs sit at the same spot, so pick this run's club
  const card = member.locator('.event-card', { hasText: CLUB }).filter({ hasText: 'Sunday Long Run' })
  await card.waitFor()
  await card.getByText('Registration open').waitFor()
  await card.getByText('Following').waitFor()
  await shot(member, 'events-feed')

  step('member logs an activity')
  await member.goto(`${BASE}/activities?log=1`)
  await member.getByLabel('Title').fill('Morning tempo run')
  await member.getByLabel('Distance (km)').fill('10.5')
  await member.getByLabel('Hours').fill('0')
  await member.getByLabel('Minutes').fill('52')
  await member.getByLabel('Seconds').fill('30')
  await member.getByRole('button', { name: /save|log activity|add/i }).first().click()
  await member.getByText('Morning tempo run').first().waitFor()
  await shot(member, 'activities')

  step('member reloads the page and stays signed in (session restored from the refresh cookie)')
  await member.reload()
  await member.getByText('Morning tempo run').first().waitFor()
  if (member.url().includes('/login')) throw new Error('session was not restored after reload')

  step('member registers for the free event and opens the ticket')
  await member.goto(freeEventUrl)
  await member.getByRole('button', { name: /^register/i }).click()
  await member.getByText(/you're going|confirmed/i).first().waitFor()
  await shot(member, 'event-registered')
  await member.getByRole('link', { name: /view ticket/i }).click()
  await member.waitForURL(/\/tickets\//)
  await member.locator('img[src^="data:image/png"]').waitFor()
  const code = (await member.locator('.ticket-code').first().textContent()).trim()
  await shot(member, 'ticket')

  step('member pays for the paid event (mock)')
  await member.goto(paidEventUrl)
  await member.getByRole('button', { name: /^register/i }).click()
  await member.getByRole('dialog').waitFor()
  await shot(member, 'payment-modal')
  await member.getByRole('dialog').getByRole('button', { name: /^pay/i }).click()
  await member.waitForURL(/\/tickets\//)
  await member.locator('img[src^="data:image/png"]').waitFor()

  step('owner refunds the paid registration')
  await owner.goto(paidEventUrl)
  await owner.getByRole('button', { name: `Refund ${MEMBER.name}` }).click()
  await owner.getByLabel('Reason (optional)').fill('Change of plans')
  await owner.getByRole('dialog').getByRole('button', { name: 'Refund payment' }).click()
  await owner.getByRole('dialog').waitFor({ state: 'detached' })
  await owner.getByText(/^Refund(ed| pending)$/).first().waitFor()
  await shot(owner, 'refunded')

  step('member sees the refunded registration as cancelled')
  await member.goto(`${BASE}/tickets`)
  await member.getByRole('tab', { name: 'Past' }).click()
  await member.locator('.ticket-card', { hasText: 'Paid 10K Time Trial' }).getByText('Cancelled').first().waitFor()
  await shot(member, 'tickets-refunded')

  // ---------- owner checks the member in by code
  step('owner checks the member in')
  await owner.goto(`${freeEventUrl}/check-in`)
  await owner.getByLabel('Ticket code').fill(code)
  await owner.getByRole('button', { name: 'Check in' }).click()
  await owner.getByText(MEMBER.name).first().waitFor()
  await owner.locator('.stat', { hasText: 'Checked in' }).locator('.stat-value', { hasText: '1' }).waitFor()
  await shot(owner, 'check-in-success')
  await owner.getByLabel('Ticket code').fill(code)
  await owner.getByRole('button', { name: 'Check in' }).click()
  await owner.getByText(/already checked in/i).first().waitFor()

  step('owner sees registrations and check-ins on the dashboard')
  await owner.goto(`${BASE}/owner`)
  const freeRow = owner.locator('tr', { hasText: 'Sunday Long Run' })
  await freeRow.getByRole('link', { name: /Check in/ }).waitFor()
  await freeRow.locator('td', { hasText: /^1/ }).first().waitFor()
  await shot(owner, 'owner-dashboard-event-day')

  step('member reviews the club; the owner replies')
  await member.goto(`${clubUrl}?tab=reviews`)
  await member.getByTitle('4 stars').click()
  await member.getByLabel('Comment').fill('Friendly pacers and a great Sunday route.')
  await member.getByRole('button', { name: 'Post review' }).click()
  await member.getByText('Thanks for your review').first().waitFor()
  await member.getByText('1 review', { exact: true }).waitFor()
  await owner.goto(`${clubUrl}?tab=reviews`)
  await owner.getByRole('button', { name: 'Reply' }).first().click()
  await owner.getByLabel('Reply').fill('Thanks Mia, see you Sunday!')
  await owner.getByRole('button', { name: 'Save reply' }).click()
  await owner.getByText(`Reply from ${CLUB}`).waitFor()
  await shot(owner, 'club-reviews')

  step('owner posts an event recap')
  await owner.goto(clubUrl)
  await owner.getByLabel('Post type').selectOption({ label: /recap/i }).catch(async () => owner.getByLabel('Post type').selectOption('EVENT_RECAP'))
  await owner.getByLabel('Event').selectOption({ index: 1 })
  await owner.getByLabel('Title').fill('Sunday Long Run recap')
  await owner.getByLabel('Message', { exact: true }).fill('Great turnout today!')
  await owner.getByRole('button', { name: 'Post', exact: true }).click()
  await owner.getByText('Sunday Long Run recap').first().waitFor()

  step('member posts in the feed with a photo; the owner likes and comments')
  await member.goto(clubUrl)
  await member.getByLabel('Title').fill('First 10k with the club')
  await member.getByLabel('Message', { exact: true }).fill('Thanks for the pacing, everyone!')
  await member.getByLabel('Add photos').setInputFiles(image('run.png'))
  await member.locator('.upload-thumb img').first().waitFor()
  const created = member.waitForResponse((r) => r.url().endsWith(`/clubs/${clubId}/posts`) && r.request().method() === 'POST')
  await member.getByRole('button', { name: 'Post', exact: true }).click()
  const memberPostId = (await (await created).json()).id
  await member.getByText('Posted to the club feed').first().waitFor()
  await owner.goto(clubUrl)
  const memberPost = owner.locator('.card', { hasText: 'First 10k with the club' })
  await memberPost.getByRole('button', { name: 'Like', exact: true }).click()
  await memberPost.getByRole('button', { name: 'Unlike' }).waitFor()
  await memberPost.getByRole('button', { name: /Comment/ }).first().click()
  await memberPost.getByLabel('Write a comment').fill('Brilliant run, Mia!')
  await memberPost.locator('.comment-form').getByRole('button').click()
  await memberPost.getByText('Brilliant run, Mia!').waitFor()
  await shot(owner, 'feed-likes-comments')

  step('someone who is not signed in opens the shared post link')
  const visitor = await newUser('visitor')
  await visitor.goto(`${BASE}/clubs/${clubId}/posts/${memberPostId}`)
  await visitor.getByText('First 10k with the club').waitFor()
  await visitor.getByText(/Join The Club to see the whole post/).waitFor()
  await shot(visitor, 'shared-post-preview')
  await visitor.context().close()

  step('club chat: the member and the owner talk live')
  await member.goto(`${clubUrl}?tab=chat`)
  await owner.goto(`${clubUrl}?tab=chat`)
  await member.getByLabel('Message', { exact: true }).fill('Anyone up for hills on Wednesday?')
  await member.getByRole('button', { name: 'Send', exact: true }).click()
  await owner.getByText('Anyone up for hills on Wednesday?').waitFor()
  await owner.getByLabel('Message', { exact: true }).fill('Count me in')
  await owner.keyboard.press('Enter')
  await member.getByText('Count me in').waitFor()
  await shot(member, 'club-chat')

  step('member messages the owner directly from the Members tab')
  await member.goto(`${clubUrl}?tab=members`)
  await member.locator('.list-item', { hasText: OWNER.name }).getByRole('button', { name: 'Message' }).click()
  await member.waitForURL(/\/messages\//)
  await member.getByLabel('Message', { exact: true }).fill('Can I bring a friend on Sunday?')
  await member.keyboard.press('Enter')
  await member.locator('.chat-row.mine', { hasText: 'Can I bring a friend on Sunday?' }).waitFor()
  await owner.goto(`${BASE}/messages`)
  await owner.getByRole('link', { name: /Messages \(\d+ unread\)/ }).waitFor()
  await owner.locator('.inbox-item', { hasText: MEMBER.name }).click()
  await owner.getByText('Can I bring a friend on Sunday?').waitFor()
  await shot(owner, 'messages')

  // ---------- member sees results
  step('member checks leaderboards, tickets, notifications, home')
  await member.goto(`${clubUrl}?tab=leaderboard`)
  await member.getByText(MEMBER.name).first().waitFor()
  await shot(member, 'club-leaderboard')
  await member.goto(`${BASE}/leaderboard`)
  // Every run earns the same points, so on a reused database this run's club may be tied outside the top 10:
  // check that the platform leaderboard renders entries rather than where this club ranks
  await member.getByText(/Marina Runners|Chennai Runners/).first().waitFor()
  await shot(member, 'monthly-leaderboard')
  await member.goto(`${BASE}/tickets`)
  await shot(member, 'my-tickets')
  await member.goto(`${BASE}/notifications`)
  await member.getByText(/registered/i).first().waitFor()
  await shot(member, 'notifications')
  await member.goto(`${BASE}/`)
  await member.waitForTimeout(800)
  await shot(member, 'member-home')

  // ---------- mobile layout
  step('mobile viewport')
  const mobile = await newUser('mobile', { width: 390, height: 844 })
  await login(mobile, MEMBER.email, MEMBER.password)
  await mobile.waitForTimeout(800)
  await shot(mobile, 'mobile-home')
  await mobile.goto(clubUrl)
  await mobile.getByRole('heading', { level: 1, name: CLUB }).waitFor()
  await shot(mobile, 'mobile-club')
  const overflow = await mobile.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
  if (overflow) problems.push('[mobile] horizontal overflow on club page')

  step('forgot password shows the generic confirmation')
  const guest = await newUser('guest')
  await guest.goto(`${BASE}/login`)
  await guest.getByRole('link', { name: 'Forgot password?' }).click()
  await guest.waitForURL(/\/forgot-password/)
  await guest.getByRole('button', { name: 'Send reset link' }).waitFor()
  await guest.getByLabel('Email').fill(MEMBER.email)
  await guest.getByRole('button', { name: 'Send reset link' }).click()
  await guest.getByText('Check your email').waitFor()
  await shot(guest, 'forgot-password')

  step('owner signs out of all devices')
  await owner.goto(`${BASE}/profile`)
  await owner.getByRole('button', { name: 'Sign out of all devices' }).click()
  await owner.getByRole('dialog').getByRole('button', { name: 'Sign out everywhere' }).click()
  await owner.waitForURL(/\/login/)
  await owner.goto(`${BASE}/`)
  await owner.waitForURL(/\/login/)

  console.log('FLOW COMPLETE')
} catch (e) {
  process.exitCode = 1
  console.log('FAILED:', e.message.split('\n').slice(0, 6).join('\n'))
  for (const ctx of browser.contexts()) for (const p of ctx.pages()) await p.screenshot({ path: `${SHOTS}zz-failure-${browser.contexts().indexOf(ctx)}.png`, fullPage: true }).catch(() => {})
}
// 409 = the deliberate double check-in; 401 = signed-out page loads probing /auth/refresh for a session
const unexpected = problems.filter((p) => !p.includes('status of 409') && !p.includes('status of 401'))
if (unexpected.length) process.exitCode = 1
console.log(unexpected.length ? 'PROBLEMS:\n' + unexpected.join('\n') : 'No console errors or 5xx responses')
await browser.close()
