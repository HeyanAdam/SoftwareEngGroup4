import { createIdempotencyKey, get, post, put } from '../../shared/api/request'
import type {
  StudyGoalPayload,
  StudyGoalRecord,
  UserProfilePayload,
  UserProfileRecord,
} from './types'

const USER = '/user'

export function getProfile(): Promise<UserProfileRecord> {
  return get<UserProfileRecord>(`${USER}/profile`)
}

export function saveProfile(payload: UserProfilePayload): Promise<UserProfileRecord> {
  return put<UserProfileRecord>(`${USER}/profile`, payload)
}

export function listActiveGoals(): Promise<StudyGoalRecord[]> {
  return get<StudyGoalRecord[]>(`${USER}/study-goals`, { params: { status: 1 } })
}

export function createGoal(payload: StudyGoalPayload): Promise<StudyGoalRecord> {
  return post<StudyGoalRecord>(`${USER}/study-goals`, payload, {
    headers: { 'Idempotency-Key': createIdempotencyKey() },
  })
}

export function updateGoal(id: number, payload: StudyGoalPayload): Promise<StudyGoalRecord> {
  return put<StudyGoalRecord>(`${USER}/study-goals/${id}`, payload)
}
