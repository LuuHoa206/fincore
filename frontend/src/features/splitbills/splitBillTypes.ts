export type SplitBillStatus = 'OPEN' | 'PARTIALLY_SETTLED' | 'SETTLED' | 'CANCELLED'
export type SplitBillParticipantStatus = 'PENDING' | 'PARTIALLY_PAID' | 'PAID'

export type SplitBillParticipant = {
  id: string
  name: string
  contact: string | null
  owedAmount: number
  settledAmount: number
  outstandingAmount: number
  status: SplitBillParticipantStatus
}

export type SplitBillPayment = {
  id: string
  participantId: string
  transactionId: string
  amount: number
  occurredAt: string
}

export type SplitBill = {
  id: string
  expenseTransactionId: string
  name: string
  totalAmount: number
  payerShareAmount: number
  reimbursableAmount: number
  settledAmount: number
  outstandingAmount: number
  currency: string
  status: SplitBillStatus
  description: string
  notes: string | null
  occurredAt: string
  createdAt: string
  updatedAt: string
  participants: SplitBillParticipant[]
  payments: SplitBillPayment[]
}

export type CreateSplitBillInput = {
  walletId: string
  expenseCategoryId: string
  name: string
  totalAmount: number
  payerShareAmount: number
  description: string
  notes?: string
  occurredAt: string
  participants: Array<{ name: string; contact?: string; owedAmount: number }>
}

export type RecordSplitBillPaymentInput = {
  walletId: string
  incomeCategoryId: string
  amount: number
  notes?: string
  occurredAt: string
}
