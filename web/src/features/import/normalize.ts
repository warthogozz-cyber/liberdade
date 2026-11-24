/**
 * The key a description is compared by: no accents, lower case, no installment marks, and only letters, digits
 * and single spaces. It must equal `DescriptionKey.of` in the API for every input.
 */

const MARKS = /\p{M}+/gu
// Java's `\s` is ASCII only; JS's would also match U+00A0 and other Unicode spaces, so spell the class out.
const SPACE = String.raw`[ \t\n\x0B\f\r]`
const INSTALLMENT = new RegExp(
  String.raw`\b(?:parc(?:ela)?\.?${SPACE}*)?\d{1,2}${SPACE}*(?:/|de)${SPACE}*\d{1,2}\b`,
  'g',
)
const SEPARATORS = /[^a-z0-9]+/g

export function normalizeDescription(text: string): string {
  const plain = text.normalize('NFD').replace(MARKS, '').toLowerCase()
  return plain.replace(INSTALLMENT, '').replace(SEPARATORS, ' ').trim()
}
