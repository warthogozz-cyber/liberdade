/**
 * The text of the recurring rules: their screen, their form and the one-line description of a rule.
 * It loads with the planning module (see `register.ts`), never with the app.
 */
export const recurrencesMessages = {
  'recurrences.title': 'Recorrências',
  'recurrences.new': 'Nova recorrência',
  'recurrences.edit': 'Editar recorrência',
  'recurrences.archive': 'Arquivar recorrência',
  'recurrences.archiveBody':
    'Os previstos que ainda não foram confirmados somem. O que já foi lançado fica.',
  'recurrences.archiveCancel': 'Manter',
  'recurrences.empty': 'Nenhuma recorrência ainda.',
  'recurrences.next': 'próximo: {date}',
  'recurrences.ended': 'sem próximo previsto',
  'recurrences.saved': 'Recorrência salva.',
  'recurrences.archived': 'Recorrência arquivada.',
  'recurrences.changed': 'Essa recorrência mudou. Abra a lista de novo.',
  'recurrences.repeat': 'Repete',
  'recurrences.frequency.WEEKLY': 'Toda semana',
  'recurrences.frequency.MONTHLY': 'Todo mês',
  'recurrences.frequency.YEARLY': 'Todo ano',
  'recurrences.every': 'A cada',
  'recurrences.unit.WEEKLY': '{interval, plural, one {semana} other {semanas}}',
  'recurrences.unit.MONTHLY': '{interval, plural, one {mês} other {meses}}',
  'recurrences.unit.YEARLY': '{interval, plural, one {ano} other {anos}}',
  'recurrences.startOn': 'Começa em',
  'recurrences.endOn': 'Termina em',
  'recurrences.rule.WEEKLY':
    '{interval, plural, one {Toda semana} other {A cada # semanas}}, {weekday}',
  'recurrences.rule.MONTHLY':
    '{interval, plural, one {Todo mês} other {A cada # meses}}, dia {day}',
  'recurrences.rule.YEARLY': '{interval, plural, one {Todo ano} other {A cada # anos}}, {date}',
} as const
