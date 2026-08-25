export const walletTypes = ['CASH', 'BANK', 'E_WALLET', 'CREDIT', 'SAVINGS'] as const

export type WalletType = typeof walletTypes[number]

export type Wallet = {
  id: string
  name: string
  walletType: WalletType
  currency: string
  currentBalance: number
  allowNegative: boolean
  createdAt: string
  updatedAt: string
}

export type CreateWalletInput = {
  name: string
  walletType: WalletType
  currency: string
  allowNegative: boolean
}

export type UpdateWalletInput = Pick<CreateWalletInput, 'name' | 'walletType' | 'allowNegative'>
