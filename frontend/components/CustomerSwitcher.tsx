'use client'

import { useState } from 'react'
import type { CustomerInfo } from '@/lib/customers'
import { CUSTOMER_OPTIONS } from '@/lib/customers'

const STATUS_CONFIG: Record<
  CustomerInfo['status'],
  { badgeCls: string; label: string; message: string }
> = {
  active: { badgeCls: 'badge badge-green', label: 'Active', message: '' },
  expired: {
    badgeCls: 'badge badge-orange',
    label: 'Expired',
    message: "This customer's subscription has expired.",
  },
  'no-subscription': {
    badgeCls: 'badge badge-orange',
    label: 'No Plan',
    message: 'This customer has no active subscription.',
  },
  conflict: {
    badgeCls: 'badge badge-error',
    label: 'Conflict',
    message: 'This customer has multiple active subscriptions.',
  },
}

function fmt(n: number): string {
  return n.toLocaleString('en-US')
}

function CustomerInfoCard({ info }: { info: CustomerInfo }) {
  const { badgeCls, label, message } = STATUS_CONFIG[info.status]
  const usagePct = info.monthlyQuota
    ? (info.usedTokens / info.monthlyQuota) * 100
    : 0
  const isWarning = usagePct >= 75

  return (
    <div className="card card-gradient animate-fade-in">
      {/* Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: '1rem',
        }}
      >
        <div>
          <h2
            style={{
              fontSize: '1.125rem',
              fontWeight: 700,
              color: 'var(--color-text-primary)',
              marginBottom: '0.2rem',
            }}
          >
            {info.name}
          </h2>
          <p style={{ fontSize: '0.8125rem', color: 'var(--color-text-secondary)' }}>
            {info.id}
          </p>
        </div>
        <span className={badgeCls}>{label}</span>
      </div>

      {/* Plan details */}
      {info.planName && (
        <div
          style={{
            display: 'flex',
            gap: '1.5rem',
            marginBottom: '1rem',
            flexWrap: 'wrap',
          }}
        >
          <div>
            <p className="label" style={{ marginBottom: '0.125rem' }}>
              Plan
            </p>
            <p
              style={{
                color: 'var(--color-purple-light)',
                fontWeight: 600,
                fontSize: '0.9375rem',
              }}
            >
              {info.planName}
            </p>
          </div>
          {info.monthlyQuota !== null && (
            <div>
              <p className="label" style={{ marginBottom: '0.125rem' }}>
                Monthly Quota
              </p>
              <p
                style={{
                  color: 'var(--color-text-primary)',
                  fontWeight: 600,
                  fontSize: '0.9375rem',
                }}
              >
                {fmt(info.monthlyQuota)}
              </p>
            </div>
          )}
          {info.overageRatePer1k !== null && (
            <div>
              <p className="label" style={{ marginBottom: '0.125rem' }}>
                Overage Rate
              </p>
              <p
                style={{
                  color: 'var(--color-text-primary)',
                  fontWeight: 600,
                  fontSize: '0.9375rem',
                }}
              >
                ${info.overageRatePer1k.toFixed(4)}/1k tokens
              </p>
            </div>
          )}
        </div>
      )}

      {/* Progress bar */}
      {info.monthlyQuota !== null && info.monthlyQuota > 0 && (
        <div>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              marginBottom: '0.5rem',
              fontSize: '0.8125rem',
            }}
          >
            <span style={{ color: 'var(--color-text-secondary)' }}>This month&apos;s usage</span>
            <span
              style={{
                fontWeight: 600,
                color: isWarning
                  ? 'var(--color-orange-light)'
                  : 'var(--color-text-primary)',
              }}
            >
              {fmt(info.usedTokens)}&nbsp;/&nbsp;{fmt(info.monthlyQuota)}
              <span
                style={{ marginLeft: '0.5rem', color: 'var(--color-text-muted)', fontWeight: 400 }}
              >
                ({usagePct.toFixed(1)}%)
              </span>
            </span>
          </div>
          <div className="progress-track">
            <div
              className={`progress-fill${isWarning ? ' warning' : ''}`}
              style={{ width: `${Math.min(usagePct, 100)}%` }}
            />
          </div>
          {isWarning && (
            <p
              style={{
                fontSize: '0.75rem',
                color: 'var(--color-orange-light)',
                marginTop: '0.375rem',
              }}
            >
              ⚠ Quota running low —{' '}
              {fmt(info.monthlyQuota - info.usedTokens)} tokens remaining
            </p>
          )}
        </div>
      )}

      {/* Status message for non-active states */}
      {message && (
        <p
          style={{
            fontSize: '0.8125rem',
            color: 'var(--color-text-secondary)',
            marginTop: '0.875rem',
            paddingTop: '0.875rem',
            borderTop: '1px solid var(--color-border-subtle)',
          }}
        >
          {message}
        </p>
      )}
    </div>
  )
}

export function CustomerSwitcher({
  onCustomerChange,
}: {
  onCustomerChange?: (id: string) => void
}) {
  const [selectedId, setSelectedId] = useState('')
  const [info, setInfo] = useState<CustomerInfo | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleChange(id: string) {
    setSelectedId(id)
    onCustomerChange?.(id)
    if (!id) {
      setInfo(null)
      return
    }
    setLoading(true)
    setInfo(null)
    try {
      const res = await fetch(`/api/customers/${id}`)
      const data: CustomerInfo = await res.json()
      setInfo(data)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ marginBottom: '2rem' }}>
      <label className="label" htmlFor="customer-select">
        Customer
      </label>
      <select
        id="customer-select"
        className="input"
        value={selectedId}
        onChange={(e) => handleChange(e.target.value)}
        style={{ cursor: 'pointer', marginBottom: '1rem' }}
      >
        <option value="">— Select a customer —</option>
        {CUSTOMER_OPTIONS.map(({ id, name, planTag }) => (
          <option key={id} value={id}>
            {id} — {name} ({planTag})
          </option>
        ))}
      </select>

      {loading && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            color: 'var(--color-text-secondary)',
          }}
        >
          <div className="spinner" />
          <span style={{ fontSize: '0.875rem' }}>Loading customer data…</span>
        </div>
      )}

      {!loading && info && <CustomerInfoCard info={info} />}
    </div>
  )
}
