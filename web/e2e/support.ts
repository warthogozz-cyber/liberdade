// Two per-IP budgets limit how fast the suite may enter, and every request here comes from
// 127.0.0.1, so `support.ts` keeps a log of what it spent and waits for the window instead of
// loosening a limit:
//  - sign-in, 20 per minute (D10, `LoginRateLimitFilter`): only `/oauth2/authorization/**` (a Google
//    sign-in or a Google re-authentication: 1; the callback is not counted), `/webauthn/authenticate/options`
//    and `/login/webauthn` (a passkey sign-in: 2). Registering a passkey and accepting an invitation
//    are not counted here.
//  - public invitations, 10 per minute (`invitationPublicRateLimiter`): `POST /v1/invitations/preview`
//    and `POST /v1/invitations/accept` count 1 each.
// The log lives in one worker's memory (the config runs a single worker); a restarted worker starts
// with an empty log, which is the same risk the suite already had.
import {
  expect,
  test as base,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  type BrowserContextOptions,
  type CDPSession,
  type Locator,
  type Page,
} from '@playwright/test'
import { API_URL, WEB_URL } from './urls.ts'

export { expect }

/**
 * What a test's browser contexts saw that must not happen: a Content Security Policy violation in
 * the console, or a response with a 5xx status. It is listened to per `BrowserContext`, so pages
 * opened later and the contexts a test makes itself are covered too.
 */
function watchContext(context: BrowserContext, findings: string[]): void {
  context.on('console', (message) => {
    if (message.text().includes('Content Security Policy')) {
      findings.push(`CSP: ${message.text()}`)
    }
  })
  context.on('response', (response) => {
    if (response.status() >= 500) {
      findings.push(`${response.status()} ${response.request().method()} ${response.url()}`)
    }
  })
}

/** Whether the test running now asked for the first visit tips (`tips: true`); false in a hook. */
let activeTips = false

/** Turns the first visit tips off in a context (they are on by default for people) unless `tips` is set. */
async function silenceTips(context: BrowserContext, tips: boolean): Promise<void> {
  if (tips) return
  await context.addInitScript(() => {
    try {
      localStorage.setItem('liberdade.tips', 'off')
    } catch {
      // No storage: the tips stay on, as they do for a person without it.
    }
  })
}

/** The findings of the test running now, for the contexts that `withContext` opens inside it. */
let activeFindings: string[] | undefined

function failOnFindings(findings: string[]): void {
  if (findings.length === 0) return
  throw new Error(
    `The browser saw ${findings.length} CSP violation(s) or 5xx response(s):\n${findings.join('\n')}`,
  )
}

/**
 * `test` with a guard on every test: it fails when any context of the test, the default one and the
 * ones made through `browser.newContext` or `withContext`, logged a CSP violation or got a 5xx.
 */
export const test = base.extend<{ cspAndServerErrorGuard: void; tips: boolean }>({
  // The first visit tips stay off unless a test or describe block sets `tips: true`.
  tips: [false, { option: true }],
  context: async ({ context, tips }, provide) => {
    await silenceTips(context, tips)
    await provide(context)
  },
  cspAndServerErrorGuard: [
    async ({ browser, context, tips }, provide) => {
      const findings: string[] = []
      watchContext(context, findings)
      const newContext = browser.newContext.bind(browser)
      browser.newContext = async (...args: Parameters<Browser['newContext']>) => {
        const created = await newContext(...args)
        watchContext(created, findings)
        await silenceTips(created, tips)
        return created
      }
      activeFindings = findings
      activeTips = tips
      try {
        await provide()
      } finally {
        activeFindings = undefined
        activeTips = false
        browser.newContext = newContext
      }
      failOnFindings(findings)
    },
    { auto: true },
  ],
})

const WINDOW_MS = 60_000
/**
 * Extra time past the window. It covers the server's own clock and, more, the delay between
 * reserving and the request reaching the server: a reservation is made before the click, and the
 * request that follows a Google round trip arrives seconds later. Two arrivals must still be a
 * whole window apart, so the margin is longer than that trip takes.
 */
const WINDOW_MARGIN_MS = 6_000

class Budget {
  private readonly spent: number[] = []
  private readonly limit: number

  constructor(limit: number) {
    this.limit = limit
  }

  /** Records `cost` attempts, first waiting until they fit in the last minute. */
  async reserve(cost: number): Promise<void> {
    if (cost > this.limit) throw new Error(`A cost of ${cost} never fits a limit of ${this.limit}`)
    for (;;) {
      const now = Date.now()
      while (this.spent.length > 0 && now - this.spent[0]! >= WINDOW_MS + WINDOW_MARGIN_MS) {
        this.spent.shift()
      }
      if (this.spent.length + cost <= this.limit) break
      const oldest = this.spent[this.spent.length + cost - this.limit - 1]!
      await new Promise((resolve) =>
        setTimeout(resolve, oldest + WINDOW_MS + WINDOW_MARGIN_MS - now),
      )
    }
    const at = Date.now()
    for (let i = 0; i < cost; i += 1) this.spent.push(at)
  }
}

export const signInBudget = new Budget(20)
export const invitationBudget = new Budget(10)
export type E2EUser = { sub: string; claims: Record<string, string> }

/** A new person each time, so each test gets its own family without cleaning the database (D11). */
export function newUser(firstName = 'Ana'): E2EUser {
  return {
    sub: 'e2e-' + crypto.randomUUID(),
    claims: {
      name: `${firstName} Teste`,
      given_name: firstName,
      family_name: 'Teste',
      email: `${firstName.toLowerCase()}@example.com`,
    },
  }
}

/**
 * Fills the test provider's form and submits it. It expects no page: the caller says where it ends.
 * On the iPhone (WebKit) the claims sometimes landed in the focused username field, so the person
 * signed in without a name; both fields are read back and filled again until they hold their values.
 */
export async function completeGoogleSignIn(page: Page, user: E2EUser): Promise<void> {
  const username = page.locator('[name="username"]')
  const claims = page.locator('[name="claims"]')
  const claimsText = JSON.stringify(user.claims)
  await expect(async () => {
    await username.fill(user.sub)
    await claims.fill(claimsText)
    await expect(username).toHaveValue(user.sub, { timeout: 1_000 })
    await expect(claims).toHaveValue(claimsText, { timeout: 1_000 })
  }).toPass()
  await page.getByRole('button', { name: 'Sign-in' }).click()
}

/** Signs in through the test provider that stands in for Google, landing on the welcome screen. */
export async function signInWithGoogle(page: Page, user: E2EUser): Promise<void> {
  await signInBudget.reserve(1)
  await page.goto('/entrar')
  await page.getByRole('button', { name: 'Entrar com Google' }).click()
  await completeGoogleSignIn(page, user)
  await acceptDocuments(page)
  await page.waitForURL('**/boas-vindas')
}

/** A person with a new account accepts the terms and the policy before any screen of the family opens. */
export async function acceptDocuments(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { level: 1, name: 'Antes de começar' })).toBeVisible()
  await page.getByRole('checkbox', { name: /Li e aceito os termos/ }).check()
  await page.getByRole('button', { name: 'Continuar' }).click()
}

/** Creates the family with the suggested name, landing on the new expense. */
export async function createFamily(page: Page, firstName = 'Ana'): Promise<void> {
  await expect(page.getByRole('heading', { level: 1, name: `Olá, ${firstName}! 👋` })).toBeVisible()
  await page.getByRole('button', { name: 'Começar' }).click()
  await page.waitForURL('**/lancar')
}

/**
 * Waits until the service worker controls the page: only then do the screens that load later (lazy
 * chunks) come from the precache once the test goes offline.
 */
export async function waitForOfflineReady(page: Page): Promise<void> {
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready
    if (navigator.serviceWorker.controller) return
    await new Promise((resolve) => {
      navigator.serviceWorker.addEventListener('controllerchange', resolve, { once: true })
    })
  })
}

/** A virtual passkey device on the page's browser context, so WebAuthn works without a person. */
export async function addVirtualAuthenticator(
  page: Page,
): Promise<{ cdp: CDPSession; authenticatorId: string }> {
  // The virtual device comes from Chromium's DevTools protocol, which WebKit does not have: a test
  // that needs a passkey runs on the Chromium projects only.
  test.skip(
    page.context().browser()?.browserType().name() !== 'chromium',
    'WebKit has no virtual passkey device',
  )
  const cdp = await page.context().newCDPSession(page)
  await cdp.send('WebAuthn.enable')
  const { authenticatorId } = await cdp.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      protocol: 'ctap2',
      transport: 'internal',
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true,
    },
  })
  return { cdp, authenticatorId }
}

/** The signed-in page's own family id, read from the API the way the app does. */
export async function currentWorkspaceId(page: Page): Promise<string> {
  const response = await page.request.get(`${API_URL}/v1/me`)
  expect(response.ok()).toBe(true)
  const me = (await response.json()) as { workspaces: { id: string }[] }
  return me.workspaces[0]!.id
}

/** Switches every module of the page's family on or off through the API, with the page's CSRF token. */
export async function setModules(page: Page, enabled: boolean): Promise<void> {
  const api = API_URL
  const ws = await currentWorkspaceId(page)
  const csrf = (await (await page.request.get(`${api}/v1/csrf`)).json()) as {
    headerName: string
    token: string
  }
  const modules = (await (await page.request.get(`${api}/v1/workspaces/${ws}/modules`)).json()) as {
    key: string
  }[]
  for (const { key } of modules) {
    const response = await page.request.put(`${api}/v1/workspaces/${ws}/modules/${key}`, {
      headers: { [csrf.headerName]: csrf.token },
      data: { enabled },
    })
    expect(response.ok(), `PUT module ${key}`).toBe(true)
  }
}

/** The same browser settings the config gives every test, for the extra contexts of one test. */
export const CONTEXT_OPTIONS = {
  locale: 'pt-BR',
  timezoneId: 'America/Fortaleza',
  ignoreHTTPSErrors: true,
} as const

/**
 * For a test that stubs the API with `page.route`: on WebKit, the requests of a page the service worker
 * controls go through the worker, where `page.route` does not see them. Playwright's advice is to block
 * the worker in such tests; the offline test, which needs it, does not stub.
 */
export const NO_SERVICE_WORKER = { serviceWorkers: 'block' } as const

/**
 * Runs `body` with a new, signed-out browser context and closes it afterwards. `options` adds to
 * the suite's own, such as `NO_SERVICE_WORKER`.
 */
export async function withContext<T>(
  browser: Browser,
  body: (context: BrowserContext, page: Page) => Promise<T>,
  options: BrowserContextOptions = {},
): Promise<T> {
  const context = await browser.newContext({ ...CONTEXT_OPTIONS, ...options })
  // Inside a test the patched `newContext` did it already; the script is idempotent, so a hook is covered too.
  if (!activeFindings) await silenceTips(context, activeTips)
  // Inside a test the guard already listens to this context; in a hook it only has this list.
  const findings = activeFindings ?? []
  if (!activeFindings) watchContext(context, findings)
  try {
    return await body(context, await context.newPage())
  } finally {
    await context.close()
    if (!activeFindings) failOnFindings(findings)
  }
}

/** Types an amount on the keypad: the digits of the cents, so "123456" is R$ 1.234,56. */
export async function typeAmount(page: Page | Locator, digits: string): Promise<void> {
  for (const digit of digits) await page.getByRole('button', { name: digit, exact: true }).click()
}

/** Erases `count` digits of the amount on the keypad. */
export async function eraseAmount(page: Page, count: number): Promise<void> {
  for (let i = 0; i < count; i += 1) await page.getByRole('button', { name: 'Apagar' }).click()
}

/** Picks the account of an income or expense on the date-and-account sheet. */
export async function pickAccount(page: Page, name: string): Promise<void> {
  await page.getByRole('button', { name: /^(Hoje|Ontem|\d+ de \p{L}+\.?) · .+ · Eu/u }).click()
  await page.getByRole('radio', { name, exact: true }).check()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('radio', { name, exact: true })).toBeHidden()
}

/** A formatted amount as the app shows it, tolerant of the no-break space after "R$". */
export function money(amount: string): RegExp {
  return new RegExp(String.raw`R\$\s` + amount.replace(/[.,]/g, (c) => `\\${c}`))
}

/** The toast region, where the app's notices appear. */
export function notices(page: Page): Locator {
  return page.getByRole('region', { name: 'Notificações' })
}

/** Records an expense of the given digits (cents) in a category on the default account. */
export async function recordExpense(page: Page, digits: string, category: string): Promise<void> {
  await page.goto('/lancar')
  await typeAmount(page, digits)
  await page.getByText(category, { exact: true }).click()
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Gasto salvo')).toBeVisible()
  await expect(page).toHaveURL('/inicio')
}

/** What a recurring rule needs to be created on the Recorrências screen. */
export type RuleDraft = {
  description: string
  /** The amount as it is typed, with a comma: "1500,00". */
  amount: string
  /** The account's name; the form's first account when left out. */
  account?: string
  /** The expense category as the chips write it, emoji included. */
  category: string
}

/**
 * Creates a monthly expense rule that starts today (the form's own default) through Planejar >
 * Recorrências, and returns its start date as the form showed it (`YYYY-MM-DD`, the family's today).
 */
export async function createRule(page: Page, draft: RuleDraft): Promise<string> {
  await page.goto('/planejar')
  await page.getByRole('link', { name: /^Recorrências/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Recorrências' })).toBeVisible()
  await page.getByRole('button', { name: 'Nova recorrência' }).click()
  const form = page.getByRole('dialog', { name: 'Nova recorrência' })
  await form.getByLabel('Valor', { exact: true }).fill(draft.amount)
  if (draft.account) {
    await form.getByRole('radio', { name: draft.account, exact: true }).check()
  }
  await form.getByText(draft.category, { exact: true }).click()
  await form.getByRole('textbox', { name: 'Descrição (opcional)' }).fill(draft.description)
  const startOn = await form.getByLabel('Começa em').inputValue()
  await form.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Recorrência salva.')).toBeVisible()
  await expect(
    page.getByRole('main').getByRole('listitem').filter({ hasText: draft.description }),
  ).toBeVisible()
  return startOn
}

/** On the entry form: marks the payment method (by its label, "Pix") and adds the tag by typing it. */
export async function addTagAndMethod(page: Page, method: string, tag: string): Promise<void> {
  await page
    .getByRole('group', { name: 'Forma de pagamento' })
    .getByRole('button', { name: method, exact: true })
    .click()
  const tags = page.getByRole('combobox', { name: 'Tags' })
  await tags.fill(tag)
  await tags.press('Enter')
  await expect(page.getByRole('button', { name: `Remover tag ${tag}` })).toBeVisible()
}

/** Opens `/lancamentos` and waits until nothing is queued any more. */
export async function waitForQueueToEmpty(page: Page): Promise<void> {
  await page.goto('/lancamentos')
  await expect(page.getByRole('main').getByRole('listitem').first()).toBeVisible()
  await expect(page.getByText('Aguardando sinal')).toHaveCount(0, { timeout: 20_000 })
}

export type InvitedRole = 'Adulto' | 'Jovem' | 'Leitor'

/** The owner creates an invitation on the invite screen and returns the link it shows. */
export async function createInvitation(
  page: Page,
  nickname: string,
  role: InvitedRole = 'Adulto',
): Promise<string> {
  await page.goto('/mais/familia/convidar')
  await page.getByLabel('Apelido', { exact: true }).fill(nickname)
  await page.getByRole('combobox', { name: 'Papel' }).selectOption({ label: role })
  if (role === 'Jovem') {
    await page.getByRole('checkbox', { name: /Declaro que sou responsável legal/ }).check()
  }
  await page.getByRole('button', { name: 'Criar convite' }).click()
  const link = page.getByLabel('Link do convite')
  await expect(link).toBeVisible()
  return link.inputValue()
}

/** Opens an invitation link (one preview against the invitation budget) and waits for its heading. */
export async function openInvitation(page: Page, link: string, heading: string | RegExp) {
  await invitationBudget.reserve(1)
  await page.goto(link)
  await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
}

/**
 * Accepts a Youth's invitation on a fresh browser context's page: the name, then the passkey. Ends on
 * Início. The page must not have a virtual authenticator yet.
 */
export async function joinAsYouth(page: Page, link: string, name: string): Promise<void> {
  await addVirtualAuthenticator(page)
  await openInvitation(page, link, /como Jovem/)
  await page.getByLabel('Como você se chama?').fill(name)
  await invitationBudget.reserve(1)
  await page.getByRole('button', { name: 'Criar passkey neste aparelho' }).click()
  await page.getByRole('button', { name: 'Criar passkey', exact: true }).click()
  await expect(page).toHaveURL('/inicio')
}

/** Stubs only the first call of a route with the API's "sign in again" answer, then lets the rest through. */
export async function stubFirstReauthRequired(
  page: Page,
  url: string | RegExp,
  method: string,
): Promise<void> {
  let stubbed = false
  await page.route(url, async (route) => {
    if (stubbed || route.request().method() !== method) return route.fallback()
    stubbed = true
    await route.fulfill({
      status: 403,
      contentType: 'application/problem+json',
      headers: {
        'access-control-allow-origin': WEB_URL,
        'access-control-allow-credentials': 'true',
      },
      body: JSON.stringify({
        type: 'about:blank',
        title: 'Forbidden',
        status: 403,
        detail: 'Confirme que é você para continuar.',
        code: 'auth.reauthentication-required',
      }),
    })
  })
}

// A test value: it must match `liberdade.billing.webhook-token` in `application-e2e.properties`.
const ASAAS_WEBHOOK_TOKEN = 'test-webhook-token-0123456789abcdef0123'

/** Posts an Asaas event to the API the way Asaas would, and expects a 2xx answer. */
export async function postAsaasWebhook(
  request: APIRequestContext,
  body: Record<string, unknown>,
): Promise<void> {
  const response = await request.post(`${API_URL}/v1/billing/webhooks/asaas`, {
    headers: { 'asaas-access-token': ASAAS_WEBHOOK_TOKEN },
    data: { id: `evt_e2e_${crypto.randomUUID()}`, ...body },
  })
  expect(response.ok(), `webhook answered ${response.status()}`).toBe(true)
}

/** Answers the fake checkout host (`checkout.e2e.test`) with a plain page, so no real network is used. */
export async function stubCheckoutHost(page: Page): Promise<void> {
  await page.route('https://checkout.e2e.test/**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'text/html; charset=utf-8',
      body: '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>Pagamento de teste</title></head><body><h1>Pagamento de teste</h1></body></html>',
    }),
  )
}

/**
 * The Owner buys the Família Fundadora plan through the fake checkout and the webhook, the way
 * `billing.spec.ts` does, and ends on the plan screen once it shows the plan.
 */
export async function buyFamilyPlan(page: Page, request: APIRequestContext): Promise<void> {
  await stubCheckoutHost(page)
  await page.goto('/mais/plano')
  await page.getByRole('button', { name: 'Ser Família Fundadora' }).click()
  await page.waitForURL(/checkout\.e2e\.test/)
  const checkoutId = /\/c\/(chk_fake_[^/?#]+)/.exec(page.url())?.[1]
  expect(checkoutId).toBeTruthy()
  await postAsaasWebhook(request, { event: 'CHECKOUT_PAID', checkout: { id: checkoutId } })
  await page.goto(`/mais/plano?pedido=${checkoutId!.slice('chk_fake_'.length)}`)
  await expect(page.getByRole('heading', { name: '★ Família Fundadora' })).toBeVisible()
}

const API = API_URL

/** The page's own family: its id and the time zone its months are counted in. */
async function currentWorkspace(page: Page): Promise<{ id: string; timezone: string }> {
  const response = await page.request.get(`${API}/v1/me`)
  expect(response.ok()).toBe(true)
  const me = (await response.json()) as { workspaces: { id: string; timezone: string }[] }
  return me.workspaces[0]!
}

/** A write to the API as the signed-in page, with the CSRF token it needs. */
async function apiWrite(
  page: Page,
  method: 'PUT' | 'POST',
  path: string,
  data: Record<string, unknown>,
): Promise<unknown> {
  const csrf = (await (await page.request.get(`${API}/v1/csrf`)).json()) as {
    headerName: string
    token: string
  }
  const response = await page.request.fetch(`${API}${path}`, {
    method,
    headers: { [csrf.headerName]: csrf.token },
    data,
  })
  expect(response.ok(), `${method} ${path} answered ${response.status()}`).toBe(true)
  return response.status() === 204 ? undefined : response.json()
}

/** The family's month, `YYYY-MM`, `offset` months from the current one, counted in the family's time zone. */
export async function familyMonth(page: Page, offset = 0): Promise<string> {
  const { timezone } = await currentWorkspace(page)
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: timezone,
    year: 'numeric',
    month: '2-digit',
  }).formatToParts(new Date())
  const year = Number(parts.find((part) => part.type === 'year')!.value)
  const month = Number(parts.find((part) => part.type === 'month')!.value)
  const shifted = new Date(Date.UTC(year, month - 1 + offset, 1))
  return shifted.toISOString().slice(0, 7)
}

/** Sets the limit of an expense category (by its name) for a month (`YYYY-MM`) through the API; the amount is "100.00". */
export async function seedBudgetLimit(
  page: Page,
  month: string,
  category: string,
  amount: string,
): Promise<void> {
  const { id } = await currentWorkspace(page)
  const categories = (await (
    await page.request.get(`${API}/v1/workspaces/${id}/categories?kind=EXPENSE`)
  ).json()) as { id: string; name: string }[]
  const found = categories.find((each) => each.name === category)
  expect(found, `the expense category ${category}`).toBeDefined()
  await apiWrite(page, 'PUT', `/v1/workspaces/${id}/budgets/${month}/${found!.id}`, {
    limitAmount: { amount, currency: 'BRL' },
  })
}

/** Creates a goal with no deadline and no linked account through the API; the target is "100.00". */
export async function seedGoal(page: Page, name: string, target: string): Promise<void> {
  const { id } = await currentWorkspace(page)
  await apiWrite(page, 'POST', `/v1/workspaces/${id}/goals`, {
    name,
    icon: 'savings',
    targetAmount: { amount: target, currency: 'BRL' },
  })
}
