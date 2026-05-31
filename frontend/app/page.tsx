export const runtime = 'edge'

import { BillingDashboard } from '@/components/BillingDashboard'

export default function Home() {
  return (
    <div className="animate-fade-in">
      <header style={{ marginBottom: '2.5rem' }}>
        <h1
          style={{
            fontSize: 'clamp(1.75rem, 4vw, 2.5rem)',
            fontWeight: 800,
            lineHeight: 1.2,
            background: 'var(--gradient-brand)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
            backgroundClip: 'text',
            marginBottom: '0.5rem',
          }}
        >
          Token Billing Dashboard
        </h1>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.9375rem' }}>
          LLM API token usage billing and quota management
        </p>
      </header>

      <BillingDashboard />
    </div>
  )
}
