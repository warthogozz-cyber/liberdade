/** The text of the statement import. It loads with the lazy import screens (see `registerMessages`), never with the app. */
export const importMessages = {
  'import.columns.title': 'Quais colunas são quais?',
  'import.columns.intro':
    'Não deu para reconhecer as colunas deste arquivo. Escolha a de cada uma.',
  'import.columns.choose': 'Escolha a coluna',
  'import.columns.column': 'Coluna {n}',
  'import.columns.date': 'Data',
  'import.columns.description': 'Descrição',
  'import.columns.amount': 'Valor',
  'import.columns.debit': 'Débito',
  'import.columns.credit': 'Crédito',
  'import.columns.mode': 'Como o valor aparece',
  'import.columns.modeSigned': 'Uma coluna, com sinal',
  'import.columns.modeSplit': 'Duas colunas, débito e crédito',
  'import.columns.sample': 'Primeiras linhas do arquivo',
  'import.columns.continue': 'Continuar',
  'import.title': 'Importar extrato',
  'import.intro':
    'Escolha a conta e o arquivo OFX ou CSV que o banco exporta. O arquivo é lido aqui no aparelho.',
  'import.account': 'Conta',
  'import.file': 'Arquivo do extrato',
  'import.reading': 'Lendo o arquivo…',
  'import.offline': 'Sem conexão. A importação precisa de internet.',
  'import.error.unreadable':
    'Não conseguimos ler este arquivo. Ele precisa ser OFX ou CSV exportado pelo banco.',
  'import.error.empty': 'Nenhum lançamento no arquivo.',
  'import.error.tooLarge': 'Este arquivo é grande demais. Exporte um período menor.',
  'import.error.existing':
    'Não deu para conferir os lançamentos que já existem. Confira a conexão e tente de novo.',
  'import.preview.title': 'Confira os lançamentos',
  'import.preview.intro':
    'Marque o que deve entrar e escolha a categoria de cada um. As linhas desmarcadas ficam de fora.',
  'import.preview.categoryOf': 'Categoria de {description}',
  'import.state.alreadyImported': 'Já importado',
  'import.state.confirmPlanned': 'Confirmar o previsto',
  'import.state.possibleRepeat': 'Possível repetido',
  'import.state.billPayment': 'Pagamento da fatura?',
  'import.billPaymentNotice':
    'Pagamento da fatura? Ele já aparece como saída na conta de onde saiu o dinheiro.',
  'import.bulk':
    'Usar a mesma categoria {n, plural, one {na # sem categoria} other {nas # sem categoria}}',
  'import.needsCategory':
    'Falta escolher a categoria de {n, plural, one {# linha marcada} other {# linhas marcadas}}.',
  'import.submit': 'Importar {n, plural, one {# lançamento} other {# lançamentos}}',
  'import.sending': 'Importando…',
  'import.result.title': 'Importação concluída',
  'import.result.partialTitle': 'Parte da importação não entrou',
  'import.result.counts':
    '{created, plural, =0 {# entraram} one {# entrou} other {# entraram}}, {confirmed, plural, =0 {# confirmaram um previsto} one {# confirmou um previsto} other {# confirmaram um previsto}}, {skipped, plural, =0 {# já existiam} one {# já existia} other {# já existiam}}',
  'import.result.partial':
    '{sent, plural, one {# lançamento foi enviado} other {# lançamentos foram enviados}} de {total}. Você pode tentar de novo: o que já entrou não se repete.',
  'import.result.retry': 'Tentar de novo',
  'import.result.list': 'Ver os lançamentos',
  'import.result.another': 'Importar outro arquivo',
} as const
