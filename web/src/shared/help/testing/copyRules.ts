/** The ethics rule of the help copy: no text may press the person with loss or urgency. */
export const LOSS = /perd|perca|resta|expir|quebr|corra|última chance|não deixe|falta/i

/** A sentence is a stretch ending in `.`, `!` or `?` followed by a space or the end of the text. */
export function countSentences(text: string): number {
  return (text.match(/[^.!?]+[.!?]+(?=\s|$)/g) ?? []).length
}
