# ADR-0005: Login sem senha — Google + passkeys

- **Status:** Aceito
- **Data:** 2026-11-02

## Contexto

- Regra do projeto: nunca pedir nem guardar senhas; usar login oficial com escopos mínimos.
- A família tem adultos (com conta Google) e jovens (que podem não ter e-mail).
- Exigência de 2FA e de OWASP ASVS nível 2.

## Decisão

- **"Entrar com Google"** via OpenID Connect no Spring Security, com escopos `openid email profile`,
  PKCE e `nonce`. O app guarda só o `sub`, o nome e o e-mail.
- **Passkeys (WebAuthn)** com o suporte nativo do Spring Security: oferecidas depois do primeiro
  login e usadas por quem entra por convite sem conta Google (ex.: Jovem).
- **Sem senha própria.** O segundo fator vem do Google (2FA da conta) ou da própria passkey
  (aparelho + biometria ou PIN).
- **Sessão no servidor** (Spring Session JDBC) com cookie `__Host-sid` `HttpOnly`, `Secure`,
  `SameSite=Lax`; expira após 30 dias sem uso.
- **Reautenticação** para ações sensíveis (login nos últimos 10 min).
- **Recuperação** para quem só tem passkey: Dono ou Adulto gera um link de uso único, válido por
  24 h, auditado e avisado ao membro.
- **Limite de tentativas** em login, convites e recuperação.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Amazon Cognito | Grátis até 10 mil usuários por mês, mas telas pouco personalizáveis e mais acoplamento à AWS |
| E-mail + senha + 2FA próprio | Hash, bloqueio, recuperação e envio de e-mail por nossa conta: mais superfície de ataque |
| Tokens JWT no navegador | Mais difícil de revogar e exposto a XSS; cookie `HttpOnly` é mais seguro para um PWA do mesmo domínio |

## Consequências

- Nenhuma senha para vazar; nenhum serviço de e-mail necessário no MVP.
- Dependência do Google, mitigada pelas passkeys.
- Front e API precisam do mesmo domínio-raiz (`app.` e `api.liberdadefamilia.com.br`) para o
  cookie funcionar; por isso o domínio precisa estar comprado antes do primeiro deploy.
- Previews de PR (`pages.dev`) não fazem login real; usam dados de exemplo
  ([ADR-0009](0009-ci-cd-release-please-ghcr-ssm.md)).
