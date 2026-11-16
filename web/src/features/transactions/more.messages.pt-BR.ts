/**
 * The text of who spent, of a removed member's name and of a transaction that is gone. It loads with
 * the lazy modules that show it (see `registerMessages`), never with the app.
 */
export const transactionsMoreMessages = {
  'transactions.who': 'Quem',
  'transactions.memberLeft': '{name} (saiu)',
  'transactions.familyAccount': 'Conta da família',
  'transactions.deletedPlain': 'Este lançamento foi excluído',
  'transactions.goneNow': 'Este lançamento não existe mais',
  'transactions.backToList': 'Voltar para os lançamentos',
} as const
