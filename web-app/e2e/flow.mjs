// Browser end-to-end test of the main Phase 1 + 2 journeys: owner application → admin approval → club,
// feed, free + paid events → member join, activity, registration, mock payment, QR ticket → owner check-in.
// Needs the backend running with PAYMENT_PROVIDER=mock, the web app (npm run dev), and Chrome/Chromium.
//   APP_URL=http://localhost:5173 ADMIN_EMAIL=... ADMIN_PASSWORD=... CHROME_PATH=/usr/bin/google-chrome npm run e2e
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
const problems = []

const pad = (n) => String(n).padStart(2, '0')
const local = (offsetMin) => {
  const d = new Date(Date.now() + offsetMin * 60000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH ?? DEFAULT_CHROME, headless: true })

async function newUser(label, viewport = { width: 1280, height: 860 }) {
  const ctx = await browser.newContext({ viewport })
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

async function register(page, u) {
  await page.goto(`${BASE}/register`)
  await page.getByLabel('Name', { exact: true }).fill(u.name)
  await page.getByLabel('Email').fill(u.email)
  await page.getByLabel('Password', { exact: true }).fill(u.password)
  await page.getByLabel('Confirm password').fill(u.password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await page.waitForURL(`${BASE}/`)
}

async function login(page, email, password) {
  await page.goto(`${BASE}/login`)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
  await page.waitForURL(`${BASE}/`)
}

async function logout(page) {
  await page.getByRole('button', { name: 'Account menu' }).click()
  await page.getByRole('button', { name: 'Log out' }).click()
  await page.waitForURL(/\/login/)
}

try {
  // ---------- owner signs up and applies
  const owner = await newUser('owner')
  step('owner registers'); await register(owner, OWNER); await shot(owner, 'owner-home')
  step('owner applies to become a club owner')
  await owner.goto(`${BASE}/become-owner`)
  await owner.getByLabel('Club name').fill(CLUB)
  await owner.getByLabel('City').fill('Chennai')
  await owner.getByRole('button', { name: 'Submit application' }).click()
  await owner.getByText(/pending|submitted|review/i).first().waitFor()
  await shot(owner, 'owner-application-submitted')
  await logout(owner)

  // ---------- admin approves (and creates the club)
  step('admin approves')
  await login(owner, ADMIN_EMAIL, ADMIN_PASSWORD)
  await owner.goto(`${BASE}/admin/verification`)
  await owner.getByText(CLUB).first().click()
  await shot(owner, 'admin-review')
  await owner.getByRole('button', { name: 'Approve' }).first().click()
  await owner.getByRole('dialog').getByRole('button', { name: 'Approve' }).click()
  await owner.getByRole('dialog').waitFor({ state: 'detached' })
  await logout(owner)

  // ---------- owner activates role, lands on club
  step('owner sees verified status and opens the club created on approval')
  await login(owner, OWNER.email, OWNER.password)
  await shot(owner, 'owner-home-approved')
  // a fresh login already carries CLUB_OWNER; the approval created the club
  await owner.goto(`${BASE}/become-owner`)
  await owner.getByText("You're a verified club owner").waitFor()
  await owner.goto(`${BASE}/clubs`)
  await owner.getByText(CLUB).first().click()
  await owner.waitForURL(/\/clubs\/[0-9a-f-]{36}/)
  const clubUrl = owner.url().split('?')[0]
  const clubId = clubUrl.split('/').pop()
  await owner.getByRole('heading', { name: CLUB }).waitFor()
  await shot(owner, 'club-page-owner')

  step('owner posts an announcement')
  await owner.getByLabel('Title').fill('Welcome to the club!')
  await owner.getByLabel(/content|message|what/i).first().fill('First group run this Sunday at 6am. All paces welcome.')
  await owner.getByRole('button', { name: 'Post', exact: true }).click()
  await owner.getByText('Welcome to the club!').nth(0).waitFor()

  step('owner creates a free event')
  await owner.goto(`${BASE}/clubs/${clubId}/events/new`)
  await owner.getByLabel('Title').fill('Sunday Long Run')
  await owner.getByLabel('Location').fill('Marina Beach lighthouse')
  await owner.getByLabel('Starts at').fill(local(60))
  await owner.getByLabel('Opens at').fill(local(-5))
  await owner.getByLabel('Capacity (optional)').fill('30')
  await shot(owner, 'event-form')
  await owner.getByRole('button', { name: /create event|save|create/i }).last().click()
  await owner.waitForURL(/\/events\/[0-9a-f-]{36}$/)
  const freeEventUrl = owner.url()
  await shot(owner, 'event-page-owner')

  step('owner creates a paid event')
  await owner.goto(`${BASE}/clubs/${clubId}/events/new`)
  await owner.getByLabel('Title').fill('Paid 10K Time Trial')
  await owner.getByLabel('Starts at').fill(local(90))
  await owner.getByLabel('Opens at').fill(local(-5))
  await owner.getByLabel('Price').fill('499')
  await owner.getByRole('button', { name: /create event|save|create/i }).last().click()
  await owner.waitForURL(/\/events\/[0-9a-f-]{36}$/)
  const paidEventUrl = owner.url()

  // ---------- member joins, logs activity, registers
  const member = await newUser('member')
  step('member registers and finds the club')
  await register(member, MEMBER)
  await member.goto(`${BASE}/clubs`)
  await member.getByPlaceholder('Search clubs by name…').fill(CLUB)
  await member.getByText(CLUB).first().click()
  await member.getByRole('button', { name: /join/i }).first().click()
  await member.getByRole('button', { name: /leave/i }).waitFor()
  await shot(member, 'club-page-member')

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

  step('owner posts an event recap')
  await owner.goto(clubUrl)
  await owner.getByLabel('Post type').selectOption({ label: /recap/i }).catch(async () => owner.getByLabel('Post type').selectOption('EVENT_RECAP'))
  await owner.getByLabel('Event').selectOption({ index: 1 })
  await owner.getByLabel('Title').fill('Sunday Long Run recap')
  await owner.getByLabel(/content|message|what/i).first().fill('Great turnout today!')
  await owner.getByRole('button', { name: 'Post', exact: true }).click()
  await owner.getByText('Sunday Long Run recap').first().waitFor()

  // ---------- member sees results
  step('member checks leaderboards, tickets, notifications, home')
  await member.goto(`${clubUrl}?tab=leaderboard`)
  await member.getByText(MEMBER.name).first().waitFor()
  await shot(member, 'club-leaderboard')
  await member.goto(`${BASE}/leaderboard`)
  await member.getByText(CLUB).first().waitFor()
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
  await mobile.getByRole('heading', { name: CLUB }).waitFor()
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
