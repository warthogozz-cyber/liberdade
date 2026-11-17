# Liberdade · Plano 6: Cobrança e Retrato do mês — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a família pode virar **Família Fundadora**. São R$ 89 por um ano, pagos uma vez por Pix ou
cartão no Checkout do Asaas, e o app nunca vê CPF nem cartão. Nos primeiros 7 dias, um toque devolve
o dinheiro. Pagamento tardio ou em dobro ativa o plano e estorna o excedente sozinho. Os termos de
uso e a política de privacidade novos passam a valer junto com a cobrança. Todos os membros ganham
o **Retrato do mês**: um cartão em percentuais, desenhado no aparelho, para compartilhar ou baixar.
O plano Grátis não perde nada.

**Architecture:** um módulo novo do Spring Modulith, `billing`, depende de `identity`, `workspace`
e `shared`.
- **API pública:** `Plans`, `PlanView`, `PlanKey`, `@RequiresPlan` e `BillingExport`.
- **Gateway:** a porta `PaymentGateway` tem dois adaptadores. O `AsaasGateway` usa `RestClient`; o
  `FakePaymentGateway` serve os testes, o `dev` e o e2e.
- **Regra de pagamento única:** checkout, conferência na volta e webhook passam pela mesma
  `OrderPayments.applyPaid`. Ela usa um `UPDATE` condicional no pedido e um `INSERT ... ON CONFLICT`
  no plano, e cada pedido sai de aberto uma vez só.
- **Front:** a tela `/mais/plano` e o Retrato `/painel/retrato` são chunks sob demanda. O Retrato
  não chama a rede e usa o `useDashboard` que já existe.
- **Infra:** a única mudança é um filtro de métrica e um alarme por ambiente para o marcador de log
  `BILLING_ALERT`.

**Tech Stack:**
- **API:** Spring Boot 4.1.2, Spring Modulith 2.1.1, Java 25, PostgreSQL 18 com Flyway, `RestClient`
  do `spring-web` com `JdkClientHttpRequestFactory`. Testes com Testcontainers, `MockMvcTester`,
  `MockRestServiceServer` e jqwik.
- **Web:** React 19, react-router v8, TanStack Query, MSW, Vitest e Playwright. O canvas 2D é nativo.
- **Infra:** OpenTofu (`infra/live`), CloudWatch Logs metric filter e alarme no SNS `alerts`.
- **Sem dependências novas.** O `RestClient` e o `MockRestServiceServer` já vêm no `spring-web` e no
  `spring-test`. Só se o build provar que o Boot 4 exige `spring-boot-starter-restclient`, ele entra.
  É de primeira parte e usa a versão do BOM (D2).

**Spec:**
- [Spec do Plano 6](../specs/2026-11-17-liberdade-plan-6-billing-design.md), aprovado pelo mantenedor
  em 2026-11-17. É a autoridade: este plano argumenta a partir dele.
- [ADR-0015](../../adr/0015-revenue-model-and-billing.md) (modelo de receita e cobrança),
  [PRD](../../prd.md) (US-14 a US-17), [Roadmap](../../roadmap.md#plano-6--cobrança-e-retrato-do-mês-m),
  [Arquitetura](../../architecture.md), [Modelo de dados](../../data-model.md),
  [Design system](../../design-system.md).

## Global Constraints

- **Idiomas:** interface, mensagens, docs e textos de PR em PT-BR. Código, nomes técnicos, commits e
  comentários em inglês.
- **Commits e títulos de PR:** Conventional Commits, por exemplo `feat(billing): ...`, `test(e2e): ...`
  ou `docs: ...`. Cada commit termina com a linha `Co-Authored-By` da sessão.
- **Dados pessoais:** nada pessoal em arquivo commitado, em issue ou em log. O dono do repositório é
  "o mantenedor". Antes de cada commit, as duas buscas com `.git/info/personal-data-patterns` (a
  normal e a com `--untracked`) precisam sair com código 1.
- **Segredos:** `ASAAS_API_KEY` e `ASAAS_WEBHOOK_TOKEN` existem só no SSM (SecureString). Nunca
  aparecem no repositório, em log, em mensagem de exceção, em URL nem em resposta. Os perfis `test`,
  `dev` e `e2e` usam valores fixos de teste, marcados como tal.
- **Senhas e dados de pagamento:** o app nunca pede, recebe nem guarda CPF, cartão, endereço, nome
  ou e-mail de quem paga. O checkout vai **sem `customerData`**.
- **Minimização (LGPD):** `billing_webhook_events` não guarda o corpo do evento. A auditoria leva só
  rótulos fixos (`product`, `status`, `reason`), sem valores nem nomes.
- **Isolamento (ADR-0008):** toda rota sob `/v1/workspaces/{ws}/billing/**` usa `@WorkspaceScoped`.
  Um pedido de outra família responde 404 `billing.order-not-found`, nunca 403.
- **Papéis:** só `OWNER` compra, reembolsa e troca o tema. Todos os papéis veem o plano e conferem
  um pedido da própria família.
- **Dinheiro:** o preço fica no código (`FounderOffer.PRICE`, `Money` de R$ 89,00, 8900 centavos).
  Nunca em configuração nem `double`. O valor vai ao Asaas como `89.00` (`BigDecimal` com escala 2).
- **Tempo:** prazos usam o `Clock` da API (`paid_at + 7 × 24 h` e `paid_at + 1 ano`). Nunca
  `Instant.now()` direto. Os testes usam o `TestClock`.
- **Erros da API:** RFC 9457 com `code` estável e `detail` em PT-BR. Nunca expõem stack trace, nome
  de classe, corpo do Asaas nem mensagem de exceção. 429 leva `Retry-After`.
- **Valores fora do domínio:** logs e exceções nunca têm valores, ids do Asaas, tokens nem nomes. Os
  jobs logam só `failure.getClass().getName()`, como o `FamilyPurgeJob`.
- **Fronteiras de módulo:** `workspace` não depende de `billing`. `audit` e `privacy` passam a
  listar `"billing"` em `allowedDependencies`. O `ApplicationModulesTest` passa.
- **Contrato:** toda rota nova regenera o `api/openapi.json` (`OpenApiDriftIT`), o
  `web/src/shared/api/schema.ts` (`gen:api`) e os handlers do MSW no mesmo commit.
- **Cobertura:** JaCoCo com 80% no geral e 90% em `shared*` e `*.domain*`. O pacote
  `billing.domain` entra na regra dos 90%.
- **Testes de propriedade (ADR-0013):** jqwik com semente `20261102L` e 1000 casos para as regras
  de prazo e de transição. No web não há fast-check: o teste de propriedade do Retrato é um laço
  com gerador pseudoaleatório de semente fixa (`20261102`) e 1000 casos.
- **Desempenho:** o carregamento inicial do web continua até 200 KB (hoje 193,7 KB). Cada chunk fica
  até 260 KB. `usePlan` e o código do Retrato só entram nos chunks deles.
- **Acessibilidade:** os testes de a11y do Playwright cobrem a tela de Plano e o Retrato. Os botões
  têm nome acessível. O tema da Fundadora tem contraste AA.
- **Offline (ADR-0007):** a tela de Plano mostra o último plano conhecido. Comprar e reembolsar
  pedem rede e dizem isso. O `DocumentsGate` nunca trava ninguém offline (D13 do Plano 3).
- **Ética (spec, seção 2):** sem contagem regressiva, sem escassez falsa, sem pressão para comprar.
  Reembolso sem pergunta obrigatória de motivo.
- **GitHub Actions:** nenhum workflow novo e nenhuma permissão nova.
- **Terceiros:** o Asaas é o primeiro serviço externo chamado pela API. A exceção está aprovada na
  ADR-0015. Nada de chamada real no CI: os testes usam `MockRestServiceServer` e o gateway falso.
- **Versões:** nenhuma versão nova de biblioteca. Se o starter `restclient` entrar, ele segue o BOM do
  Boot 4.1.2.
- **Ações externas:** criar conta, chave e webhook no Asaas, parâmetros no SSM e `tofu apply` são do
  mantenedor (runbook da Task 16). O plano não executa nenhum deles.

### Pré-requisitos locais

Os mesmos do Plano 5: JDK 25, Node com corepack (pnpm), Docker Engine para Testcontainers e o
compose de e2e. Nenhum acesso ao Asaas é necessário para executar o plano.

## Entrega

- **PR 1 (docs):** o PR #42, com spec, este plano e o README dos planos. Entra antes do código.
- **PR 2 (código):** branch `feat/billing-and-retrato`, cortada da `main` depois do PR 1. Um commit
  por task, CI e E2E verdes, e uma revisão final da branch inteira.
- **Depois do merge:** o mantenedor segue o runbook do sandbox no staging. Esse é o critério de saída
  da seção 8 do spec. Um PR de docs registra o resultado e marca o Plano 6 como concluído.

### Itens herdados

| Item | Onde |
|---|---|
| Issue #37: teste que mantém a versão dos termos igual na API e no web | Task 11. O web passa a mandar a `currentVersion` da API, e um teste confere que os textos embutidos têm as quatro versões da API |
| Issue #36, #38, #39 e #40 | Fora do Plano 6 (infra, testes de proxy e prettier de docs) |
| Consentimento do Google publicado, aval do contador e chave de produção | Seção 9 do spec. São passos do mantenedor e não fazem parte da saída |

## Decisões deste plano

O spec deixa estes pontos ao construtor (seção 12), e o plano decide:

- **D1. Gateway por configuração.** `liberdade.billing.gateway` aceita `asaas` (padrão) ou `fake`.
  - O bean vem por `@ConditionalOnProperty`.
  - `application-test.properties`, `application-dev.properties` e `application-e2e.properties` usam
    `fake`.
  - O perfil `prod` com `fake` falha na subida (`BillingStartupCheck`), para o falso nunca ir à
    produção por engano.
- **D2. Cliente HTTP sem dependência nova.**
  - `RestClient.builder()` com `JdkClientHttpRequestFactory`: 5 s para conectar e 10 s para ler.
  - Base: `liberdade.billing.asaas.base-url`. Headers: `access_token` e `User-Agent: liberdade-api`.
  - Os testes ligam `MockRestServiceServer.bindTo(builder)` ao mesmo builder.
- **D3. Id da cobrança pelo checkout.**
  - Depois da ativação, a API grava o `asaas_payment_id` a partir de
    `GET /v3/payments?checkoutSession={checkoutId}` (`data[0].id`).
  - Se a consulta falhar, o reembolso tenta de novo na hora e o job de hora em hora completa.
  - O runbook manda conferir esse campo no sandbox (risco 1 do spec).
- **D4. Chaves de ambiente explícitas.**
  - O binding relaxado do Spring transforma `LIBERDADE_BILLING_ASAAS_BASE_URL` em `...asaas.base.url`,
    e não em `base-url`. Por isso o `application.properties` liga cada chave com placeholder:
    `liberdade.billing.enabled=${LIBERDADE_BILLING_ENABLED:false}`,
    `presale-open=${LIBERDADE_BILLING_PRESALE_OPEN:false}`,
    `asaas.base-url=${LIBERDADE_BILLING_ASAAS_BASE_URL:https://api-sandbox.asaas.com}`,
    `checkout-hosts=${LIBERDADE_BILLING_CHECKOUT_HOSTS:sandbox.asaas.com}`,
    `asaas.api-key=${ASAAS_API_KEY:}` e `webhook-token=${ASAAS_WEBHOOK_TOKEN:}`.
- **D5. "Cobrança configurada" sem falhar a subida.**
  - `BillingStatus.configured()` vale `enabled` mais uma de duas condições:
    - gateway `fake`;
    - `api-key` não vazia e `webhook-token` com 32 a 255 caracteres.
  - Com `enabled=true` e sem configuração, a API sobe e loga um WARN sem valores. A tela não oferece
    compra, e o webhook responde 401 sempre.
  - Token vazio nunca autentica.
- **D6. `presaleOpen` e `canPurchase`.**
  - `presaleOpen = configured() && presale-open`.
  - `canPurchase = presaleOpen && papel OWNER && plano FREE`.
  - `canRefund = papel OWNER`, o pedido de origem do plano `PAID`, o motivo não é `DUPLICATE` e
    `now < paid_at + 7 dias`.
  - `refundDeadline` é `paid_at + 7 dias`, ou `null`.
- **D7. Campos a mais no `GET plan`.** Além dos campos do spec, a resposta leva `paidOrderId` (o
  pedido de origem do plano). O front precisa dele para chamar o reembolso. `openOrder` é o pedido
  mais recente da família em `CREATED` ou `REFUND_PENDING`, como `{ id, status, refundReason }`, ou
  `null`.
- **D8. Temas da Fundadora.**
  - Quatro chaves: `TEAL` (padrão, `#0e5a66`), `OCEAN` (`#1f4e8c`), `FOREST` (`#2f6b3a`) e
    `PLUM` (`#6b3a6b`). Todas têm contraste AA sobre branco.
  - A API valida o enum, com 400 `request.invalid` fora da lista.
  - `theme` nulo vale `TEAL`.
- **D9. Limites de taxa.** São três beans `RateLimiter` em `BillingRateLimits`:
  - `checkoutRateLimiter`: 5 por 10 min, chave = workspace (spec);
  - `webhookRateLimiter`: 120 por min, chave `"asaas"` (spec);
  - `refreshRateLimiter`: 30 por min, chave = workspace. O polling de 5 s gasta 12 por minuto.
- **D10. Jobs.** Todos usam `zone = "America/Fortaleza"` e uma transação por item.
  - `BillingPurgeJob` às `0 55 3 * * *`: eventos com mais de 180 dias, pedidos pagos há mais de
    5 anos e pedidos nunca pagos com mais de 180 dias. No mesmo horário, ele também levanta o
    `REFUND_STUCK` (D11).
  - `BillingRetryJob` às `0 5 * * * *`: reembolsos `REFUND_PENDING` com até 72 h e pedidos pagos
    sem `asaas_payment_id`.
- **D11. `REFUND_STUCK` uma vez por dia.**
  - Pedidos `REFUND_PENDING` com `refund_requested_at` há mais de 72 h saem da nova tentativa. Cada um
    gera um `BILLING_ALERT type=REFUND_STUCK` por dia, no job das 03:55, até o mantenedor resolver
    no painel do Asaas e o webhook de estorno chegar.
  - O alarme manda e-mail quando o estado muda. Por isso a lembrança é diária, sem coluna nova.
- **D12. Estado do reembolso no Asaas.**
  - O job confere a cobrança com `findPayment`:
    - `REFUNDED` → o pedido vira `REFUNDED`;
    - `REFUND_REQUESTED` ou `REFUND_IN_PROGRESS` → espera;
    - outro estado → chama `refund` de novo.
  - Erros 4xx e 5xx do Asaas viram `PaymentGatewayException`, sem corpo nem mensagem do Asaas.
- **D13. Alertas.**
  - `BillingAlerts.raise(AlertType type)` escreve
    `log.warn("BILLING_ALERT type={}", type)` no logger `br.com.liberdadefamilia.billing.alerts`.
  - O log não leva id, valor nem nome. Com o ECS do perfil `prod`, a palavra continua no campo
    `message`.
  - O filtro de métrica usa o padrão `"BILLING_ALERT"`.
- **D14. Corpo do webhook.**
  - `Content-Length` acima de 65536 → 413 `request.too-large` antes de ler.
  - Sem `Content-Length`, a leitura para em 65537 bytes, e o excesso também dá 413.
  - O controller lê o corpo como `byte[]` e o `JsonMapper` o converte depois da autenticação.
- **D15. Token do webhook.** `MessageDigest.isEqual` sobre os bytes UTF-8 do header e do segredo.
  Sem header, header diferente ou segredo inválido (D5): 401 `billing.webhook-unauthorized`, sem
  gravar nada.
- **D16. Logo do checkout.**
  - `api/src/main/resources/billing/checkout-logo.png` é cópia do `web/public/pwa-192x192.png`.
  - O arquivo é lido uma vez e codificado em Base64 no `AsaasGateway`.
- **D17. Versões legais.**
  - `LegalDocuments.current(boolean billingEnabled)` devolve `terms-2026-11`/`privacy-2026-11`
    (desligada) ou `terms-2026-12`/`privacy-2026-12` (ligada).
  - O `privacy` lê `Plans.legalSetEnabled()`, um método da API pública do `billing` que expõe só esse
    booleano (`enabled`). Assim a chave fica num lugar só.
- **D18. Gateway falso no e2e.**
  - O `FakePaymentGateway` devolve o checkout `chk_fake_{orderId}` e o link
    `https://checkout.e2e.test/c/chk_fake_{orderId}`. `findPayment` responde pelo que o webhook falso
    marcou e `refund` aceita sempre.
  - O e2e usa `liberdade.billing.checkout-hosts=checkout.e2e.test`, um token de webhook de teste com
    40 caracteres, `enabled=true` e `presale-open=true`.
- **D19. Prévia do Retrato.** Canvas desenhado direto na tela e escalado por CSS. O PNG sai por
  `toBlob` só no compartilhar. A CSP (`img-src 'self' data:`) não muda.
- **D20. Polling na volta.** `useRefreshOrder` chama `refresh` a cada 5 s com `refetchInterval` e
  para no 24º chamado (2 min), quando o pedido sai de `CREATED` ou quando a tela fecha.

## Review Focus

1. **Webhook e conferência ao mesmo tempo:** a família vira Fundadora uma vez, e só um evento de
   auditoria `plan.activated` é gravado. Teste com duas threads e um `CountDownLatch`
   (`OrderPaymentsConcurrencyIT`, Task 5).
2. **Dois checkouts pagos em duas abas:** o segundo pedido, cancelado no nosso lado, é pago. O plano
   fica com o primeiro, o segundo vira `REFUND_PENDING` com `DUPLICATE`, e a tela mostra "Recebemos
   dois pagamentos...". Teste `secondPaymentWhileActiveRefundsAsDuplicate` (Task 5).
3. **Reembolso na fronteira dos 7 dias:** um segundo antes do prazo, o reembolso passa; no prazo
   exato, dá 409 `billing.refund-window-closed`. O botão some no mesmo instante em que o
   `canRefund` vira `false`. Testes `refundAtDeadlineMinusOneSecond` e `refundAtDeadline`
   (Task 7) e propriedade `refundWindowIsHalfOpen` (Task 2).
4. **Retrato com categoria minúscula, renda zero ou gasto maior que a renda:** as fatias somam 100,
   nenhuma fica negativa, o bloco de renda some sem receita e mostra "gastou X% a mais que a renda"
   quando o resultado é negativo. Propriedade `slicesAlwaysSumTo100` (Task 13).
5. **Cobrança desligada na produção:** sem botão de compra, termos `2026-11` em `/v1/legal/current`,
   e o webhook responde 401 mesmo com o token certo vindo de outro ambiente. Testes
   `BillingDisabledIT` (Task 8) e `LegalCurrentIT` (Task 11).

---

## Mapa de arquivos

**API** (`api/src/main/java/br/com/liberdadefamilia/`):

| Pacote | Novo | Muda |
|---|---|---|
| `workspace` | `WorkspaceRequests` | — |
| `workspace/internal` | — | `WorkspaceWebConfig` (ordem 100), `WorkspaceGuardInterceptor` (usa `WorkspaceRequests`) |
| `billing` | `package-info`, `Plans`, `PlanView`, `PlanKey`, `RequiresPlan`, `PlanRequiredException`, `BillingExport`, `ExportedOrder`, `ExportedPlan`, `CheckoutStarted`, `PlanActivated`, `RefundRequested`, `PlanEnded` | — |
| `billing/domain` | `OrderStatus`, `RefundReason`, `FounderOffer`, `RefundWindow`, `FounderTheme` | — |
| `billing/internal` | `BillingProperties`, `BillingStatus`, `BillingStartupCheck`, `BillingRateLimits`, `BillingAlerts`, `AlertType`, `PlanService`, `PlanRepository`, `OrderRepository`, `PaymentGateway`, `PaymentGatewayException`, `CheckoutRequest`, `CheckoutSession`, `PaymentInfo`, `AsaasGateway`, `FakePaymentGateway`, `CheckoutService`, `OrderPayments`, `RefundService`, `WebhookService`, `WebhookController`, `BillingController`, `PlanGuardInterceptor`, `BillingWebConfig`, `BillingPurgeJob`, `BillingRetryJob`, `BillingJdbcExport` | — |
| `audit/internal` | — | `AuditListeners` (4 listeners), `package-info` do `audit` (`"billing"`) |
| `audit` | — | `AuditEntry` (javadoc com `ORDER` e `PLAN`) |
| `identity/internal` | — | `SecurityConfig` (permitAll e CSRF ignore do webhook, permitAll de `GET /v1/legal/current`) |
| `privacy/internal` | `LegalController` | `LegalDocuments`, `DocumentService`, `ExportService`, `package-info` do `privacy` (`"billing"`) |

**Migração:** `api/src/main/resources/db/migration/V14__billing.sql`.

**Recursos:** `api/src/main/resources/billing/checkout-logo.png`, `application.properties`,
`application-dev.properties`, `application-e2e.properties` e
`api/src/test/resources/application-test.properties`.

**Testes da API** (`api/src/test/java/br/com/liberdadefamilia/`): `billing/` (os ITs de cada task),
`RoleMatrixIT`, `ApplicationModulesTest` (sem mudança, só passa) e `OpenApiDriftIT` (o contrato
regenera).

**Web** (`web/src/`):

| Pasta | Novo | Muda |
|---|---|---|
| `shared/api` | `billing.ts`, `legal.ts` (fora do barrel) | `endpoints.ts`, `types.ts`, `schema.ts` (gerado) |
| `features/billing` | `PlanPage.tsx`, `PlanStatusCard.tsx`, `FounderOfferCard.tsx`, `RefundSheet.tsx`, `ThemePicker.tsx`, `founderThemes.ts`, `messages.pt-BR.ts`, `register.ts` e os testes | — |
| `features/portrait` | `PortraitPage.tsx`, `portraitData.ts`, `drawPortrait.ts`, `sharePortrait.ts`, `messages.pt-BR.ts`, `register.ts` e os testes | — |
| `features/dashboard` | — | `DashboardPage.tsx` (botão "Retrato do mês") |
| `features/legal` | `texts/terms-2026-12.ts`, `texts/privacy-2026-12.ts` | `legalConfig.ts`, `useDocuments.ts`, `TermsPage.tsx`, `PrivacyPolicyPage.tsx`, `messages.pt-BR.ts` |
| `features/privacy` | — | `DeleteAccountSheet.tsx` (lembrete do reembolso), `messages.pt-BR.ts` |
| `app` | — | `routes.tsx` (2 rotas), `MorePage.tsx` (entrada "Plano"), `returnPath.ts` |
| `shared/i18n` | — | `messages.pt-BR.ts` (`more.plan`), `formatjs.d.ts` (2 bundles) |
| `mocks` | — | `handlers.ts`, `data.ts` |
| `eslint` | — | `boundaries.js` (`billing` e `portrait` no `CORE`) |

**E2E** (`web/e2e/`): `billing.spec.ts` (novo), `legal.spec.ts`, `a11y.spec.ts`, `support.ts` e
`core-without-modules.spec.ts` (sem mudança, só passa).

**Infra:** `infra/live/billing_alerts.tf` (novo), `infra/live/tests/operations.tftest.hcl`.

**Docs:** `docs/runbooks/billing-sandbox.md` (novo), `docs/runbooks/go-live.md`,
`docs/architecture.md`, `docs/data-model.md`.

**Em toda task de API:** as rotas de família usam `@WorkspaceScoped`. Rota nova regenera, no mesmo
commit, o OpenAPI (`UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'` em
`api/`), o `schema.ts` (`corepack pnpm --dir web gen:api`) e os handlers do MSW. Eventos são records
públicos no pacote raiz do `billing`, só com ids e rótulos.

## Tasks

### Task 1: Ordem das guardas e `WorkspaceRequests`

**Files:**
- Create em `workspace/`: `WorkspaceRequests.java`.
- Modify: `workspace/internal/WorkspaceWebConfig.java:61-66`, `workspace/internal/WorkspaceGuardInterceptor.java:40`.
- Test: `workspace/WorkspaceRequestsTest.java`, `workspace/internal/WorkspaceGuardOrderIT.java`.

**Interfaces:**
- Produces:
  - `public final class WorkspaceRequests`;
  - `public static Optional<WorkspaceMember> member(HttpServletRequest request)`;
  - `public static final int GUARD_ORDER = 100` (constante pública para o `billing` registrar a
    guarda dele em `GUARD_ORDER + 100`).

**Como fica:**
- A chave do atributo passa para `WorkspaceRequests`. O `WorkspaceGuardInterceptor` (em
  `workspace.internal`) grava o membro com `WorkspaceRequests.attach(request, member)`. Como são
  pacotes diferentes, `attach` precisa ser público; o javadoc diz "só a guarda da família chama".
  O `ApplicationModulesTest` continua verde, porque é API pública do próprio módulo `workspace`.
- `WorkspaceWebConfig` registra a guarda com `.order(WorkspaceRequests.GUARD_ORDER)`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `WorkspaceRequestsTest.memberIsEmptyWithoutGuard` | `member(new MockHttpServletRequest())` → `Optional.empty()` |
| `WorkspaceRequestsTest.memberReturnsWhatTheGuardAttached` | depois de `attach(req, m)`, `member(req)` → `Optional.of(m)` |
| `WorkspaceGuardOrderIT.guardIsRegisteredWithOrder100` | o `InterceptorRegistry` do contexto expõe a guarda da família com ordem 100 (via `RequestMappingHandlerMapping.getHandler(...)` em uma rota `/v1/workspaces/{ws}/modules`: a guarda vem antes de qualquer interceptor de ordem maior) |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*WorkspaceRequestsTest' --tests '*WorkspaceGuardOrderIT'`
Expected: FAIL na compilação (`WorkspaceRequests` não existe).

- [ ] **Step 3: Implementar.** Siga "Como fica". `RoleMatrixIT` e `WorkspaceGuardCoverageIT` não
  mudam e precisam continuar verdes.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*RoleMatrixIT'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "refactor(workspace): order the family guard and expose the resolved member"
```

---

### Task 2: Módulo `billing`, migração V14 e plano atual

**Files:**
- Create em `billing/`: `package-info.java`, `Plans.java`, `PlanView.java`, `PlanKey.java`.
- Create em `billing/domain/`: `OrderStatus.java`, `RefundReason.java`, `FounderOffer.java`, `RefundWindow.java`, `FounderTheme.java`.
- Create em `billing/internal/`: `BillingProperties.java`, `BillingStatus.java`, `BillingStartupCheck.java`, `PlanService.java`, `PlanRepository.java`, `OrderRepository.java`.
- Create: `api/src/main/resources/db/migration/V14__billing.sql`.
- Modify: `audit/AuditEntry.java` (javadoc), `application.properties`, `application-dev.properties`, `application-e2e.properties`, `api/src/test/resources/application-test.properties`.
- Test: `billing/domain/RefundWindowProperties.java`, `billing/domain/OrderStatusTest.java`, `billing/PlansIT.java`, `billing/internal/BillingStatusTest.java`, `billing/internal/BillingStartupCheckTest.java`, `billing/MigrationV14IT.java`.

**Interfaces:**
- Produces:
  - `public enum PlanKey { FREE, FAMILY }`;
  - `public record PlanView(PlanKey key, @Nullable Instant endsAt, boolean founder)`;
  - `public interface Plans { PlanView current(UUID workspaceId); boolean legalSetEnabled(); }`;
  - `domain.OrderStatus { CREATED, PAID, EXPIRED, CANCELED, REFUND_PENDING, REFUNDED, CHARGEBACK }`
    com `boolean canBecomePaid()`, verdadeiro para `CREATED`, `EXPIRED` e `CANCELED`;
  - `domain.RefundReason { CUSTOMER, DUPLICATE }`;
  - `domain.FounderOffer`: `PRODUCT = "FOUNDER_2026"`, `PRICE_CENTS = 8900L`, `CURRENCY = "BRL"`,
    `NAME = "Família Fundadora"`, `DESCRIPTION = "Plano Família por 1 ano"`,
    `static Instant endsAt(Instant paidAt)`, que é `paidAt` mais 1 ano em UTC
    (`atOffset(UTC).plusYears(1)`);
  - `domain.RefundWindow`: `static Instant deadline(Instant paidAt)` (`paidAt + 7 dias`) e
    `static boolean isOpen(Instant paidAt, Instant now)` (`now.isBefore(deadline)`);
  - `domain.FounderTheme { TEAL, OCEAN, FOREST, PLUM }` com `static FounderTheme orDefault(@Nullable String)`;
  - `internal.BillingProperties`, um `@ConfigurationProperties("liberdade.billing")` record com
    `boolean enabled`, `boolean presaleOpen`, `String gateway`, `List<String> checkoutHosts`,
    `String webhookToken` e `Asaas asaas(String baseUrl, String apiKey)`;
  - `internal.BillingStatus`: `boolean configured()`, `boolean presaleOpen()` (D5, D6) e
    `boolean webhookTokenValid()`;
  - `internal.PlanRepository`: `Optional<PlanRow> find(UUID ws)`;
    `boolean activateIfFree(UUID ws, UUID orderId, Instant startsAt, Instant endsAt)`, que devolve
    `true` se gravou; `void endNow(UUID ws, UUID orderId, Instant now)`;
    `void setTheme(UUID ws, FounderTheme theme)`;
  - `internal.OrderRepository`: `insert`, `findForWorkspace(UUID id, UUID ws)`,
    `findByCheckoutId(String)`, `findByPaymentId(String)`, `markPaid(UUID id, Instant paidAt)`
    (devolve `boolean`, `UPDATE ... WHERE status IN ('CREATED','EXPIRED','CANCELED')`),
    `transition(UUID id, OrderStatus from, OrderStatus to)` (devolve `boolean`),
    `cancelOpen(UUID ws)`, `setCheckoutId`, `setPaymentId`, `markRefundPending(UUID id, RefundReason, Instant)`
    e `latestOpen(UUID ws)`.

**Como fica:**
- O `V14__billing.sql` cria as três tabelas exatamente como na seção 6.2 do spec. Também cria:
  - os índices `billing_orders_workspace_ix (workspace_id, created_at DESC)` e
    `billing_orders_status_ix (status, created_at)`;
  - o índice único parcial `billing_orders_one_open_ux ON billing_orders (workspace_id) WHERE status = 'CREATED'`.
- **CHECK da auditoria.** A mesma migração troca o CHECK de `audit_events.target_type` com
  `ALTER TABLE ... DROP CONSTRAINT audit_events_target_type_check` e um `ADD CONSTRAINT` com a
  lista de antes mais `'ORDER'` e `'PLAN'`. Antes de escrever o `DROP`, confira o nome no banco do
  Testcontainers com
  `SELECT conname FROM pg_constraint WHERE conrelid = 'audit_events'::regclass AND contype = 'c'`.
- **Plano atual.** `PlanService implements Plans`:
  - sem linha, ou com `ends_at <= clock.instant()` → `new PlanView(FREE, null, false)`;
  - senão → `new PlanView(FAMILY, endsAt, founder)`.
  - `legalSetEnabled()` devolve `properties.enabled()`.
- **Pacote do módulo.** O `package-info.java` declara
  `@ApplicationModule(allowedDependencies = {"identity", "workspace", "shared"})` e
  `@NamedInterface` só onde o Modulith pedir.
- **Propriedades.** O `application.properties` recebe as chaves da D4 e
  `liberdade.billing.gateway=asaas`. Os perfis `test`, `dev` e `e2e` recebem
  `liberdade.billing.gateway=fake`.
  - **`test`:** `enabled=true`, `presale-open=true`, `checkout-hosts=checkout.e2e.test` e
    `webhook-token=test-webhook-token-0123456789abcdef0123`, comentado como valor de teste.
  - **`e2e`:** os mesmos valores do `test`.
  - **`dev`:** `enabled=true`, `presale-open=true` e o mesmo host falso.
- **Checagem na subida.** `BillingStartupCheck` é um `ApplicationRunner`:
  - com o perfil `prod` ativo e `gateway=fake`, lança `IllegalStateException("billing.gateway=fake is not allowed in prod")`;
  - com `enabled=true` e sem configuração, loga `WARN "billing enabled but not configured"`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `RefundWindowProperties.refundWindowIsHalfOpen` (jqwik, semente `20261102L`, 1000 casos, `paidAt` e deslocamento em segundos de −10 dias a +10 dias) | `isOpen(paidAt, now)` ⇔ `now ≥ paidAt` e `now < paidAt + 604800 s`; em `now == deadline` é `false` |
| `RefundWindowProperties.founderYearIsCalendarYear` | `endsAt(2028-02-29T12:00Z)` → `2029-02-28T12:00Z`; `endsAt(2026-11-17T10:00Z)` → `2027-11-17T10:00Z` |
| `OrderStatusTest.onlyOpenStatusesCanBecomePaid` | `canBecomePaid` só em `CREATED`, `EXPIRED`, `CANCELED` |
| `MigrationV14IT.auditAcceptsOrderAndPlanTargets` | `INSERT` em `audit_events` com `target_type` `ORDER` e `PLAN` passa; `'FOO'` viola o CHECK |
| `MigrationV14IT.oneOpenOrderPerFamily` | dois `CREATED` na mesma família → violação de `billing_orders_one_open_ux`; `CREATED` + `CANCELED` passa |
| `MigrationV14IT.familyDeletionKeepsPaidOrder` | apagar a linha de `workspaces` → `workspace_plans` some; `billing_orders.workspace_id` e `requested_by` ficam `null`; `amount_cents` e `paid_at` ficam |
| `PlansIT.freeWithoutRow` | família nova → `PlanView(FREE, null, false)` |
| `PlansIT.familyWhileActive` / `freeAfterEndsAt` | com `TestClock` em `ends_at − 1s` → `FAMILY`; em `ends_at` → `FREE` |
| `BillingStatusTest` (tabela) | `enabled=false` → `configured()=false`; `fake` + `enabled` → `true`; `asaas` sem chave → `false`; `asaas` com chave e token de 31 caracteres → `false`; 32 → `true`; 256 → `false`; `presaleOpen()` exige `configured()` |
| `BillingStartupCheckTest.prodWithFakeFails` | perfil `prod` + `fake` → `IllegalStateException` |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests 'br.com.liberdadefamilia.billing.*'`
Expected: FAIL na compilação (pacote `billing` não existe).

- [ ] **Step 3: Implementar.** Siga "Como fica". O `AuditEntry` passa a citar `ORDER` e `PLAN` no
  javadoc de `targetType`.

- [ ] **Step 4: Rodar e ver passar.** Mesmo comando, mais `--tests '*ApplicationModulesTest'`.
  Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL. A cobertura de `billing.domain`
  é de pelo menos 90%.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(billing): add the billing module, its tables and the current plan"
```

---

### Task 3: Porta `PaymentGateway`, `AsaasGateway` e gateway falso

**Files:**
- Create em `billing/internal/`: `PaymentGateway.java`, `PaymentGatewayException.java`, `CheckoutRequest.java`, `CheckoutSession.java`, `PaymentInfo.java`, `AsaasGateway.java`, `FakePaymentGateway.java`.
- Create: `api/src/main/resources/billing/checkout-logo.png` (cópia de `web/public/pwa-192x192.png`).
- Test: `billing/internal/AsaasGatewayTest.java`, `billing/internal/FakePaymentGatewayTest.java`, `api/src/test/resources/billing/asaas/*.json`.

**Interfaces:**
- Produces:
  - `record CheckoutRequest(UUID orderId, String callbackUrl)`. O callback é
    `{web.origin}/mais/plano?pedido={orderId}`, montado pelo `CheckoutService`.
  - `record CheckoutSession(String checkoutId, URI link)`.
  - `record PaymentInfo(String paymentId, String status, @Nullable Instant paidAt)`.
  - `interface PaymentGateway`:
    - `CheckoutSession createCheckout(CheckoutRequest request)`;
    - `Optional<PaymentInfo> findPayment(String checkoutId)`;
    - `void refund(String paymentId)`.
  - `class PaymentGatewayException extends RuntimeException`, criada com
    `PaymentGatewayException(int status)`. A mensagem é só `"asaas status " + status`; nunca o corpo.
  - `FakePaymentGateway` tem três métodos só de teste:
    - `markPaid(String checkoutId, String paymentId, Instant paidAt)`;
    - `failNextRefund()`;
    - `markRefunded(String paymentId)`.

**Como fica:**
- **`createCheckout`** chama `POST {base}/v3/checkouts` com o header `access_token` e o corpo da
  seção 6.3 do spec:
  - `billingTypes ["PIX","CREDIT_CARD"]`, `chargeTypes ["DETACHED"]`, `minutesToExpire 60`;
  - `externalReference = orderId`;
  - `items [{name, description, quantity 1, value 89.00, imageBase64}]`;
  - `callback {successUrl, cancelUrl, expiredUrl}`, os três com o `callbackUrl`.
  - Sem `customerData`. A resposta `{id, link}` vira `CheckoutSession`.
- **`findPayment`** chama `GET {base}/v3/payments?checkoutSession={checkoutId}` e lê
  `data[0].{id, status, paymentDate}`. Lista vazia → `Optional.empty()`. `paymentDate` é uma data
  (`AAAA-MM-DD`) e vira o início do dia em `America/Sao_Paulo`. O `paid_at` do pedido usa o instante
  do evento quando houver (Task 5); a data do Asaas só completa.
- **`refund`** chama `POST {base}/v3/payments/{paymentId}/refund` com corpo `{}` (estorno integral,
  sem `value`).
- **Erros:** status fora de 2xx → `PaymentGatewayException(status)`. Timeout ou IO →
  `PaymentGatewayException(0)`.
- **Bean:** o `AsaasGateway` é `@ConditionalOnProperty(name="liberdade.billing.gateway", havingValue="asaas", matchIfMissing=true)`.
  O `FakePaymentGateway` usa `havingValue="fake"`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `AsaasGatewayTest.createCheckoutSendsTheFounderItem` | `MockRestServiceServer` espera `POST /v3/checkouts` com header `access_token: test-key`, `jsonPath("$.items[0].value").value(89.00)`, `$.chargeTypes[0] == "DETACHED"`, `$.billingTypes` = `["PIX","CREDIT_CARD"]`, `$.minutesToExpire == 60`, `$.externalReference == orderId`, `$.callback.successUrl == callbackUrl`, `$.customerData` ausente, `$.items[0].imageBase64` não vazio; resposta `checkout-created.json` → `CheckoutSession("8f2c...", URI("https://sandbox.asaas.com/checkoutSession/show?id=8f2c..."))` |
| `AsaasGatewayTest.findPaymentByCheckoutSession` | `GET /v3/payments?checkoutSession=chk_1` → `payments-by-checkout.json` → `PaymentInfo("pay_123","RECEIVED", 2026-11-17T03:00Z)` |
| `AsaasGatewayTest.findPaymentEmpty` | `{"data":[]}` → `Optional.empty()` |
| `AsaasGatewayTest.refundPostsWithoutValue` | `POST /v3/payments/pay_123/refund` com corpo `{}` |
| `AsaasGatewayTest.errorsNeverCarryTheBody` | resposta 400 com `{"errors":[{"description":"segredo"}]}` → `PaymentGatewayException` com mensagem `asaas status 400`, sem "segredo" |
| `FakePaymentGatewayTest.linksUseTheE2eHost` | `createCheckout` → link `https://checkout.e2e.test/c/chk_fake_{orderId}` |
| `FakePaymentGatewayTest.failNextRefund` | depois de `failNextRefund()`, o próximo `refund` lança `PaymentGatewayException(400)` e o seguinte passa |

Os JSONs de exemplo seguem a documentação do Asaas (fontes na seção 10 do spec), com ids fictícios.

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*AsaasGatewayTest' --tests '*FakePaymentGatewayTest'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar.** Siga "Como fica" e a D2. O construtor do `AsaasGateway` recebe um
  `RestClient.Builder` (pacote-privado para o teste) e o `BillingProperties`.

- [ ] **Step 4: Rodar e ver passar.** Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL. Se faltar classe do `RestClient`
  na compilação, adicione `spring-boot-starter-restclient` sem versão (BOM) e registre na descrição
  do PR.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(billing): add the payment gateway port with Asaas and fake adapters"
```

---

### Task 4: Comprar (checkout)

**Files:**
- Create em `billing/`: `CheckoutStarted.java`.
- Create em `billing/internal/`: `CheckoutService.java`, `BillingController.java`, `BillingRateLimits.java`.
- Modify: `audit/internal/AuditListeners.java`, `audit/package-info.java` (`"billing"`).
- Test: `billing/CheckoutIT.java`.

**Interfaces:**
- Consumes: `PaymentGateway` e `OrderRepository` (Tasks 2 e 3); `WorkspaceMember` e
  `@WorkspaceScoped` do `workspace`.
- Produces:
  - `POST /v1/workspaces/{ws}/billing/checkout`, `@WorkspaceScoped(allow = OWNER)`, sem corpo.
    Responde 201 com `CheckoutView(UUID orderId, URI checkoutUrl)`.
  - `public record CheckoutStarted(UUID workspaceId, UUID orderId, UUID actorUserId)`.
  - Erros:
    - 409 `billing.unavailable`, detail "A compra não está disponível agora.", quando
      `presaleOpen()` é falso;
    - 409 `billing.plan-active`, detail "Sua família já tem um plano ativo.";
    - 429 `rate.limited`;
    - 502 `billing.checkout-failed`, detail "Não conseguimos abrir o pagamento agora. Tente de novo
      em instantes.", quando o gateway falha ou o link está fora da lista.
  - Bean `@Qualifier("checkoutRateLimiter") RateLimiter`. Os outros dois limites (D9) entram nas
    Tasks 5 e 6, na mesma classe.

**Como fica:**
1. Confere o limite de taxa, `presaleOpen()` e o plano atual.
2. Cancela o pedido `CREATED` anterior da família (`cancelOpen`).
3. Insere o pedido `CREATED` (8900, BRL, `FOUNDER_2026`, `requested_by` = usuário) e publica
   `CheckoutStarted`.
4. Chama o gateway **depois do commit dessa primeira transação**, para não segurar conexão do banco
   durante a chamada HTTP.
5. Numa segunda transação, grava `asaas_checkout_id`.
6. Valida o link: `https` e host em `checkoutHosts`, com comparação exata e sem curinga. Link
   inválido ou falha do gateway → o pedido vira `CANCELED` e a resposta é 502.
- **Auditoria:** `AuditListeners` grava `billing.checkout-started` com alvo `ORDER`, id do pedido e
  metadata `{product: FOUNDER_2026}`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `ownerStartsCheckout` | 201; `checkoutUrl` = `https://checkout.e2e.test/c/chk_fake_{orderId}`; pedido `CREATED` com `asaas_checkout_id`; auditoria `billing.checkout-started` com alvo `ORDER` |
| `secondCheckoutCancelsTheFirst` | dois POSTs → o primeiro pedido `CANCELED`, o segundo `CREATED` |
| `adultCannotBuy` | `ADULT` → 403 `auth.forbidden` |
| `otherFamilyIsNotFound` | Dono de outra família → 404 |
| `presaleClosedIsUnavailable` | com `presale-open=false` (`@TestPropertySource`) → 409 `billing.unavailable` |
| `activePlanCannotBuyAgain` | com plano ativo → 409 `billing.plan-active` |
| `sixthCheckoutInTenMinutesIsRateLimited` | 6º POST → 429 com `Retry-After` |
| `linkOutsideAllowedHostsCancels` | gateway falso devolvendo `https://evil.example/c/x` (bean `@Primary` de teste) → 502 `billing.checkout-failed`; pedido `CANCELED`; corpo sem a URL |
| `httpLinkIsRejected` | `http://checkout.e2e.test/...` → 502; pedido `CANCELED` |
| `gatewayFailureCancels` | gateway lança `PaymentGatewayException(500)` → 502; pedido `CANCELED`; log sem `asaas status 500` no corpo da resposta |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*CheckoutIT'`
Expected: FAIL (rota não existe).

- [ ] **Step 3: Implementar.** Siga "Como fica". Regenere o OpenAPI, o `schema.ts` e acrescente o
  handler do MSW `POST /v1/workspaces/:ws/billing/checkout` em `web/src/mocks/handlers.ts`, com o
  estado em `data.ts` (`billing: { plan, orders }` no `MockState`).

- [ ] **Step 4: Rodar e ver passar.** Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` e `corepack pnpm --dir web check` (lint, typecheck e
  testes) → verdes.

- [ ] **Step 6: Commit**

```bash
git add api web
git commit -m "feat(billing): start the Founder checkout on Asaas"
```

---

### Task 5: Pagamento confirmado, ativação e conferência na volta

**Files:**
- Create em `billing/`: `PlanActivated.java`, `RefundRequested.java`, `PlanEnded.java`.
- Create em `billing/internal/`: `OrderPayments.java`, `BillingAlerts.java`, `AlertType.java`.
- Modify: `billing/internal/BillingController.java` (rota `refresh`), `BillingRateLimits.java`, `audit/internal/AuditListeners.java`.
- Test: `billing/OrderPaymentsIT.java`, `billing/OrderPaymentsConcurrencyIT.java`, `billing/RefreshIT.java`.

**Interfaces:**
- Consumes: `OrderRepository`, `PlanRepository`, `PaymentGateway`.
- Produces:
  - `OrderPayments`:
    - `ApplyResult applyPaid(UUID orderId, Instant paidAt, @Nullable String paymentId)`;
    - `enum ApplyResult { ACTIVATED, DUPLICATE_REFUND, ALREADY_APPLIED }`;
    - `void requestRefund(UUID orderId, RefundReason reason)`, usado também pela Task 7.
  - `public record PlanActivated(UUID workspaceId, UUID orderId, Instant endsAt)`.
  - `public record RefundRequested(@Nullable UUID workspaceId, UUID orderId, RefundReason reason, @Nullable UUID actorUserId)`.
  - `public record PlanEnded(UUID workspaceId, @Nullable UUID orderId, String reason)`, com `reason`
    em `REFUND`, `CHARGEBACK` ou `CUSTOMER_REFUND`. As Tasks 6 e 7 também o publicam.
  - `enum AlertType { CHARGEBACK, REFUND_STUCK, WEBHOOK_ERROR }` e `BillingAlerts.raise(AlertType)` (D13).
  - `POST /v1/workspaces/{ws}/billing/orders/{id}/refresh`, `@WorkspaceScoped` com todos os papéis.
    Responde 200 com `OrderView(UUID id, OrderStatus status, @Nullable RefundReason refundReason)`.
    Um pedido de outra família → 404 `billing.order-not-found`, detail "Não encontramos esse pedido.".
    Usa o `refreshRateLimiter` (D9).

**Como fica:**
- **`applyPaid`, numa transação só:**
  1. `markPaid` com `UPDATE ... WHERE status IN ('CREATED','EXPIRED','CANCELED')`. Sem linha →
     `ALREADY_APPLIED`.
  2. Grava o `paymentId` se veio.
  3. Com `workspace_id` não nulo, chama `activateIfFree`. O SQL faz
     `INSERT ... ON CONFLICT (workspace_id) DO UPDATE SET ... WHERE workspace_plans.ends_at <= :now`,
     com `:now` do `Clock`, e não `now()` do banco, para o `TestClock` valer.
  4. Gravou → publica `PlanActivated` e devolve `ACTIVATED`.
  5. Não gravou, ou `workspace_id` é nulo → `requestRefund(orderId, DUPLICATE)` e `DUPLICATE_REFUND`.
- **`requestRefund`:**
  1. Grava `REFUND_PENDING`, `refund_reason` e `refund_requested_at`.
  2. Para `CUSTOMER`, termina o plano agora (`endNow`) e publica `PlanEnded(..., "CUSTOMER_REFUND")`.
  3. Publica `RefundRequested`.
  4. Depois do commit (`TransactionSynchronization.afterCommit`), resolve o `paymentId` se faltar
     (`findPayment`) e chama `gateway.refund`. Falha → só loga a classe e deixa para o job (Task 7).
- **`refresh`:**
  - Pedido `CREATED` com `asaas_checkout_id` → `findPayment`.
  - Status `RECEIVED` ou `CONFIRMED` → `applyPaid(id, paidAt ?: clock.instant(), paymentId)`.
  - Devolve o estado atual do pedido.
- **Auditoria:** `plan.activated` (alvo `PLAN`, id da família), `billing.refund-requested` (alvo
  `ORDER`, metadata `{reason}`) e `plan.ended` (alvo `PLAN`, metadata `{reason}`).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `OrderPaymentsIT.paidActivatesForOneYear` | `applyPaid(order, T, "pay_1")` → `ACTIVATED`; `workspace_plans.ends_at = T + 1 ano`; `founder = true`; `Plans.current` → `FAMILY` |
| `OrderPaymentsIT.secondApplyIsNoop` | segundo `applyPaid` igual → `ALREADY_APPLIED`; um só `plan.activated` na auditoria |
| `OrderPaymentsIT.paidAfterCancelActivates` / `paidAfterExpireActivates` | pedido `CANCELED`/`EXPIRED` sem plano ativo → `ACTIVATED` |
| `OrderPaymentsIT.secondPaymentWhileActiveRefundsAsDuplicate` | dois pedidos, o primeiro ativa; o segundo (cancelado) pago → `DUPLICATE_REFUND`; o pedido vira `REFUND_PENDING`/`DUPLICATE`; o plano continua com o primeiro pedido e o mesmo `ends_at`; o gateway falso recebeu `refund("pay_2")` |
| `OrderPaymentsIT.paymentAfterFamilyDeletionRefunds` | família apagada antes (`workspace_id` nulo) → `DUPLICATE_REFUND` |
| `OrderPaymentsIT.paidAfterPlanExpiredActivatesAgain` | com `TestClock` depois de `ends_at`, um pedido novo pago → `ACTIVATED`, `ends_at` novo |
| `OrderPaymentsConcurrencyIT.webhookAndRefreshTogether` | duas threads chamam `applyPaid` no mesmo pedido, soltas por um `CountDownLatch` → um `ACTIVATED` e um `ALREADY_APPLIED`; um só evento de auditoria |
| `RefreshIT.refreshActivatesWhenAsaasConfirms` | gateway falso marca o checkout pago → `refresh` responde `PAID`; plano `FAMILY` |
| `RefreshIT.refreshWhilePendingStaysCreated` | sem pagamento → `CREATED` |
| `RefreshIT.anyRoleCanRefresh` | `VIEWER` da família → 200 |
| `RefreshIT.otherFamilyOrderIsNotFound` | id de pedido de outra família → 404 `billing.order-not-found` |
| `RefreshIT.thirtyFirstRefreshIsRateLimited` | 31º em um minuto → 429 |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*OrderPayments*' --tests '*RefreshIT'`
Expected: FAIL na compilação.

- [ ] **Step 3: Implementar.** Siga "Como fica". Regenere o OpenAPI, o `schema.ts` e o handler do MSW
  da rota `refresh`.

- [ ] **Step 4: Rodar e ver passar.** Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web
git commit -m "feat(billing): activate the plan once and refund late or double payments"
```

---

### Task 6: Webhook do Asaas

**Files:**
- Create em `billing/internal/`: `WebhookController.java`, `WebhookService.java`.
- Modify: `identity/internal/SecurityConfig.java:83-93,133`, `billing/internal/BillingRateLimits.java`.
- Test: `billing/WebhookIT.java`, `billing/internal/WebhookPayloadTest.java`, `api/src/test/resources/billing/asaas/webhook-*.json`.

**Interfaces:**
- Consumes:
  - `OrderPayments.applyPaid` e `requestRefund` (Task 5);
  - `BillingAlerts`;
  - `OrderRepository.findByCheckoutId`, `findByPaymentId` e `transition`;
  - `PlanRepository.endNow`;
  - `PlanEnded` (Task 5).
- Produces:
  - `POST /v1/billing/webhooks/asaas`, público, sem CSRF. Respostas:
    - 200 sem corpo;
    - 401 `billing.webhook-unauthorized`, detail "Requisição não autorizada.";
    - 413 `request.too-large`, detail "A requisição é grande demais.";
    - 429 `rate.limited`;
    - 500 só para erro de banco.

**Como fica:**
- **Ordem do controller:** confere o limite de taxa, depois o tamanho (D14) e só então o token
  (D15). O corpo é lido como `byte[]` e convertido com `JsonMapper` num record
  `AsaasEvent(String id, String event, @Nullable Checkout checkout, @Nullable Payment payment)`,
  que ignora campos desconhecidos.
- **Registro do evento:** `WebhookService.handle(AsaasEvent)` roda numa transação e faz
  `INSERT INTO billing_webhook_events ... ON CONFLICT (event_id) DO NOTHING`. Sem linha (evento
  repetido) → 200 sem efeito.
- **Efeito por evento:**

| `event` | Busca o pedido por | Efeito | `outcome` |
|---|---|---|---|
| `CHECKOUT_PAID` | `checkout.id` | `applyPaid(id, clock.instant(), null)`; depois do commit, resolve o `paymentId` (D3) | `APPLIED` |
| `CHECKOUT_EXPIRED` / `CHECKOUT_CANCELED` | `checkout.id` | `transition(CREATED → EXPIRED/CANCELED)` | `APPLIED` (ou `IGNORED` se não estava `CREATED`) |
| `PAYMENT_REFUNDED` | `payment.id`, senão `payment.checkoutSession` | pedido → `REFUNDED` com `refunded_at`; se o pedido é a origem do plano ativo, `endNow` e `PlanEnded(..., "REFUND")` | `APPLIED` |
| `PAYMENT_CHARGEBACK_REQUESTED` / `PAYMENT_CHARGEBACK_DISPUTE` | idem | pedido → `CHARGEBACK`; `endNow` se for a origem; `PlanEnded(..., "CHARGEBACK")`; `BillingAlerts.raise(CHARGEBACK)` | `APPLIED` |
| outro | — | nada | `IGNORED` |

- **Pedido não encontrado:** `outcome = UNKNOWN_ORDER`, 200.
- **Erros:**
  - Uma `DomainException` ou `IllegalStateException` da regra, dentro de um savepoint, grava
    `outcome = ERROR`, chama `BillingAlerts.raise(WEBHOOK_ERROR)` e responde 200.
  - Uma `DataAccessException` sobe e vira 500.
- **SecurityConfig:**
  - `requestMatchers(HttpMethod.POST, "/v1/billing/webhooks/asaas").permitAll()`;
  - `csrf.ignoringRequestMatchers(pathPattern(HttpMethod.POST, "/v1/billing/webhooks/asaas"))`.
    É só essa rota.
- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `missingTokenIs401` / `wrongTokenIs401` | 401 `billing.webhook-unauthorized`; `billing_webhook_events` vazio |
| `blankConfiguredTokenAlwaysRejects` | `@TestPropertySource(webhook-token=)` + header vazio → 401 |
| `checkoutPaidActivates` | `webhook-checkout-paid.json` com o `checkout.id` do pedido → 200; pedido `PAID`; plano `FAMILY`; `outcome = APPLIED` |
| `sameEventTwiceIsIdempotent` | mesmo `id` duas vezes → 200 e 200; um `plan.activated`; uma linha de evento |
| `checkoutExpiredMarksExpired` / `checkoutCanceledMarksCanceled` | pedido `CREATED` → `EXPIRED` / `CANCELED` |
| `unknownCheckoutIs200` | `checkout.id` desconhecido → 200, `outcome = UNKNOWN_ORDER` |
| `refundEventEndsThePlan` | pedido `REFUND_PENDING` de origem do plano → `REFUNDED`; `Plans.current` → `FREE`; `plan.ended` |
| `chargebackEndsAndAlerts` | `PAYMENT_CHARGEBACK_REQUESTED` → `CHARGEBACK`; plano `FREE`; saída do log (`OutputCaptureExtension`) contém `BILLING_ALERT type=CHARGEBACK` e não contém `pay_` |
| `otherEventIsIgnored` | `PAYMENT_CREATED` → `IGNORED` |
| `ruleErrorReturns200AndAlerts` | regra forçada a lançar (bean de teste) → 200, `outcome = ERROR`, log com `BILLING_ALERT type=WEBHOOK_ERROR` |
| `bodyOver64KbIs413` | 65 537 bytes → 413 `request.too-large`; nada gravado |
| `rateLimitedAfter120` | 121º no minuto → 429 com `Retry-After` |
| `noCsrfNeeded` | POST sem sessão e sem `X-XSRF-TOKEN` com token certo → 200 (não 403 `csrf.invalid`) |
| `bodyIsNotStored` (`WebhookPayloadTest` ou consulta direta) | as colunas de `billing_webhook_events` são só as do spec; nenhum campo do corpo além de ids e tipo |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*WebhookIT' --tests '*WebhookPayloadTest'`
Expected: FAIL (rota não existe).

- [ ] **Step 3: Implementar.** Siga "Como fica". A rota fica fora do OpenAPI público do front: marque
  com `@Hidden` do springdoc, porque o web não a chama. O `OpenApiDriftIT` continua verde.

- [ ] **Step 4: Rodar e ver passar.** Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(billing): receive Asaas webhooks idempotently and raise billing alerts"
```

---

### Task 7: Reembolso em 7 dias e nova tentativa

**Files:**
- Create em `billing/internal/`: `RefundService.java`, `BillingRetryJob.java`.
- Modify: `billing/internal/BillingController.java` (rota `refund`).
- Test: `billing/RefundIT.java`, `billing/BillingRetryJobIT.java`.

**Interfaces:**
- Consumes: `OrderPayments.requestRefund` (Task 5), `RefundWindow` (Task 2) e
  `PaymentGateway.findPayment`/`refund`.
- Produces:
  - `POST /v1/workspaces/{ws}/billing/orders/{id}/refund`, `@WorkspaceScoped(allow = OWNER)`, sem
    corpo. Responde 202 com `OrderView`.
  - Erros:
    - 404 `billing.order-not-found`;
    - 409 `billing.refund-window-closed`, detail "O prazo de 7 dias para o reembolso já passou.";
    - 409 `billing.refund-not-allowed`, detail "Esse pedido não pode ser reembolsado.", quando o
      pedido não é `PAID` ou não é a origem do plano atual.
  - `BillingRetryJob.run()`, com `@Scheduled(cron = "0 5 * * * *", zone = "America/Fortaleza")`.

**Como fica:**
- **`refund`:** `RefundWindow.isOpen(paid_at, clock.instant())` e depois
  `requestRefund(id, CUSTOMER)`. O plano termina na hora, mesmo que o Asaas recuse depois.
- **`BillingRetryJob`:**
  - Para cada pedido `REFUND_PENDING` com `refund_requested_at > now − 72 h`, numa transação própria,
    segue a D12.
  - Para cada pedido `PAID` sem `asaas_payment_id`, resolve pelo `findPayment` (D3).
  - Falhas logam só a classe da exceção.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `refundAtDeadlineMinusOneSecond` | `TestClock` em `paid_at + 7d − 1s` → 202; pedido `REFUND_PENDING`/`CUSTOMER`; `Plans.current` → `FREE`; gateway recebeu `refund` |
| `refundAtDeadline` | `TestClock` em `paid_at + 7d` → 409 `billing.refund-window-closed`; plano continua `FAMILY` |
| `adultCannotRefund` | `ADULT` → 403 |
| `otherFamilyOrderIsNotFound` | 404 |
| `refundOfNonPaidIsNotAllowed` | pedido `CREATED` → 409 `billing.refund-not-allowed` |
| `refundRefusedStaysPendingAndPlanEnded` | `failNextRefund()` → 202; pedido `REFUND_PENDING`; plano `FREE` |
| `BillingRetryJobIT.retriesRefusedRefund` | depois de uma recusa, `run()` chama `refund` de novo |
| `BillingRetryJobIT.marksRefundedWhenAsaasSaysSo` | `markRefunded("pay_1")` → `run()` deixa o pedido `REFUNDED` |
| `BillingRetryJobIT.stopsAfter72Hours` | `TestClock` em `refund_requested_at + 72h + 1s` → `run()` não chama o gateway |
| `BillingRetryJobIT.resolvesMissingPaymentId` | pedido `PAID` sem `asaas_payment_id` → `run()` grava `pay_…` |
| `BillingRetryJobIT.failureLogsOnlyTheClass` | gateway lança → log com o nome da classe e sem `asaas status` nem ids |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*RefundIT' --tests '*BillingRetryJobIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica". Regenere o OpenAPI, o `schema.ts` e o handler do MSW
  da rota `refund`.

- [ ] **Step 4: Rodar e ver passar.** Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web
git commit -m "feat(billing): refund within 7 days and retry refused refunds hourly"
```

---

### Task 8: Plano na API, tema e `@RequiresPlan`

**Files:**
- Create em `billing/`: `RequiresPlan.java`, `PlanRequiredException.java`.
- Create em `billing/internal/`: `PlanGuardInterceptor.java`, `BillingWebConfig.java`.
- Modify: `billing/internal/BillingController.java`, `RoleMatrixIT.java:112` e `Family.snapshot()`.
- Test: `billing/PlanEndpointIT.java`, `billing/PlanGuardIT.java`, `billing/BillingDisabledIT.java`.

**Interfaces:**
- Consumes: `WorkspaceRequests.member` e `GUARD_ORDER` (Task 1); `Plans` (Task 2);
  `OrderRepository.latestOpen` (Task 2); `RefundWindow` (Task 2).
- Produces:
  - `@Target(METHOD) @Retention(RUNTIME) public @interface RequiresPlan { PlanKey value(); }`.
  - `public class PlanRequiredException extends DomainException`, criada com
    `super(403, "plan.required", "Este recurso é do plano Família.")`.
  - `GET /v1/workspaces/{ws}/billing/plan`, para todos os papéis, devolve
    `PlanDetails(PlanKey key, boolean founder, @Nullable Instant endsAt, FounderTheme theme, boolean presaleOpen, boolean canPurchase, boolean canRefund, @Nullable Instant refundDeadline, @Nullable UUID paidOrderId, @Nullable OrderView openOrder)`.
  - `PUT /v1/workspaces/{ws}/billing/plan/theme`, só `OWNER`, com `@RequiresPlan(FAMILY)`. O corpo é
    `ThemeRequest(@NotNull FounderTheme theme)` e a resposta é 204.

**Como fica:**
- O `BillingWebConfig` registra o `PlanGuardInterceptor` em `/v1/workspaces/*/**` com
  `.order(WorkspaceRequests.GUARD_ORDER + 100)`.
- **Guarda:** sem `@RequiresPlan`, segue. Sem membro (`WorkspaceRequests.member` vazio), lança
  `IllegalStateException`: é bug de ordem e deve falhar alto no teste. Plano abaixo do exigido →
  `PlanRequiredException`.
- **Campos calculados:** os de `PlanDetails` seguem a D6 e a D7.
- **Tema:** o padrão é `TEAL` quando a coluna é nula. Sem plano, o GET devolve `TEAL`, como manda a
  seção 6.6 ("o tema volta ao padrão"), mas a coluna fica como está.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `PlanEndpointIT.freeOwnerCanPurchase` | Dono sem plano → `key FREE`, `canPurchase true`, `canRefund false`, `theme TEAL`, `openOrder null` |
| `PlanEndpointIT.adultSeesButCannotPurchase` | `ADULT` → `canPurchase false` |
| `PlanEndpointIT.founderWithinSevenDays` | plano ativo há 2 dias → `key FAMILY`, `founder true`, `canRefund true`, `refundDeadline = paid_at + 7d`, `paidOrderId` = pedido |
| `PlanEndpointIT.founderAfterSevenDays` | `TestClock` em `paid_at + 8d` → `canRefund false`, `refundDeadline` ainda presente |
| `PlanEndpointIT.duplicateShowsOpenOrder` | depois de um `DUPLICATE` → `openOrder.status REFUND_PENDING`, `refundReason DUPLICATE` |
| `PlanGuardIT.themeWithoutPlanIs403PlanRequired` | Dono sem plano, `PUT theme` → 403 `plan.required` |
| `PlanGuardIT.themeWithPlanIs204` | Dono com plano → 204; GET devolve o tema novo |
| `PlanGuardIT.adultThemeIs403Forbidden` | `ADULT` com plano → 403 `auth.forbidden` (a guarda da família vem antes) |
| `PlanGuardIT.nonMemberIs404` | estranho → 404, não `plan.required` |
| `PlanGuardIT.invalidThemeIs400` | `{"theme":"RED"}` → 400 `request.invalid` |
| `PlanGuardIT.themeResetsAfterRefund` | depois do reembolso → GET `theme TEAL` |
| `BillingDisabledIT` (`enabled=false`) | GET plan → `presaleOpen false`, `canPurchase false`; checkout → 409 `billing.unavailable`; webhook com o token de teste → 401 |
| `RoleMatrixIT.billingRows` | as 5 rotas × 4 papéis: `GET plan` OK para todos; `checkout`, `refund` e `PUT theme` OK só `OWNER` (os outros `FORBIDDEN`); `refresh` OK para todos; `Family.snapshot()` inclui `billing_orders` e `workspace_plans` |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*PlanEndpointIT' --tests '*PlanGuardIT' --tests '*BillingDisabledIT' --tests '*RoleMatrixIT'`
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica". O `RoleMatrixIT` precisa de um plano ativo para a
  linha do `PUT theme` dar OK: crie o plano direto no banco no `setUp` da linha. Regenere o OpenAPI,
  o `schema.ts` e os handlers do MSW (`GET plan`, `PUT theme`).

- [ ] **Step 4: Rodar e ver passar.** Mais `--tests '*WorkspaceGuardCoverageIT'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api web
git commit -m "feat(billing): expose the family plan, the Founder theme and the plan guard"
```

---

### Task 9: Jobs de limpeza

**Files:**
- Create em `billing/internal/`: `BillingPurgeJob.java`.
- Test: `billing/BillingPurgeJobIT.java`.

**Interfaces:**
- Consumes: `BillingAlerts` (Task 5) e `TestClock`.
- Produces: `BillingPurgeJob.run()`, com `@Scheduled(cron = "0 55 3 * * *", zone = "America/Fortaleza")`.

**Como fica:**
- Apaga, em lotes de 500 e numa transação por lote:
  - `billing_webhook_events` com `received_at < now − 180 dias`;
  - `billing_orders` com `paid_at < now − 5 anos`;
  - `billing_orders` com `paid_at IS NULL` e `created_at < now − 180 dias`.
- No fim, para cada pedido `REFUND_PENDING` com `refund_requested_at < now − 72 h`, chama
  `BillingAlerts.raise(REFUND_STUCK)` (D11).

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `deletesOldWebhookEvents` | evento de 181 dias some; de 179 fica |
| `deletesPaidOrdersAfterFiveYears` | pago há 5 anos + 1 dia some; há 5 anos − 1 dia fica |
| `deletesUnpaidOrdersAfter180Days` | `EXPIRED` com 181 dias some; `CANCELED` com 179 fica |
| `oldOrderOfLivingFamilyKeepsThePlanRow` | pedido de origem de um plano ainda existente é apagado → `workspace_plans.source_order_id` vira `null`; a linha do plano fica |
| `stuckRefundAlertsDaily` | `REFUND_PENDING` com 73 h → log `BILLING_ALERT type=REFUND_STUCK`; com 71 h → sem alerta |
| `failureLogsOnlyTheClass` | falha forçada → log só com o nome da classe |

- [ ] **Step 2: Rodar e ver falhar.** Run (em `api/`): `./gradlew test --tests '*BillingPurgeJobIT'`.
  Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica", no molde do `FamilyPurgeJob`.

- [ ] **Step 4: Rodar e ver passar.** Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(billing): purge old billing records and remind stuck refunds daily"
```

---

### Task 10: Pedidos e plano na exportação da família

**Files:**
- Create em `billing/`: `BillingExport.java`, `ExportedOrder.java`, `ExportedPlan.java`.
- Create em `billing/internal/`: `BillingJdbcExport.java`.
- Modify: `privacy/internal/ExportService.java:248,350` (`loadFamily`, `writeFamily`, `FamilyData`), `privacy/package-info.java` (`"billing"`).
- Test: `billing/BillingExportIT.java`, mais um caso novo no IT de exportação que já existe no `privacy`.

**Interfaces:**
- Produces:
  - `public interface BillingExport`:
    - `List<ExportedOrder> orders(UUID workspaceId)`;
    - `Optional<ExportedPlan> plan(UUID workspaceId)`.
  - `public record ExportedOrder(String product, long amountCents, String currency, String status, Instant createdAt, @Nullable Instant paidAt, @Nullable Instant refundedAt)`.
    Não leva ids do Asaas nem `requested_by`.
  - `public record ExportedPlan(String key, boolean founder, Instant startsAt, Instant endsAt)`.

**Como fica:** o `dados.json` da família ganha duas chaves novas no fim do `LinkedHashMap`: `orders`
(o valor em texto `"89.00"`, como o resto da exportação) e `plan` (objeto ou `null`). O
`formatVersion` sobe em 1.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `BillingExportIT.exportsOrdersWithoutAsaasIds` | depois de comprar e pagar → um `ExportedOrder` com `FOUNDER_2026`, 8900, `PAID`; o JSON não contém `chk_` nem `pay_` |
| `BillingExportIT.planIsEmptyWhenFree` | família sem plano → `Optional.empty()` |
| IT de exportação do `privacy`, caso `familyExportIncludesBilling` | `dados.json` tem `orders[0].status == "PAID"`, `orders[0].amount == "89.00"` e `plan.founder == true`; `formatVersion` incrementado |

- [ ] **Step 2: Rodar e ver falhar.** Expected: FAIL na compilação.

- [ ] **Step 3: Implementar.** Siga "Como fica", no molde do `LedgerExport`/`LedgerJdbcExport`.

- [ ] **Step 4: Rodar e ver passar.** Mais `--tests '*ApplicationModulesTest'`. Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add api
git commit -m "feat(privacy): include billing orders and the plan in the family export"
```

---

### Task 11: Termos e privacidade que valem com a cobrança

**Files:**
- Create: `privacy/internal/LegalController.java`.
- Create: `web/src/shared/api/legal.ts`, `web/src/features/legal/texts/terms-2026-12.ts` e `texts/privacy-2026-12.ts`. Os textos atuais vão para `texts/terms-2026-11.ts` e `texts/privacy-2026-11.ts`.
- Modify:
  - API: `privacy/internal/LegalDocuments.java`, `privacy/internal/DocumentService.java`, `identity/internal/SecurityConfig.java` (permitAll de `GET /v1/legal/current`).
  - Web: `features/legal/legalConfig.ts:23`, `useDocuments.ts`, `TermsPage.tsx`, `PrivacyPolicyPage.tsx`, `messages.pt-BR.ts`, `mocks/data.ts:59` (`CURRENT_DOCUMENTS`), `mocks/handlers.ts`.
  - E2E: `web/e2e/legal.spec.ts`.
- Test: `privacy/LegalCurrentIT.java`, `privacy/DocumentsBillingIT.java`, `features/legal/legalTexts.test.ts`, `TermsPage.test.tsx`, `PrivacyPolicyPage.test.tsx`, `DocumentsGate.test.tsx`.

**Interfaces:**
- Consumes: `Plans.legalSetEnabled()` (Task 2).
- Produces:
  - `GET /v1/legal/current`, público e sem sessão. Responde 200 com
    `LegalVersions(String terms, String privacy)` e `Cache-Control: no-store`.
  - `LegalDocuments.current(boolean billingEnabled)` devolve um `Map<ConsentPurpose, String>`
    (D17). `DocumentService.list` e `accept` usam esse mapa.
  - Web:
    - `useLegalVersions()`, com `meta: { persist: true }`, para o último valor ficar para o offline;
    - `legalConfig.texts`, um `Record<string, LegalText>` com as quatro versões;
    - `legalConfig.latest = { terms: 'terms-2026-12', privacy: 'privacy-2026-12' }`.

**Como fica:**
- **Textos novos.** Os textos `2026-12` seguem a seção 6.8 do spec:
  - **Política:** o Asaas como operador, a tabela de dados, prazos e bases legais, e o CPF e o cartão
    só no Asaas.
  - **Termos:** a pré-venda, o que a Fundadora inclui hoje (tema e selo), o reembolso integral em
    7 dias, o fim do acesso em 1 ano, sem renovação automática, e o e-mail de contato.
  - O `updatedAt` passa a ser por texto.
- **Páginas.** `/termos` e `/privacidade` mostram o texto que `useLegalVersions` indicar. Sem
  resposta, mostram `legalConfig.latest`.
- **Aceite.** `useDocuments` manda no aceite a `currentVersion` que o `GET /v1/me/documents`
  devolveu, e não mais a constante.
- **`DocumentsGate`.** Continua igual: pede o aceite quando a versão aceita é diferente da atual e
  nunca trava offline.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `LegalCurrentIT.billingOnReturnsDecemberSet` | perfil de teste (`enabled=true`) → `{terms: "terms-2026-12", privacy: "privacy-2026-12"}`; sem sessão → 200 |
| `LegalCurrentIT.billingOffReturnsNovemberSet` | `@TestPropertySource(liberdade.billing.enabled=false)` → `terms-2026-11`/`privacy-2026-11` |
| `DocumentsBillingIT.acceptOldVersionIsOutdated` | com cobrança ligada, aceitar `terms-2026-11` → 409 `documents.version-outdated` |
| `DocumentsBillingIT.userWhoAcceptedNovemberMustAcceptAgain` | aceite de 2026-11 gravado → `GET /v1/me/documents` com `accepted false` e `currentVersion terms-2026-12` |
| `legalTexts.test.ts.embedsEveryApiVersion` | `legalConfig.texts` tem as chaves `terms-2026-11`, `terms-2026-12`, `privacy-2026-11` e `privacy-2026-12`; cada texto tem `updatedAt` e seções. Fecha a issue #37 |
| `TermsPage.test.tsx.showsTheVersionTheApiSays` | MSW devolve `terms-2026-11` → a página mostra "Versão terms-2026-11"; sem resposta (erro de rede) → mostra `terms-2026-12` |
| `DocumentsGate.test.tsx.postsTheApiCurrentVersion` | o POST de aceite leva `terms: "terms-2026-12"` vindo da API |
| `DocumentsGate.test.tsx.offlineNeverBlocks` | continua passando |
| `web/e2e/legal.spec.ts` | a regex `/Versão terms-\d{4}-\d{2} · atualizada em /` continua válida; o aceite novo aparece para quem já tinha aceitado a versão antiga (o e2e roda com cobrança ligada) |

- [ ] **Step 2: Rodar e ver falhar**

Run (em `api/`): `./gradlew test --tests '*LegalCurrentIT' --tests '*DocumentsBillingIT'`.
Run (na raiz): `corepack pnpm --dir web test -- legal`.
Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica". Regenere o OpenAPI, o `schema.ts` e o handler do MSW
  de `GET /v1/legal/current`.

- [ ] **Step 4: Rodar e ver passar.** Expected: PASS.

- [ ] **Step 5: Conferir.** `./gradlew build` e `corepack pnpm --dir web check` → verdes.

- [ ] **Step 6: Commit**

```bash
git add api web
git commit -m "feat(legal): pick the terms and privacy versions by the billing switch"
```

---

### Task 12: Tela de Plano no web

**Files:**
- Create: `web/src/shared/api/billing.ts`.
- Create em `web/src/features/billing/`: `PlanPage.tsx`, `PlanStatusCard.tsx`, `FounderOfferCard.tsx`, `RefundSheet.tsx`, `ThemePicker.tsx`, `founderThemes.ts`, `messages.pt-BR.ts`, `register.ts`.
- Modify:
  - Rotas e menu: `web/src/app/routes.tsx` (filha do AppShell `/mais/plano`, lazy), `app/MorePage.tsx`, `app/returnPath.ts` (`/mais/plano` em `RETURN_PATHS`).
  - Mensagens: `shared/i18n/messages.pt-BR.ts` (`more.plan: 'Plano'`), `shared/i18n/formatjs.d.ts`.
  - Outros: `eslint/boundaries.js`, `features/privacy/DeleteAccountSheet.tsx` e `features/privacy/messages.pt-BR.ts`, `mocks/data.ts`, `mocks/handlers.ts`.
- Test: `features/billing/PlanPage.test.tsx`, `RefundSheet.test.tsx`, `founderThemes.test.ts`, `app/MorePage.test.tsx`, `features/privacy/DeleteAccountSheet.test.tsx`.

**Interfaces:**
- Consumes: as rotas das Tasks 4, 5, 7 e 8 via `client`/`unwrap`; `ApiError`.
- Produces (`shared/api/billing.ts`, fora do barrel):
  - `usePlan(ws)`, com a chave `['ws', ws, 'billing', 'plan']`;
  - `useStartCheckout(ws, assign = (url) => window.location.assign(url))`;
  - `useRefreshOrder(ws, orderId | null)` (D20);
  - `useRequestRefund(ws)`;
  - `useSetTheme(ws)`.
  - Cada mutação invalida a chave do plano.
- Produces (`features/billing`): `founderThemes`, um `Record<FounderTheme, { accent: string; label: string }>` com as cores da D8.

**Como fica:**
- **Estados da tela** (ASCII da seção 6.10 do spec):
  - **Dono sem plano, `canPurchase`:** "Seu plano: Grátis", o texto da oferta, "Hoje inclui: tema e
    selo." e o botão "Ser Família Fundadora". Abaixo, "Pix ou cartão, na página segura do Asaas.
    Reembolso em 7 dias."
  - **Sem `presaleOpen`:** só "Seu plano: Grátis" e "Tudo o que o app faz hoje é grátis e continua
    grátis.".
  - **Fundadora:** "★ Família Fundadora", "Vale até dd/mm/aaaa" e "Não existe cobrança automática.".
    O Dono vê o `ThemePicker` (radiogroup com os quatro temas). Com `canRefund`: "Desistiu? Até
    dd/mm você recebe os R$ 89 de volta." e o botão "Receber meu dinheiro de volta".
  - **Fundadora depois dos 7 dias:** "Seu plano vale até dd/mm/aaaa. Não existe cobrança
    automática." e o e-mail de contato (`legalConfig.contactEmail`).
  - **`openOrder.status = CREATED` com `?pedido=`:** "Estamos esperando a confirmação do Asaas. Pode
    fechar esta tela; avisamos aqui quando chegar." enquanto o `useRefreshOrder` consulta.
  - **`openOrder` `REFUND_PENDING`/`CUSTOMER`:** "Reembolso em processamento". No cartão, também
    "No cartão, o estorno pode levar até 10 dias úteis para aparecer na fatura.".
  - **`openOrder` `REFUND_PENDING`/`DUPLICATE`:** "Recebemos dois pagamentos. Devolvemos um deles
    pelo Asaas.".
  - **Quem não é Dono:** os mesmos estados sem botões, com "Quem cuida do plano é o Dono da
    família".
  - **Offline:** o último plano do cache. Os botões ficam desativados com "Você está sem
    internet.".
- **Reembolso.** `RefundSheet` é uma confirmação simples, sem campo de motivo, com "Receber os R$ 89
  de volta" e "Continuar Fundadora".
- **Tema e selo.** O acento do tema e o selo aparecem só nesta tela (CSS custom property no
  container da página, não no `:root`).
- **Exclusão de conta.** `DeleteAccountSheet` mostra
  `privacy.deleteAccount.refundReminder` ("Sua família é Fundadora há menos de 7 dias. Se quiser o
  dinheiro de volta, peça o reembolso em Mais > Plano antes de excluir.") quando o `usePlan` da
  família atual diz `canRefund`. O hook só carrega dentro do sheet.
- **Erros.** `billing.unavailable`, `billing.plan-active`, `billing.checkout-failed`,
  `billing.refund-window-closed` e `rate.limited` aparecem com o `detail` da API num `role="alert"`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `PlanPage.test.tsx.ownerFreeSeesOffer` | MSW `canPurchase true` → botão "Ser Família Fundadora"; clicar chama `assign` com a `checkoutUrl` |
| `.presaleClosedShowsOnlyFree` | `presaleOpen false` → sem botão; texto "continua grátis" |
| `.nonOwnerSeesWhoManages` | `ADULT` → "Quem cuida do plano é o Dono da família"; nenhum botão |
| `.returnPollsUntilPaid` | rota `/mais/plano?pedido=o1`, MSW devolve `CREATED` duas vezes e depois `PAID` (com timers falsos de 5 s) → mensagem de espera e depois "★ Família Fundadora" |
| `.pollingStopsAfterTwoMinutes` | sempre `CREATED` → 24 chamadas e depois nenhuma |
| `.founderWithinSevenDaysCanRefund` | `canRefund true` → texto com a data e botão "Receber meu dinheiro de volta" |
| `.founderAfterSevenDaysShowsNoAutoCharge` | `canRefund false`, `FAMILY` → "Não existe cobrança automática." e o e-mail de contato |
| `.duplicateShowsTwoPayments` | `openOrder REFUND_PENDING/DUPLICATE` → "Recebemos dois pagamentos..." |
| `.checkoutErrorShowsDetail` | MSW 502 `billing.checkout-failed` → `role="alert"` com o detail |
| `.offlineDisablesButtons` | `navigator.onLine = false` → botões desativados |
| `RefundSheet.test.tsx.confirmsWithoutReason` | confirmar chama `POST .../refund` sem corpo; sem campo de motivo no DOM |
| `founderThemes.test.ts.everyThemeHasAAContrast` | razão de contraste de cada `accent` com `#ffffff` ≥ 4,5 (fórmula WCAG no teste) |
| `MorePage.test.tsx.hasPlanEntry` | link "Plano" para `/mais/plano` |
| `DeleteAccountSheet.test.tsx.remindsRefundWithinSevenDays` | `canRefund true` → texto do lembrete; `false` → sem lembrete |

- [ ] **Step 2: Rodar e ver falhar.** Run (na raiz): `corepack pnpm --dir web test -- billing MorePage DeleteAccountSheet`.
  Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica". Registre `billingMessages` no `formatjs.d.ts` e o
  `billing` no `CORE` do `eslint/boundaries.js`.

- [ ] **Step 4: Rodar e ver passar.** Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` e `corepack pnpm --dir web build &&
  corepack pnpm --dir web check:chunks`. O inicial continua ≤ 200 KB, e o chunk da tela aparece
  separado.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the Plan screen with the Founder pre-sale and refund"
```

---

### Task 13: Retrato do mês

**Files:**
- Create em `web/src/features/portrait/`: `PortraitPage.tsx`, `portraitData.ts`, `drawPortrait.ts`, `sharePortrait.ts`, `messages.pt-BR.ts`, `register.ts`.
- Modify: `web/src/app/routes.tsx` (filha do AppShell `/painel/retrato`, lazy), `features/dashboard/DashboardPage.tsx` (botão "Retrato do mês" que leva o `mes` atual), `shared/i18n/formatjs.d.ts`, `eslint/boundaries.js` (`portrait`).
- Test: `features/portrait/portraitData.test.ts`, `PortraitPage.test.tsx`, `sharePortrait.test.ts`, `features/dashboard/DashboardPage.test.tsx`.

**Interfaces:**
- Consumes:
  - `useDashboard(ws, month)` (`DashboardView`), `useCategories(ws, 'EXPENSE')`;
  - `parseCents` e `CHART_PALETTE`;
  - `MONTH_FORMAT`, `MIN_MONTH`, `maxMonth` e `currentMonth`;
  - `usePlan(ws)` (Task 12), só para tema e selo.
- Produces:
  - `portraitData.ts`:
    - `type PortraitSlice = { label: string; percent: number; other: boolean }`;
    - `type PortraitData = { month: string; slices: PortraitSlice[]; income: IncomeBlock | null; empty: boolean }`;
    - `type IncomeBlock = { kind: 'left' | 'over'; percent: number }`;
    - `function buildPortrait(view: DashboardView, names: Map<string, string>): PortraitData`.
  - `drawPortrait.ts`:
    - `function drawPortrait(ctx: CanvasRenderingContext2D, data: PortraitData, options: { hidden: Set<'categories' | 'income' | 'month'>; theme: FounderTheme | null; founder: boolean; tokens: DesignTokens }): void`.
  - `sharePortrait.ts`:
    - `function sharePortrait(canvas: HTMLCanvasElement, month: string, deps = { nav: navigator, download }): Promise<'shared' | 'downloaded' | 'cancelled'>`.

**Como fica:**
- **`buildPortrait`:**
  - `byCategory` vem com até 6 categorias mais "Outras" (`categoryId: null`).
  - A 6ª categoria se junta a "Outras". Fatias com `current` zero saem.
  - Cada fatia vale `Math.round(cents ÷ gastosDoMês × 100)`, e o resto (`100 − soma`) vai para
    "Outras".
  - Se o resto for negativo e "Outras" ficaria abaixo de 0, o excedente sai da maior fatia. Assim
    nenhuma fatia é negativa e a soma é sempre 100.
  - Sem gastos → `empty: true`, `slices: []`.
  - **Bloco de renda:**
    - receita 0 → `null`;
    - receita maior que zero e resultado ≥ 0 → `{kind: 'left', percent: round(resultado ÷ receita × 100)}`;
    - resultado negativo → `{kind: 'over', percent: round(−resultado ÷ receita × 100)}`.
  - O mês sai como "novembro de 2026".
- **Desenho:**
  - Canvas 1080 × 1350.
  - Os tokens vêm de `getComputedStyle(document.documentElement)`: primary, text, bg e a fonte Inter.
  - Barras horizontais por fatia.
  - Rodapé "Feito com Liberdade · {domínio}", com o domínio de `window.location.host`.
  - A Fundadora ganha o acento do tema e o selo "★ Família Fundadora".
  - Nenhum nome de pessoa nem valor em reais.
- **Tela:**
  - Prévia (canvas visível, `aria-label` com o resumo em texto) e três checkboxes para esconder
    "Categorias", "Renda" e "Mês".
  - Botão "Compartilhar", desativado quando `empty`, com o texto "Ainda não há gastos neste mês".
- **Compartilhar:**
  1. Gera o PNG com `canvas.toBlob(..., 'image/png')`.
  2. Cria `new File([blob], 'retrato-AAAA-MM.png', { type: 'image/png' })`.
  3. Se `nav.canShare?.({ files })`, chama `nav.share({ files })`. `AbortError` → `cancelled`.
  4. Senão, chama o `download` que já existe em `features/privacy/download.ts`. Se a fronteira do
     ESLint não deixar importar, mova o helper para `shared/download.ts` neste commit.
- **Sem rede:** o Retrato não faz `fetch`. O teste confere que o MSW não recebe nenhuma chamada nova
  depois que os dados do painel estão em cache.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `portraitData.test.ts.mergesSixthIntoOther` | 6 categorias + Outras → 5 fatias + "Outras" com a soma da 6ª e das Outras |
| `.roundingRemainderGoesToOther` | gastos 3 × R$ 33,33 + R$ 0,01 → fatias 33, 33, 33 e "Outras" 1; soma 100 |
| `.tinyCategoryDoesNotGoNegative` | uma categoria de R$ 0,01 em R$ 10.000 → 0% (sai) e soma 100 |
| `.zeroIncomeHidesIncomeBlock` | receita 0 → `income null` |
| `.spentMoreThanIncome` | receita 1000, gasto 1250 → `{kind: 'over', percent: 25}` |
| `.leftoverIncome` | receita 1000, gasto 700 → `{kind: 'left', percent: 30}` |
| `.emptyMonth` | sem gastos → `empty true` |
| `.slicesAlwaysSumTo100` (laço de 1000 casos, semente `20261102`, 1 a 7 categorias com centavos de 1 a 10⁷) | soma = 100; toda fatia ≥ 0; no máximo 6 fatias |
| `PortraitPage.test.tsx.disablesShareWhenEmpty` | painel sem gastos → "Ainda não há gastos neste mês" e botão desativado |
| `.hidesBlocks` | desmarcar "Renda" → `drawPortrait` chamado com `hidden` contendo `income` (spy) |
| `.noNetworkCalls` | depois do primeiro render, o contador de requisições do MSW não muda ao alternar blocos e compartilhar |
| `.founderBadge` | `usePlan` `founder true`, `theme OCEAN` → `drawPortrait` com `founder true`, `theme OCEAN` |
| `sharePortrait.test.ts.sharesWhenCanShare` | `canShare` true → `share` chamado com um `File` `retrato-2026-11.png` → `'shared'` |
| `.downloadsOtherwise` | sem `canShare` → `download` chamado com o mesmo nome → `'downloaded'` |
| `.abortIsCancelled` | `share` rejeita com `AbortError` → `'cancelled'` |
| `DashboardPage.test.tsx.linksToPortrait` | botão "Retrato do mês" leva a `/painel/retrato?mes=2026-11` |

No jsdom, `HTMLCanvasElement.prototype.getContext` devolve um stub com os métodos usados, e o
`toBlob` é um stub que chama o callback com um `Blob` vazio.

- [ ] **Step 2: Rodar e ver falhar.** Run (na raiz): `corepack pnpm --dir web test -- portrait DashboardPage`.
  Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica".

- [ ] **Step 4: Rodar e ver passar.** Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check`, `corepack pnpm --dir web build` e
  `corepack pnpm --dir web check:chunks`. O chunk do Retrato fica ≤ 260 KB e o inicial ≤ 200 KB.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "feat(web): add the month portrait card, drawn and shared on the device"
```

---

### Task 14: E2E da compra e do Retrato

**Files:**
- Create: `web/e2e/billing.spec.ts`.
- Modify: `web/e2e/support.ts` (helpers `postAsaasWebhook(page, payload)` e `stubCheckoutHost(page)`), `web/e2e/a11y.spec.ts` (Plano e Retrato).
- Test: o próprio `billing.spec.ts`, mais o `core-without-modules.spec.ts` (S2) sem mudança.

**Interfaces:**
- Consumes: o perfil `e2e` da Task 2 (gateway falso, host `checkout.e2e.test` e token de teste de 40
  caracteres) e as rotas das Tasks 4 a 8.
- Produces: os helpers do `support.ts`.
  - `stubCheckoutHost(page)` faz `page.route('https://checkout.e2e.test/**', ...)` e responde uma
    página simples "Pagamento de teste".
  - `postAsaasWebhook(request, body)` faz `POST http://localhost:8080/v1/billing/webhooks/asaas` com
    o header `asaas-access-token` de teste.

**Como fica:** o token de teste do e2e fica numa constante do `support.ts`, comentada como valor de
teste, igual ao do `application-e2e.properties`.

- [ ] **Step 1: Escrever os testes (falhando)**

| Teste | Esperado |
|---|---|
| `owner buys, becomes Founder and refunds` | 1. `newUser` → `createFamily` → Mais → Plano. 2. Clicar "Ser Família Fundadora"; a navegação vai para `checkout.e2e.test` (stub). 3. `postAsaasWebhook` com `CHECKOUT_PAID` do `chk_fake_{orderId}` (o `orderId` sai da URL). 4. `page.goto('/mais/plano?pedido={orderId}')` → "★ Família Fundadora". 5. Trocar o tema para "Oceano". 6. "Receber meu dinheiro de volta" → confirmar → "Reembolso em processamento". 7. `postAsaasWebhook` `PAYMENT_REFUNDED` → recarregar → "Seu plano: Grátis" |
| `adult sees the plan but no buttons` | membro `ADULT` → "Quem cuida do plano é o Dono da família" |
| `portrait shares without network` | lançar um gasto → painel → "Retrato do mês" → a prévia aparece; com `page.on('request')` contando, compartilhar (com `canShare` falso) dispara um download `retrato-AAAA-MM.png` e nenhuma requisição nova |
| `a11y.spec.ts` | `/mais/plano` e `/painel/retrato` sem violações do axe |
| `core-without-modules.spec.ts` (S2) | passa sem compra |

O `cspAndServerErrorGuard` continua ativo em todos.

- [ ] **Step 2: Rodar e ver falhar.** Run (na raiz): `corepack pnpm --dir web e2e -- billing`, com o
  compose de e2e no ar. Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Como fica". Se o `page.goto` para o host falso violar a CSP
  (`navigate-to` não existe hoje), ajuste só o teste, nunca a CSP.

- [ ] **Step 4: Rodar e ver passar.** Toda a suíte E2E. Expected: PASS.

- [ ] **Step 5: Conferir.** `corepack pnpm --dir web check` → verde.

- [ ] **Step 6: Commit**

```bash
git add web
git commit -m "test(e2e): cover the Founder purchase, the refund and the month portrait"
```

---

### Task 15: Alarme de cobrança no OpenTofu

**Files:**
- Create: `infra/live/billing_alerts.tf`.
- Modify: `infra/live/tests/operations.tftest.hcl`.

**Interfaces:**
- Consumes: `aws_cloudwatch_log_group.main` (com `for_each` em `local.log_group_names`) e
  `aws_sns_topic.alerts`.
- Produces:
  - `aws_cloudwatch_log_metric_filter.billing_alert` com `for_each` sobre
    `{ staging = "/liberdade/staging", production = "/liberdade/production" }`:
    - padrão `"BILLING_ALERT"`;
    - métrica `BillingAlerts` no namespace `Liberdade/${each.key}`, valor `1` e `default_value` `0`.
  - `aws_cloudwatch_metric_alarm.billing_alert` com o mesmo `for_each`:
    - `Sum >= 1` em 1 período de 300 s;
    - `treat_missing_data = "notBreaching"`;
    - `alarm_actions` e `ok_actions` em `[aws_sns_topic.alerts.arn]`.

- [ ] **Step 1: Escrever os testes (falhando)** em `operations.tftest.hcl` (`command = plan`):

| Teste | Esperado |
|---|---|
| `billing_alert_filters` | dois filtros, nos grupos `/liberdade/staging` e `/liberdade/production`, padrão `"BILLING_ALERT"` |
| `billing_alarms_notify_alerts` | dois alarmes com `alarm_actions` = tópico `alerts`, `threshold = 1`, `treat_missing_data = "notBreaching"` |

- [ ] **Step 2: Rodar e ver falhar.** Run (em `infra/live`): `tofu test`. Expected: FAIL.

- [ ] **Step 3: Implementar.** Siga "Interfaces". Nenhum outro recurso muda.

- [ ] **Step 4: Rodar e ver passar.** `tofu fmt -check -recursive` e `tofu test`. Expected: PASS.

- [ ] **Step 5: Conferir.** O job de infra do CI (o mesmo do Plano 5) fica verde.

- [ ] **Step 6: Commit**

```bash
git add infra
git commit -m "feat(infra): alarm by email on billing alerts in staging and production"
```

---

### Task 16: Runbook do sandbox e documentação

**Files:**
- Create: `docs/runbooks/billing-sandbox.md`.
- Modify: `docs/runbooks/go-live.md:288-333` (seção 9 do SSM: 8 → 12 parâmetros no staging, com a nota de que a produção continua com 8 até a cobrança ligar), `docs/architecture.md` (módulo `billing`, gateway, webhook e alarme), `docs/data-model.md` (as três tabelas e o CHECK da auditoria).

**Como fica:** o runbook tem passos numerados, em PT-BR, cada um com o resultado esperado.
1. **Conta e chave.** Criar a conta de sandbox do Asaas e gerar a chave de API.
2. **Token do webhook.** Gerar com `openssl rand -hex 32` (64 caracteres, dentro de 32 a 255) num
   terminal local, sem colar em lugar nenhum além do SSM e do painel do Asaas.
3. **Parâmetros no SSM.** `aws ssm put-parameter` para os quatro parâmetros `String` do staging e
   os dois `SecureString` com `--key-id alias/liberdade`. O runbook diz que valores com `'` ou CR
   quebram o `deploy.sh`.
4. **Webhook no Asaas.** URL `https://{staging}/v1/billing/webhooks/asaas`, com o token e os eventos
   `CHECKOUT_PAID`, `CHECKOUT_EXPIRED`, `CHECKOUT_CANCELED`, `PAYMENT_REFUNDED`,
   `PAYMENT_CHARGEBACK_REQUESTED` e `PAYMENT_CHARGEBACK_DISPUTE`.
5. **Alarme.** Rodar o `tofu apply` e confirmar a assinatura do e-mail, se ainda não estiver
   confirmada.
6. **Deploy e conferências.** Fazer o deploy do staging. Conferir que `/termos` mostra
   `terms-2026-12` e que o aceite novo aparece.
7. **Compra por Pix.** Comprar com o Pix de sandbox e ver a família virar Fundadora. Conferir no
   banco que `asaas_payment_id` foi gravado (risco 1 do spec). Se ficar nulo depois de 1 h, abrir
   issue.
8. **Compra por cartão.** Comprar por cartão de teste do Asaas.
9. **Reembolso.** Pedir o reembolso e ver "Reembolso em processamento" e depois o Grátis.
10. **Expiração.** Deixar um checkout expirar (60 min) e ver o pedido `EXPIRED`.
11. **Alerta.** Disparar um alerta de teste: enviar um webhook com um tipo que force `WEBHOOK_ERROR`
    (procedimento no runbook) e ver o e-mail chegar.
12. **Retrato.** Gerar o Retrato no Android e no iOS e conferir compartilhar e baixar.
13. **Registro.** Registrar no PR de docs o resultado, as taxas cobradas no sandbox e os nomes reais
    dos eventos de estorno e chargeback.

Também tem uma seção "Antes da produção", com a seção 9 do spec como checklist.

- [ ] **Step 1: Escrever.** Siga "Como fica".

- [ ] **Step 2: Conferir.** Os links relativos novos apontam para arquivos que existem
  (`git ls-files` para cada caminho), e as duas buscas de dados pessoais saem com código 1. O CI não
  roda prettier em docs (issue #40); siga a quebra de linha em 100 colunas dos docs atuais.

- [ ] **Step 3: Commit**

```bash
git add docs
git commit -m "docs: add the Asaas sandbox runbook and document the billing module"
```

---

## Depois das tasks

- Revisão final da branch inteira (modelo mais capaz). Ela confere estes pontos:
  - o Review Focus;
  - as Global Constraints;
  - nenhum valor, id do Asaas ou token em log;
  - o `ApplicationModulesTest`;
  - o orçamento de bundle.
- PR 2 com a descrição em PT-BR, o checklist do runbook para o mantenedor e o custo: R$ 0 fixo,
  alarmes na cota grátis do CloudWatch, ≈ R$ 4,59/mês fora dela.
