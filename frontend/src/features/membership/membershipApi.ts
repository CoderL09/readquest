import axios from 'axios'
import { api } from '../../lib/api'

export type MembershipEntitlement = {
  code: string
  label: string
  startsAt: string
  endsAt?: string
}

type MembershipStatusResponse = {
  entitlements: MembershipEntitlement[]
}

type RedeemMembershipResponse = {
  success: true
  entitlement: MembershipEntitlement
}

type ErrorBody = {
  error?: { message?: string }
}

export async function getMembershipStatus(signal?: AbortSignal): Promise<MembershipEntitlement[]> {
  return (await api.get<MembershipStatusResponse>('/membership/status', { signal })).data
    .entitlements
}

export async function redeemMembership(code: string): Promise<MembershipEntitlement> {
  return (await api.post<RedeemMembershipResponse>('/membership/redeem', { code })).data.entitlement
}

export function membershipErrorMessage(error: unknown, fallback: string): string {
  if (!axios.isAxiosError<ErrorBody>(error)) return fallback
  const message = error.response?.data?.error?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}
