/** What one tab tells the others about the session (D26). */
export type SessionMessage = { type: 'signed-out' } | { type: 'signed-in'; userId: string }

const CHANNEL_NAME = 'liberdade-session'

function open(): BroadcastChannel | undefined {
  return typeof BroadcastChannel === 'undefined' ? undefined : new BroadcastChannel(CHANNEL_NAME)
}

function send(message: SessionMessage): void {
  try {
    const channel = open()
    channel?.postMessage(message)
    channel?.close()
  } catch {
    // The other tabs find out on their next request.
  }
}

/** Tells the other tabs this one signed out. A no-op where the browser has no BroadcastChannel. */
export function broadcastSignedOut(): void {
  send({ type: 'signed-out' })
}

/** Tells the other tabs this one signed in as `userId`. */
export function broadcastSignedIn(userId: string): void {
  send({ type: 'signed-in', userId })
}

function parse(data: unknown): SessionMessage | undefined {
  if (typeof data !== 'object' || data === null) return undefined
  const { type, userId } = data as { type?: unknown; userId?: unknown }
  if (type === 'signed-out') return { type }
  if (type === 'signed-in' && typeof userId === 'string' && userId !== '') {
    return { type, userId }
  }
  return undefined
}

/** Listens to the other tabs' messages, ignoring any with another shape. Returns the unsubscribe. */
export function onSessionMessage(handler: (message: SessionMessage) => void): () => void {
  let channel: BroadcastChannel | undefined
  try {
    channel = open()
  } catch {
    return () => {}
  }
  if (!channel) return () => {}
  channel.onmessage = (event: MessageEvent) => {
    const message = parse(event.data)
    if (message) handler(message)
  }
  return () => channel.close()
}
