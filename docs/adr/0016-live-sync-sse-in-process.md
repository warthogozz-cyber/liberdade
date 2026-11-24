# ADR-0016: Avisos ao vivo por SSE, no processo

- **Status:** Aceito em 2026-11-24, com as decisões do
  [plano 7.3](../superpowers/plans/2026-11-24-liberdade-plan-7-3-live-sync-sound-motion.md).
- **Data:** 2026-11-24

## Contexto

- Uma família usa o app em mais de um aparelho. Hoje, quem lança um gasto só aparece para os outros
  quando eles recarregam ou voltam à aba.
- O app é um PWA com um único processo de API, atrás do Caddy
  ([ADR-0004](0004-hosting-aws-sa-east-1-lean.md)). Não há fila de mensagens nem Redis, e a
  intenção é continuar assim ([ADR-0003](0003-modular-monolith-spring-modulith.md)).
- Os dados de cada pessoa têm regras de visibilidade: o Jovem só vê as contas dele
  ([ADR-0008](0008-workspace-isolation.md)). Um aviso não pode vazar o que a pessoa não pode ler.

## Decisão

- **SSE, não WebSocket.** `GET /v1/workspaces/{ws}/events` devolve `text/event-stream`. É uma via só
  (servidor para o navegador), passa pelo HTTP comum, usa o cookie de sessão e o navegador reconecta
  sozinho.
- **No processo.** O módulo `live` da API guarda os streams abertos em memória, por família. Os
  eventos de domínio que já existem (`TransactionChanged`, `GoalChanged` e outros) viram avisos
  depois do commit. Uma batida a cada 25 s mantém a conexão viva e fecha o stream de quem perdeu a
  sessão ou a participação.
- **Só ids, nunca dados.** O aviso diz o que mudou, por exemplo `{"entity":"transaction","id":"…"}`.
  Valores, nomes e descrições não viajam pelo stream.
- **Filtro por pessoa.** Cada stream é de uma pessoa e de um papel. O Jovem não recebe avisos do
  orçamento, da reserva e das recorrências, e só recebe os de contas que ele pode ver. O mesmo vale
  quando a conta deixa de ser dele: o antigo dono recebe o aviso.
- **Refetch no cliente.** O app junta os avisos por 250 ms e busca de novo as consultas afetadas
  pelo caminho de sempre, com as mesmas regras de acesso. Se o stream não abrir, o app busca tudo
  de novo quando a pessoa volta à aba ou à rede.
- **Limites.** No máximo 5 streams por pessoa e 1.000 por processo. O sexto stream da mesma pessoa
  toma o lugar do mais antigo, que recebe o evento `replaced` e fecha; a aba que o recebe para de
  reconectar e segue sem stream. É assim porque a página recarregada deixa o stream antigo aberto
  até a próxima escrita mostrar que ele caiu, e recusar o novo deixaria a página nova sem avisos.
  Acima do limite do processo, a API responde `429` com `Retry-After: 60` e o app segue sem stream.
  Cada stream dura até 30 min; ao fim, o navegador reconecta.
- **`LiveStreams` é o ponto de troca.** A interface (`open`, `publish`, `closeMember`,
  `closeWorkspace`, `all`, `close`) não expõe o mapa em memória. Se a API passar a rodar em mais de
  um processo, uma implementação com `LISTEN/NOTIFY` do PostgreSQL entra no lugar de
  `InMemoryLiveStreams`, sem mudar o relay, o controller nem o app.

## Alternativas consideradas

- **WebSocket.** Traz duas vias e um protocolo próprio, e o app não precisa mandar nada pelo canal.
  Mais código e mais risco com o proxy, sem ganho aqui.
- **Push do navegador (Web Push).** Serve para avisar com o app fechado, o que não é o objetivo.
  Exige chave, permissão e serviço de terceiros.
- **Payload com dados.** Evitaria a segunda busca, mas obrigaria a filtrar e a formatar dados em dois
  lugares. Com só ids, a regra de acesso fica toda nas rotas que já existem.
- **`LISTEN/NOTIFY` já agora.** Só faz sentido com mais de um processo. Hoje seria uma conexão a mais
  do banco por processo, sem uso.
- **Polling.** Gasta bateria e requisições em todas as famílias para ganhar pouco.

## Consequências

- Com mais de um processo de API, um aviso só chega a quem está ligado ao mesmo processo. Antes de
  escalar para fora, trocar a implementação de `LiveStreams` (acima).
- Reiniciar a API (um deploy) fecha todos os streams. Os navegadores reconectam e buscam o que
  perderam.
- Um stream aberto ocupa uma conexão e uma thread virtual. Com o teto de 1.000, o custo é pequeno
  na escala atual.
- O Caddy já repassa `text/event-stream` sem segurar. Só se, no staging, o aviso demorar mais de
  5 s, entra `flush_interval -1` para a rota de eventos, em um PR próprio.

## Fora do escopo

- WebSocket e qualquer canal do app para o servidor.
- Notificações push com o app fechado.
- Dados dentro do aviso.
- Avisos de convites, do nome da família e do job diário das recorrências.
