import { httpClient } from '../../shared/api/httpClient'
import type { StatementImportInput, StatementImportPreview, StatementImportResult } from './statementImportTypes'

export const statementImportApi = {
  preview: async (input: StatementImportInput) => {
    const { data } = await httpClient.post<StatementImportPreview>('/statement-imports/preview', input)
    return data
  },
  confirm: async (input: StatementImportInput) => {
    const { data } = await httpClient.post<StatementImportResult>('/statement-imports/confirm', input)
    return data
  },
}
