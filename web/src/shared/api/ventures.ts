/**
 * What only the projects (ventures) screens need: the family's projects, their steps and the
 * starting-point templates. The `shared/api` barrel does not re-export this file, so none of it
 * weighs on the initial load. Every call needs the connection: none goes through the offline queue
 * (ADR-0007); the list is kept on the device (the 9.2 screens read it offline), the rest is not.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { client, unwrap } from './client'
import { queryKeys } from './queryKeys'
import type { components } from './schema'

type Schemas = components['schemas']

export type VenturesView = Schemas['VenturesView']
export type VentureSummary = Schemas['VentureSummary']
export type VentureView = Schemas['VentureView']
export type TemplatesView = Schemas['TemplatesView']
export type CreateVentureRequest = Schemas['CreateVentureRequest']
export type UpdateVentureRequest = Schemas['UpdateVentureRequest']
export type ChangeStatusRequest = Schemas['ChangeStatusRequest']
export type VentureType = Schemas['VentureView']['type']
export type VentureFigures = Schemas['FiguresView']
export type TemplateView = Schemas['TemplateView']
export type TrackView = Schemas['TrackView']
export type SimulationRequest = Schemas['SimulationRequest']
export type StepView = Schemas['StepView']
export type CreateStepRequest = Schemas['CreateStepRequest']
export type UpdateStepRequest = Schemas['UpdateStepRequest']

/** Everything the projects read sits under the family key, so one invalidation of `all` reaches only them. */
export const venturesKeys = {
  all: (ws: string) => [...queryKeys.workspace(ws), 'ventures'] as const,
  list: (ws: string) => [...venturesKeys.all(ws), 'list'] as const,
  detail: (ws: string, id: string) => [...venturesKeys.all(ws), 'detail', id] as const,
  templates: (ws: string) => [...venturesKeys.all(ws), 'templates'] as const,
  /** `request` is the draft without any name: names do not change the figures. */
  simulation: (ws: string, request: SimulationRequest) =>
    [...venturesKeys.all(ws), 'simulation', request] as const,
}

/** The family's projects, with whether the plan lets it start another. */
export function getVentures(ws: string): Promise<VenturesView> {
  return unwrap(client.GET('/v1/workspaces/{ws}/ventures', { params: { path: { ws } } }))
}

/** One project with its figures and steps. */
export function getVenture(ws: string, id: string): Promise<VentureView> {
  return unwrap(client.GET('/v1/workspaces/{ws}/ventures/{id}', { params: { path: { ws, id } } }))
}

/** The starting-point templates, by track. */
export function getVentureTemplates(ws: string): Promise<TemplatesView> {
  return unwrap(client.GET('/v1/workspaces/{ws}/ventures/templates', { params: { path: { ws } } }))
}

/** The figures of a draft the person is typing, without saving anything. */
export function simulateVenture(ws: string, body: SimulationRequest): Promise<VentureFigures> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/ventures/simulation', { params: { path: { ws } }, body }),
  )
}

/** Starts a project, empty or from a template. */
export function createVenture(ws: string, body: CreateVentureRequest): Promise<VentureView> {
  return unwrap(client.POST('/v1/workspaces/{ws}/ventures', { params: { path: { ws } }, body }))
}

/** Saves a project, with the `version` it was opened at. */
export function updateVenture(
  ws: string,
  id: string,
  body: UpdateVentureRequest,
): Promise<VentureView> {
  return unwrap(
    client.PUT('/v1/workspaces/{ws}/ventures/{id}', { params: { path: { ws, id } }, body }),
  )
}

/** Moves a project to another status. */
export function changeVentureStatus(
  ws: string,
  id: string,
  body: ChangeStatusRequest,
): Promise<VentureView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/ventures/{id}/status', {
      params: { path: { ws, id } },
      body,
    }),
  )
}

/** Deletes a project. */
export async function deleteVenture(ws: string, id: string): Promise<void> {
  await unwrap(client.DELETE('/v1/workspaces/{ws}/ventures/{id}', { params: { path: { ws, id } } }))
}

/** Adds a step at the end of the project's list. */
export function addStep(ws: string, id: string, body: CreateStepRequest): Promise<StepView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/ventures/{id}/steps', { params: { path: { ws, id } }, body }),
  )
}

/** Changes a step: send only the field that changed. */
export function updateStep(
  ws: string,
  id: string,
  stepId: string,
  body: UpdateStepRequest,
): Promise<StepView> {
  return unwrap(
    client.PATCH('/v1/workspaces/{ws}/ventures/{id}/steps/{stepId}', {
      params: { path: { ws, id, stepId } },
      body,
    }),
  )
}

/** Removes a step. */
export async function deleteStep(ws: string, id: string, stepId: string): Promise<void> {
  await unwrap(
    client.DELETE('/v1/workspaces/{ws}/ventures/{id}/steps/{stepId}', {
      params: { path: { ws, id, stepId } },
    }),
  )
}

/** The family's projects, kept on the device; waits for a family id. */
export function useVentures(ws: string) {
  return useQuery({
    queryKey: venturesKeys.list(ws),
    queryFn: () => getVentures(ws),
    enabled: ws !== '',
    meta: { persist: true },
  })
}

/** One project; waits for a family id and a project. */
export function useVenture(ws: string, id: string) {
  return useQuery({
    queryKey: venturesKeys.detail(ws, id),
    queryFn: () => getVenture(ws, id),
    enabled: ws !== '' && id !== '',
    meta: { persist: false },
  })
}

/** The templates; they change only with a release, so they stay fresh for the session. */
export function useVentureTemplates(ws: string) {
  return useQuery({
    queryKey: venturesKeys.templates(ws),
    queryFn: () => getVentureTemplates(ws),
    enabled: ws !== '',
    staleTime: Infinity,
    meta: { persist: false },
  })
}

/** Any change to a project may change the list's figures, so the family's projects refresh. */
function useRefreshVentures(ws: string) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: venturesKeys.all(ws) })
}

/** Starts a project. */
export function useCreateVenture(ws: string) {
  return useMutation({
    mutationFn: (body: CreateVentureRequest) => createVenture(ws, body),
    onSettled: useRefreshVentures(ws),
  })
}

/** Saves an edit of a project. */
export function useUpdateVenture(ws: string, id: string) {
  return useMutation({
    mutationFn: (body: UpdateVentureRequest) => updateVenture(ws, id, body),
    onSettled: useRefreshVentures(ws),
  })
}

/** Changes a project's status. */
export function useChangeVentureStatus(ws: string, id: string) {
  return useMutation({
    mutationFn: (body: ChangeStatusRequest) => changeVentureStatus(ws, id, body),
    onSettled: useRefreshVentures(ws),
  })
}

/**
 * Deletes a project. The project's own query goes away with it, not refetched: asking for a deleted
 * project would only answer 404. Only the list refreshes.
 */
export function useDeleteVenture(ws: string, id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => deleteVenture(ws, id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: venturesKeys.detail(ws, id) })
      void queryClient.invalidateQueries({ queryKey: venturesKeys.list(ws) })
    },
  })
}

/** The project's step mutations: add, change one field of a step, and remove. */
export function useStepMutations(ws: string, id: string) {
  const refresh = useRefreshVentures(ws)
  const add = useMutation({
    mutationFn: (body: CreateStepRequest) => addStep(ws, id, body),
    onSettled: refresh,
  })
  const update = useMutation({
    mutationFn: ({ stepId, body }: { stepId: string; body: UpdateStepRequest }) =>
      updateStep(ws, id, stepId, body),
    onSettled: refresh,
  })
  const remove = useMutation({
    mutationFn: (stepId: string) => deleteStep(ws, id, stepId),
    onSettled: refresh,
  })
  return { add, update, remove }
}
