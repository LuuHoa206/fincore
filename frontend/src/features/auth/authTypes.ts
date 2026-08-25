export type UserProfile = {
  id: string
  email: string
  displayName: string
  preferredCurrency: string
  timeZone: string
  roles: string[]
  createdAt: string
}

export type AuthResponse = {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: UserProfile
}

export type LoginInput = {
  email: string
  password: string
}

export type RegisterInput = LoginInput & {
  displayName: string
  preferredCurrency: string
  timeZone: string
}
