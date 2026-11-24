/**
 * What only the planning screens need: monthly limits, goals and their contributions, the emergency
 * reserve, the balance projection and the fixed/variable split. The `shared/api` barrel does not
 * re-export this file, so none of it weighs on the initial load. Every call needs the connection:
 * none goes through the offline queue (ADR-0007); the screens read the cache when there is none.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { emitLocalAction } from '@/shared/events'
import { client, unwrap } from './client'
import { queryKeys } from './queryKeys'
import type { components } from './schema'

type Schemas = components['schemas']

export type BudgetMonthView = Schemas['BudgetMonthView']
export type BudgetLineView = Schemas['BudgetLineView']
export type CopyBudgetsView = Schemas['CopyBudgetsView']
export type SetBudgetRequest = Schemas['SetBudgetRequest']
export type GoalsView = Schemas['GoalsView']
export type GoalView = Schemas['GoalView']
export type CreateGoalRequest = Schemas['CreateGoalRequest']
export type UpdateGoalRequest = Schemas['UpdateGoalRequest']
export type ContributionView = Schemas['ContributionView']
export type CreateContributionRequest = Schemas['CreateContributionRequest']
export type ReserveView = Schemas['ReserveView']
export type UpdateReserveRequest = Schemas['UpdateReserveRequest']
export type ProjectionView = Schemas['ProjectionView']
export type SpendingSplitView = Schemas['SpendingSplitView']

/**
 * Everything planning reads sits under the family key (`['ws', ws, 'planning']`), so a core edit
 * that refreshes the family reaches planning too, and one invalidation of `all` reaches only it.
 */
export const planningKeys = {
  all: (ws: string) => [...queryKeys.workspace(ws), 'planning'] as const,
  budgets: (ws: string, month: string) => [...planningKeys.all(ws), 'budgets', month] as const,
  goals: (ws: string) => [...planningKeys.all(ws), 'goals'] as const,
  contributions: (ws: string, goalId: string) =>
    [...planningKeys.goals(ws), goalId, 'contributions'] as const,
  reserve: (ws: string) => [...planningKeys.all(ws), 'reserve'] as const,
  projection: (ws: string) => [...planningKeys.all(ws), 'projection'] as const,
  spendingSplit: (ws: string, month: string) =>
    [...planningKeys.all(ws), 'spending-split', month] as const,
}

/** The month's limits with what each category spent. */
export function getBudgets(ws: string, month: string): Promise<BudgetMonthView> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/budgets', { params: { path: { ws }, query: { month } } }),
  )
}

/** Sets a category's limit for the month; `body.version` is the one it was opened at, if it had one. */
export function setBudget(
  ws: string,
  month: string,
  categoryId: string,
  body: SetBudgetRequest,
): Promise<BudgetLineView> {
  return unwrap(
    client.PUT('/v1/workspaces/{ws}/budgets/{month}/{categoryId}', {
      params: { path: { ws, month, categoryId } },
      body,
    }),
  )
}

/** Removes a category's limit for the month; removing a missing one changes nothing. */
export async function deleteBudget(ws: string, month: string, categoryId: string): Promise<void> {
  await unwrap(
    client.DELETE('/v1/workspaces/{ws}/budgets/{month}/{categoryId}', {
      params: { path: { ws, month, categoryId } },
    }),
  )
}

/** Copies the previous month's limits into `month`. */
export function copyPreviousBudgets(ws: string, month: string): Promise<CopyBudgetsView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/budgets/{month}/copy-previous', {
      params: { path: { ws, month } },
    }),
  )
}

/** The family's goals. */
export function getGoals(ws: string): Promise<GoalsView> {
  return unwrap(client.GET('/v1/workspaces/{ws}/goals', { params: { path: { ws } } }))
}

/** Creates a goal. */
export function createGoal(ws: string, body: CreateGoalRequest): Promise<GoalView> {
  return unwrap(client.POST('/v1/workspaces/{ws}/goals', { params: { path: { ws } }, body }))
}

/** Changes a goal, with the `version` it was opened at. */
export function updateGoal(ws: string, id: string, body: UpdateGoalRequest): Promise<GoalView> {
  return unwrap(
    client.PATCH('/v1/workspaces/{ws}/goals/{id}', { params: { path: { ws, id } }, body }),
  )
}

/** Archives a goal; archiving it again changes nothing. */
export async function archiveGoal(ws: string, id: string): Promise<void> {
  await unwrap(
    client.POST('/v1/workspaces/{ws}/goals/{id}/archive', { params: { path: { ws, id } } }),
  )
}

/** A goal's contributions, newest first. */
export function getContributions(ws: string, id: string): Promise<ContributionView[]> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/goals/{id}/contributions', { params: { path: { ws, id } } }),
  )
}

/** Adds a contribution to a goal. */
export function createContribution(
  ws: string,
  id: string,
  body: CreateContributionRequest,
): Promise<ContributionView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/goals/{id}/contributions', {
      params: { path: { ws, id } },
      body,
    }),
  )
}

/** The emergency reserve. */
export function getReserve(ws: string): Promise<ReserveView> {
  return unwrap(client.GET('/v1/workspaces/{ws}/reserve', { params: { path: { ws } } }))
}

/** Changes the reserve's target months or the monthly cost the family typed in. */
export function updateReserve(ws: string, body: UpdateReserveRequest): Promise<ReserveView> {
  return unwrap(client.PATCH('/v1/workspaces/{ws}/reserve', { params: { path: { ws } }, body }))
}

/** The balance projection for the next month ends. */
export function getProjection(ws: string): Promise<ProjectionView> {
  return unwrap(client.GET('/v1/workspaces/{ws}/projection', { params: { path: { ws } } }))
}

/** The month's confirmed spending split into fixed and variable. */
export function getSpendingSplit(ws: string, month: string): Promise<SpendingSplitView> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/spending-split', {
      params: { path: { ws }, query: { month } },
    }),
  )
}

/** The month's limits; waits for a family id and a month. */
export function useBudgets(ws: string, month: string) {
  return useQuery({
    queryKey: planningKeys.budgets(ws, month),
    queryFn: () => getBudgets(ws, month),
    enabled: ws !== '' && month !== '',
  })
}

/** The family's goals; waits for a family id. */
export function useGoals(ws: string) {
  return useQuery({
    queryKey: planningKeys.goals(ws),
    queryFn: () => getGoals(ws),
    enabled: ws !== '',
  })
}

/** A goal's contributions; waits for a family id and a goal. */
export function useContributions(ws: string, goalId: string) {
  return useQuery({
    queryKey: planningKeys.contributions(ws, goalId),
    queryFn: () => getContributions(ws, goalId),
    enabled: ws !== '' && goalId !== '',
  })
}

/** The emergency reserve; waits for a family id. */
export function useReserve(ws: string) {
  return useQuery({
    queryKey: planningKeys.reserve(ws),
    queryFn: () => getReserve(ws),
    enabled: ws !== '',
  })
}

/** The balance projection; waits for a family id. */
export function useProjection(ws: string) {
  return useQuery({
    queryKey: planningKeys.projection(ws),
    queryFn: () => getProjection(ws),
    enabled: ws !== '',
  })
}

/** The month's fixed/variable split; waits for a family id and a month. */
export function useSpendingSplit(ws: string, month: string) {
  return useQuery({
    queryKey: planningKeys.spendingSplit(ws, month),
    queryFn: () => getSpendingSplit(ws, month),
    enabled: ws !== '' && month !== '',
  })
}

/** A limit, a goal or the reserve may change what the others show, so the family's planning refreshes. */
function useRefreshPlanning(ws: string) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: planningKeys.all(ws) })
}

/** Sets a category's limit for a month. */
export function useSetBudget(ws: string) {
  return useMutation({
    mutationFn: ({
      month,
      categoryId,
      body,
    }: {
      month: string
      categoryId: string
      body: SetBudgetRequest
    }) => setBudget(ws, month, categoryId, body),
    onSettled: useRefreshPlanning(ws),
  })
}

/** Removes a category's limit for a month. */
export function useDeleteBudget(ws: string) {
  return useMutation({
    mutationFn: ({ month, categoryId }: { month: string; categoryId: string }) =>
      deleteBudget(ws, month, categoryId),
    onSettled: useRefreshPlanning(ws),
  })
}

/** Copies the previous month's limits into a month. */
export function useCopyBudgets(ws: string) {
  return useMutation({
    mutationFn: (month: string) => copyPreviousBudgets(ws, month),
    onSettled: useRefreshPlanning(ws),
  })
}

/** Creates a goal. */
export function useCreateGoal(ws: string) {
  return useMutation({
    mutationFn: (body: CreateGoalRequest) => createGoal(ws, body),
    onSettled: useRefreshPlanning(ws),
  })
}

/** Saves an edit of a goal. */
export function useUpdateGoal(ws: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateGoalRequest }) => updateGoal(ws, id, body),
    onSettled: useRefreshPlanning(ws),
  })
}

/** Archives a goal. */
export function useArchiveGoal(ws: string) {
  return useMutation({
    mutationFn: (id: string) => archiveGoal(ws, id),
    onSettled: useRefreshPlanning(ws),
  })
}

/** Adds a contribution to a goal and tells the device's listeners (D17). */
export function useAddContribution(ws: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: CreateContributionRequest }) =>
      createContribution(ws, id, body),
    onSuccess: () =>
      emitLocalAction({ type: 'contribution-saved', workspaceId: ws, fromQueue: false }),
    onSettled: useRefreshPlanning(ws),
  })
}

/** Changes the reserve's target or typed cost. */
export function useUpdateReserve(ws: string) {
  return useMutation({
    mutationFn: (body: UpdateReserveRequest) => updateReserve(ws, body),
    onSettled: useRefreshPlanning(ws),
  })
}
