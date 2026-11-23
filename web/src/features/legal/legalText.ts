export type MessageId = FormatjsIntl.Message['ids']

/** One block inside a section: a paragraph, or a list. */
export type Block = { p: MessageId } | { list: MessageId[] }

export interface Section {
  title: MessageId
  blocks: Block[]
}

/** One version of the terms or of the policy, as the page shows it. */
export interface LegalText {
  /** The month the text came out, as the page writes it ("novembro de 2026"). */
  updatedAt: string
  intro: MessageId
  sections: Section[]
}
