export type CustomerStatus = 'active' | 'expired' | 'no-subscription' | 'conflict'

export interface CustomerInfo {
  id: string
  name: string
  planName: string | null
  monthlyQuota: number | null
  usedTokens: number
  overageRatePer1k: number | null
  status: CustomerStatus
}

export interface CustomerOption {
  id: string
  name: string
  planTag: string
}

export const CUSTOMER_OPTIONS: CustomerOption[] = [
  { id: 'CUST-001', name: 'Acme Corp', planTag: 'Starter' },
  { id: 'CUST-002', name: 'TechStart Inc', planTag: 'Free' },
  { id: 'CUST-003', name: 'Enterprise Solutions Ltd', planTag: 'Enterprise' },
  { id: 'CUST-004', name: 'No Active Sub Corp', planTag: 'Expired' },
  { id: 'CUST-005', name: 'Multi Sub Corp', planTag: 'Conflict' },
  { id: 'CUST-006', name: 'Never Subscribed Corp', planTag: 'No Plan' },
]

const MOCK_CUSTOMERS: Record<string, CustomerInfo> = {
  'CUST-001': {
    id: 'CUST-001',
    name: 'Acme Corp',
    planName: 'Starter',
    monthlyQuota: 100000,
    usedTokens: 80000,
    overageRatePer1k: 0.02,
    status: 'active',
  },
  'CUST-002': {
    id: 'CUST-002',
    name: 'TechStart Inc',
    planName: 'Free Tier',
    monthlyQuota: 10000,
    usedTokens: 3500,
    overageRatePer1k: 0.03,
    status: 'active',
  },
  'CUST-003': {
    id: 'CUST-003',
    name: 'Enterprise Solutions Ltd',
    planName: 'Enterprise',
    monthlyQuota: 2000000,
    usedTokens: 450000,
    overageRatePer1k: 0.01,
    status: 'active',
  },
  'CUST-004': {
    id: 'CUST-004',
    name: 'No Active Sub Corp',
    planName: 'Starter',
    monthlyQuota: null,
    usedTokens: 0,
    overageRatePer1k: null,
    status: 'expired',
  },
  'CUST-005': {
    id: 'CUST-005',
    name: 'Multi Sub Corp',
    planName: null,
    monthlyQuota: null,
    usedTokens: 0,
    overageRatePer1k: null,
    status: 'conflict',
  },
  'CUST-006': {
    id: 'CUST-006',
    name: 'Never Subscribed Corp',
    planName: null,
    monthlyQuota: null,
    usedTokens: 0,
    overageRatePer1k: null,
    status: 'no-subscription',
  },
}

export function getCustomerInfo(id: string): CustomerInfo | null {
  return MOCK_CUSTOMERS[id] ?? null
}
