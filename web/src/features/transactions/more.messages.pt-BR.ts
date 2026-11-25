/**
 * The text of who spent, of a removed member's name, of a transaction that is gone and of the planned
 * ones. It loads with the lazy modules that show it (see `registerMessages`), never with the app.
 */
export const transactionsMoreMessages = {
  'transactions.who': 'Quem',
  'transactions.memberLeft': '{name} (saiu)',
  'transactions.familyAccount': 'Conta da família',
  'transactions.deletedPlain': 'Este lançamento foi excluído',
  'transactions.goneNow': 'Este lançamento não existe mais',
  'transactions.backToList': 'Voltar para os lançamentos',
  'transactions.importStatement': 'Importar extrato',
  'planned.title': 'Previstos',
  'planned.sheetTitle': 'Previsto',
  'planned.confirm': 'Confirmar',
  'planned.confirmNamed': 'Confirmar {name}',
  'planned.skip': 'Pular',
  'planned.date': 'Data',
  'planned.overdue': 'venceu em {date}',
  'planned.skipped': 'Previsto pulado',
  'planned.confirmed': 'Previsto confirmado.',
  'planned.changed': 'Esse previsto mudou. Confira a lista.',
} as const
