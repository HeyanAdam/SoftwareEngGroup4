export interface UserProfileRecord {
  userId: number
  gender: number | null
  major: string | null
  grade: string | null
  baseLevel: number | null
  weeklyStudyMinutes: number
  weakPointsJson: unknown
  profileJson: unknown
}

export interface UserProfilePayload {
  gender: number | null
  major: string | null
  grade: string | null
  baseLevel: number | null
  weeklyStudyMinutes: number
  weakPoints: string[]
  profile: Record<string, unknown>
}

export interface StudyGoalRecord {
  id: number
  targetSchoolId: number | null
  targetSchoolName: string | null
  targetMajor: string | null
  targetScore: number | null
  targetSubjectScores: Record<string, number> | null
  examYear: number
  examDate: string | null
  planType: number
  status: number
  remark: string | null
  version: number
}

export interface StudyGoalPayload {
  targetSchoolId: number | null
  targetSchoolName: string | null
  targetMajor: string | null
  targetScore: number | null
  targetSubjectScores: Record<string, number>
  examYear: number
  examDate: string | null
  planType: number
  status: number
  remark: string | null
}
