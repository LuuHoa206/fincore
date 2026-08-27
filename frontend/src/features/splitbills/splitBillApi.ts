import { httpClient } from '../../shared/api/httpClient'
import type { CreateSplitBillInput, RecordSplitBillPaymentInput, SplitBill } from './splitBillTypes'

export const splitBillApi = {
  list: async () => (await httpClient.get<SplitBill[]>('/split-bills')).data,
  create: async (input: CreateSplitBillInput, idempotencyKey: string) => (
    await httpClient.post<SplitBill>('/split-bills', input, { headers: { 'Idempotency-Key': idempotencyKey } })
  ).data,
  recordPayment: async (billId: string, participantId: string, input: RecordSplitBillPaymentInput, idempotencyKey: string) => (
    await httpClient.post<SplitBill>(`/split-bills/${billId}/participants/${participantId}/payments`, input, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
  ).data,
}
