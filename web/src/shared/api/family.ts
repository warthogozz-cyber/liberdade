/**
 * The family's endpoints, mutations and hooks: members, invitations, recovery links and the family's
 * name. Only the lazy family screens use them, so the `shared/api` barrel does not re-export this
 * file; they import it directly and it stays out of the initial load.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { client, unwrap } from './client'
import { queryKeys } from './queryKeys'
import type {
  AcceptedInvitation,
  AcceptNewUser,
  CreateInvitationRequest,
  InvitationPreview,
  InvitationView,
  MeWorkspace,
  MemberView,
  UpdateMemberRequest,
} from './types'

/** The family's people, active first. Every role may read them. */
export function listMembers(ws: string): Promise<MemberView[]> {
  return unwrap(client.GET('/v1/workspaces/{ws}/members', { params: { path: { ws } } }))
}

/** Changes a member's role or nickname. A role change needs a recent sign-in (US-01). */
export function updateMember(
  ws: string,
  id: string,
  body: UpdateMemberRequest,
): Promise<MemberView> {
  return unwrap(
    client.PATCH('/v1/workspaces/{ws}/members/{id}', { params: { path: { ws, id } }, body }),
  )
}

/** Takes a person out of the family. The last Owner is a 422 `member.last-owner`. */
export async function removeMember(ws: string, id: string): Promise<void> {
  await unwrap(client.DELETE('/v1/workspaces/{ws}/members/{id}', { params: { path: { ws, id } } }))
}

/** The pending invitations; only Owners and Adults may read them. */
export function listInvitations(ws: string): Promise<InvitationView[]> {
  return unwrap(client.GET('/v1/workspaces/{ws}/invitations', { params: { path: { ws } } }))
}

/** Creates an invitation. The answer is the only one that carries the link's `token`. */
export function createInvitation(
  ws: string,
  body: CreateInvitationRequest,
): Promise<InvitationView> {
  return unwrap(client.POST('/v1/workspaces/{ws}/invitations', { params: { path: { ws } }, body }))
}

export async function revokeInvitation(ws: string, id: string): Promise<void> {
  await unwrap(
    client.DELETE('/v1/workspaces/{ws}/invitations/{id}', { params: { path: { ws, id } } }),
  )
}

/** A 24-hour, single-use link that brings a passkey-only member back in. */
export function createRecoveryLink(ws: string, id: string): Promise<InvitationView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/members/{id}/recovery', { params: { path: { ws, id } } }),
  )
}

export function renameWorkspace(ws: string, name: string): Promise<MeWorkspace> {
  return unwrap(client.PATCH('/v1/workspaces/{ws}', { params: { path: { ws } }, body: { name } }))
}

/** Who invited, to which family and as what, for a link's token. A gone link is a 410, an unknown one a 404. */
export function previewInvitation(token: string): Promise<InvitationPreview> {
  return unwrap(client.POST('/v1/invitations/preview', { body: { token } }))
}

/** Joins the family, or brings a member back. `newUser` is for a person with no session. */
export function acceptInvitation(
  token: string,
  newUser?: AcceptNewUser,
): Promise<AcceptedInvitation> {
  return unwrap(client.POST('/v1/invitations/accept', { body: { token, newUser } }))
}

/** The family's people, active first. */
export function useMembers(ws: string) {
  return useQuery({ queryKey: queryKeys.members(ws), queryFn: () => listMembers(ws) })
}

/** The pending invitations; the screen asks only for the roles the API lets read them. */
export function useInvitations(ws: string) {
  return useQuery({ queryKey: queryKeys.invitations(ws), queryFn: () => listInvitations(ws) })
}

/** The family and the signed-in person's own row both read these, so a change refreshes both. */
function useRefreshFamily(ws: string) {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.workspace(ws) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.me }),
    ])
}

/** Changes a member's role or nickname. */
export function useUpdateMember(ws: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateMemberRequest }) =>
      updateMember(ws, id, body),
    onSettled: useRefreshFamily(ws),
  })
}

/** Takes a member out of the family. */
export function useRemoveMember(ws: string) {
  return useMutation({
    mutationFn: (id: string) => removeMember(ws, id),
    onSettled: useRefreshFamily(ws),
  })
}

/** Creates an invitation; the answer carries the link's token once. */
export function useCreateInvitation(ws: string) {
  return useMutation({
    mutationFn: (body: CreateInvitationRequest) => createInvitation(ws, body),
    onSettled: useRefreshFamily(ws),
  })
}

export function useRevokeInvitation(ws: string) {
  return useMutation({
    mutationFn: (id: string) => revokeInvitation(ws, id),
    onSettled: useRefreshFamily(ws),
  })
}

/** Makes a recovery link for a member; the answer carries the link's token once. */
export function useCreateRecoveryLink(ws: string) {
  return useMutation({ mutationFn: (memberId: string) => createRecoveryLink(ws, memberId) })
}

export function useRenameWorkspace(ws: string) {
  return useMutation({
    mutationFn: (name: string) => renameWorkspace(ws, name),
    onSettled: useRefreshFamily(ws),
  })
}

/**
 * Reads a link. A mutation, not a query: the token travels in the body, and a query would put it in
 * a cache key.
 */
export function usePreviewInvitation() {
  return useMutation({ mutationFn: (token: string) => previewInvitation(token) })
}

/** Joins the family (or recovers access) with a link's token. */
export function useAcceptInvitation() {
  return useMutation({
    mutationFn: ({ token, newUser }: { token: string; newUser?: AcceptNewUser }) =>
      acceptInvitation(token, newUser),
  })
}
