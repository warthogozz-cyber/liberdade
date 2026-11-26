# Runbook: conferir um estado para ler a NFC-e

Roteiro para o mantenedor. Serve para decidir, estado por estado, se a API pode um dia abrir a página
da nota para ler os itens e os preços. A 10.2 entrega só o modo manual (a pessoa digita os preços);
cada estado que passar aqui ganha um plano 10.2.x com o leitor.

**Nada daqui vai para o repositório ou para o PR:** nem nota real, nem chave, nem CPF ou CNPJ de
gente de verdade, nem dado de quem comprou. Nos exemplos abaixo, os valores são inventados
(`<CHAVE>`, `<UF>`, `<CNPJ>`).

## Regra de ouro

Só se lê a página de um estado quando **os termos de uso e o `robots.txt` do portal permitem**. Sem
permissão clara, o estado fica no modo manual, e isso é uma resposta válida. Onde a leitura é
permitida, ela sempre vem com cache, limite de requisições e a identificação do robô (as cinco regras
mais abaixo).

## Situação em 24/11/2026

| Estado | Situação | Motivo |
| --- | --- | --- |
| RS | Candidato | `robots.txt` não proíbe |
| MG | Candidato | `robots.txt` não proíbe |
| BA | Candidato | `robots.txt` não proíbe |
| PR | Candidato | `robots.txt` não proíbe |
| SC | Bloqueado | `robots.txt` com `Disallow: /` |
| SP | Bloqueado | firewall contra robôs |
| RJ | Bloqueado | bloqueio por IP e aviso de sigilo fiscal |

Vários estados usam o portal do SVRS (Sefaz Virtual do Rio Grande do Sul). Um leitor só pode servir
a todos eles: confira o portal uma vez e anote quais estados caem nele.

## Lista de conferência por estado

Faça com o navegador, sem robô, e sem pressa. Marque cada item e anote a data.

1. **`robots.txt`.** Abra `https://<portal-do-estado>/robots.txt`. O caminho da consulta da NFC-e
   está proibido (`Disallow`) para robôs em geral? Se sim, **pare**: o estado fica bloqueado.
2. **Termos de uso.** Leia os termos do portal de consulta. Eles proíbem leitura automática, coleta ou
   uso por programa? Se sim ou se não ficar claro, **pare**.
3. **Captcha.** Abra a página de uma nota pelo link do QR. Pede captcha, login ou certificado? Se
   pedir, **pare**: não se contorna captcha nem proteção contra robô.
4. **Uma nota real, aberta à mão.** Escaneie o QR de uma nota sua, abra o link no navegador e veja se
   a página mostra, para cada item: descrição, quantidade, unidade e preço unitário. Anote a data e o
   resultado. A nota é sua e fica só com você.
5. **Cabe numa tabela só?** Diga se a página é a mesma para todos os estados do portal (SVRS) ou se
   cada estado tem a sua.

Passou nos cinco: registre na tabela do fim e peça o plano 10.2.x.

## Como salvar a página de exemplo

O teste do leitor precisa de uma página de exemplo, feita a partir da real, **sem a parte do
consumidor**.

1. Com a nota aberta no navegador, use "Salvar como" e escolha "Página da Web, somente HTML".
2. Abra o arquivo salvo num editor de texto.
3. Apague a parte do consumidor (CPF, nome) e troque por dados inventados (`<CPF>`, `<NOME>`). Troque
   também a chave de acesso e o CNPJ do emitente por valores inventados.
4. Guarde o arquivo **fora do repositório**. Ele só entra no plano 10.2.x como página de exemplo
   inventada, feita à mão a partir dele, nunca a página real.

## As cinco regras dos planos 10.2.x

Cada plano de leitor segue estas regras (spec 6.5, "Leitor por estado"):

1. **Busca automática só se o estado estiver em `liberdade.markets.nfce.fetch-states`** (vazia por
   padrão; variável `LIBERDADE_MARKETS_NFCE_FETCH_STATES`). A API monta a URL a partir do modelo do
   estado na configuração (`liberdade.markets.nfce.states.<UF>.url`) e do `p` do QR. **Nunca abre uma
   URL vinda do aparelho** (sem SSRF).
2. **Cache, limite e identificação do robô.** Uma nota já lida não é buscada de novo (cache). Abre a
   página uma vez (tempo limite de 10 s, sem nova tentativa), com `User-Agent` do robô, no máximo 1 requisição a cada 4 s por estado em cada
   processo (staging e produção dividem o IP) e 30 notas por família por dia. Lê o HTML e devolve o
   nome do emitente, a data e, para cada item, descrição, código, GTIN (se a página mostrar),
   quantidade, unidade e preço unitário.
3. **Ignora a parte do consumidor** (CPF, nome) e nunca guarda nem registra a página em log. Captcha,
   HTML que o leitor não entende, erro ou tempo esgotado: cai no modo manual, com
   `MARKETS_ALERT type=NFCE_PARSE_FAILED uf=<UF>` (sem a chave).
4. **O teste usa uma página de exemplo** feita a partir da página real, com a parte do consumidor
   trocada por dados inventados. A página real nunca entra no repositório.
5. **No staging, o estado fica na lista só durante a conferência.** Depois do merge do plano, ponha o
   estado em `LIBERDADE_MARKETS_NFCE_FETCH_STATES` do staging, confira, e só então da produção.

O primeiro plano 10.2.x também traz a política nova (a API passa a consultar a SEFAZ) e a linha da
NFC-e na página `/robo`.

## Registro das conferências

Preencha uma linha por estado conferido. "Resultado" diz passou ou parou, e em qual item da lista.

| Estado | Data | Resultado | Plano |
| --- | --- | --- | --- |
|  |  |  |  |
