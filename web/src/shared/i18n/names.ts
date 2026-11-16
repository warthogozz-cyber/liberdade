/** The first word of a person's name, for greetings (D5). */
export function firstName(displayName: string): string {
  return displayName.trim().split(/\s+/)[0]
}
