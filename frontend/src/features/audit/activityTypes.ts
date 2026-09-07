export type ActivityDetails = Record<string, unknown>

export interface ActivityLog {
  id: string
  action: string
  entityType: string
  entityId: string | null
  details: ActivityDetails
  createdAt: string
}
