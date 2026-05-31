export const runtime = 'edge'

import { CustomerSwitcher } from '@/components/CustomerSwitcher'

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

      <CustomerSwitcher />

      <div
        style={{
          display: 'grid',
          gap: '1rem',
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        }}
      >
        <section className="card card-gradient">
          <p className="label" style={{ color: 'var(--color-green)', marginBottom: '0.5rem' }}>
            Submit Usage
          </p>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            Submit token usage for billing calculation.
          </p>
        </section>

        <section className="card card-gradient">
          <p className="label" style={{ color: 'var(--color-orange)', marginBottom: '0.5rem' }}>
            Billing Result
          </p>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            View calculated bill and overage charge details.
          </p>
        </section>
      </div>
    </div>
  )
}
