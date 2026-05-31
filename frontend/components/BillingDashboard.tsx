'use client'

import { useState } from 'react'
import { CustomerSwitcher } from './CustomerSwitcher'
import { UsageForm, type BillingResult, type BillingError } from './UsageForm'

export function BillingDashboard() {
  const [customerId, setCustomerId] = useState('')
  const [result, setResult] = useState<BillingResult | null>(null)
  const [idempotentReplayed, setIdempotentReplayed] = useState(false)
  const [error, setError] = useState<{ body: BillingError; status: number } | null>(null)

  function handleResult(r: BillingResult, replayed: boolean) {
    setError(null)
    setResult(r)
    setIdempotentReplayed(replayed)
  }

  function handleError(e: BillingError, status: number) {
    setResult(null)
    setError({ body: e, status })
  }

  return (
    <>
      <CustomerSwitcher onCustomerChange={setCustomerId} />

      <div
        style={{
          display: 'grid',
          gap: '1.5rem',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          alignItems: 'start',
        }}
      >
        {/* Usage submission form */}
        <section className="card card-gradient">
          <p className="label" style={{ color: 'var(--color-green)', marginBottom: '1rem' }}>
            Submit Usage
          </p>
          {customerId ? (
            <UsageForm
              customerId={customerId}
              onResult={handleResult}
              onError={handleError}
            />
          ) : (
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
              Select a customer above to submit token usage.
            </p>
          )}
        </section>

        {/* Billing result / error card */}
        <section>
          {result && (
            <ResultCard result={result} idempotentReplayed={idempotentReplayed} />
          )}
          {error && <ErrorCard error={error.body} status={error.status} />}
          {!result && !error && (
            <div className="card" style={{ opacity: 0.4 }}>
              <p className="label" style={{ color: 'var(--color-orange)', marginBottom: '0.5rem' }}>
                Billing Result
              </p>
              <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
                Submit usage to see the calculated bill here.
              </p>
            </div>
          )}
        </section>
      </div>
    </>
  )
}

function ResultCard({
  result,
  idempotentReplayed,
}: {
  result: BillingResult
  idempotentReplayed: boolean
}) {
  const charge = parseFloat(result.totalCharge)
  const formattedCharge = `$${charge.toFixed(2)}`

  return (
    <div className="card card-gradient animate-fade-in">
      {idempotentReplayed && (
        <div style={{ marginBottom: '0.875rem' }}>
          <span className="badge badge-purple">Idempotent Replayed</span>
        </div>
      )}
      <p className="label" style={{ color: 'var(--color-orange)', marginBottom: '0.875rem' }}>
        Billing Result
      </p>

      {/* Total charge — prominent */}
      <div
        style={{
          fontSize: '2.25rem',
          fontWeight: 800,
          background: 'var(--gradient-brand)',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent',
          backgroundClip: 'text',
          marginBottom: '1rem',
          lineHeight: 1,
        }}
      >
        {formattedCharge}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem 1.25rem' }}>
        <Stat label="Total Tokens" value={result.totalTokens.toLocaleString()} />
        <Stat label="From Quota" value={result.tokensFromQuota.toLocaleString()} />
        <Stat
          label="Overage Tokens"
          value={result.overageTokens.toLocaleString()}
          highlight={result.overageTokens > 0}
        />
        <Stat label="Currency" value={result.currency} />
      </div>

      <div className="divider" />

      <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
        <Stat label="Bill ID" value={result.billId.slice(0, 8) + '…'} mono />
        <Stat label="Customer" value={result.customerId} />
      </div>

      <p
        style={{
          fontSize: '0.7rem',
          color: 'var(--color-text-muted)',
          marginTop: '0.75rem',
        }}
      >
        Calculated at {new Date(result.calculatedAt).toLocaleString()}
      </p>
    </div>
  )
}

function Stat({
  label,
  value,
  highlight,
  mono,
}: {
  label: string
  value: string
  highlight?: boolean
  mono?: boolean
}) {
  return (
    <div>
      <p className="label" style={{ marginBottom: '0.125rem' }}>
        {label}
      </p>
      <p
        style={{
          fontWeight: 600,
          fontSize: '0.9375rem',
          color: highlight ? 'var(--color-orange-light)' : 'var(--color-text-primary)',
          fontFamily: mono ? 'monospace' : undefined,
        }}
      >
        {value}
      </p>
    </div>
  )
}

function ErrorCard({ error, status }: { error: BillingError; status: number }) {
  return (
    <div
      data-testid="error-card"
      className="animate-fade-in"
      style={{
        background: 'var(--color-error-dim)',
        border: '1px solid rgba(239, 68, 68, 0.3)',
        borderRadius: 'var(--radius-lg)',
        padding: '1.5rem',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.875rem' }}>
        <span style={{ fontSize: '1.25rem' }}>⛔</span>
        <p className="label" style={{ color: 'var(--color-error)', margin: 0 }}>
          Error {status}
        </p>
      </div>
      {error.title && (
        <p style={{ fontWeight: 700, color: '#f87171', marginBottom: '0.375rem' }}>
          {error.title}
        </p>
      )}
      {error.detail && (
        <p style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>
          {error.detail}
        </p>
      )}
      {error.type && (
        <p style={{ fontSize: '0.7rem', color: 'var(--color-text-muted)', marginTop: '0.5rem' }}>
          {error.type}
        </p>
      )}
    </div>
  )
}
