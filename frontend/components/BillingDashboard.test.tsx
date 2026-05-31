import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { BillingDashboard } from './BillingDashboard'

async function selectCustomerAndSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(screen.getByRole('combobox'), 'CUST-001')
  await waitFor(() => screen.getByText('Acme Corp'))

  await user.type(screen.getByLabelText(/prompt tokens/i), '100')
  await user.type(screen.getByLabelText(/completion tokens/i), '50')
  await user.click(screen.getByRole('button', { name: /calculate bill/i }))
}

describe('BillingDashboard — RFC 7807 error rendering (GlobalExceptionHandler)', () => {
  it('renders red error card with title and detail when backend returns 404', async () => {
    server.use(
      http.post('/api/billing-proxy', () => {
        return HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Customer not found',
            status: 404,
            detail: 'No customer with id CUST-001',
          },
          { status: 404 },
        )
      }),
    )

    const user = userEvent.setup()
    render(<BillingDashboard />)

    await selectCustomerAndSubmit(user)

    await waitFor(() => {
      expect(screen.getByText('Error 404')).toBeInTheDocument()
    })
    expect(screen.getByText('Customer not found')).toBeInTheDocument()
    expect(screen.getByText('No customer with id CUST-001')).toBeInTheDocument()

    expect(screen.getByText('⛔')).toBeInTheDocument()
    expect(screen.getByTestId('error-card')).toHaveStyle({ border: '1px solid rgba(239, 68, 68, 0.3)' })
  })

  it('renders result card on successful submission', async () => {
    const user = userEvent.setup()
    render(<BillingDashboard />)

    await selectCustomerAndSubmit(user)

    await waitFor(() => {
      expect(screen.getByText('Billing Result')).toBeInTheDocument()
    })
    expect(screen.getByText('$0.00')).toBeInTheDocument()
  })

  it('shows placeholder before any submission', () => {
    render(<BillingDashboard />)
    expect(screen.getByText('Submit usage to see the calculated bill here.')).toBeInTheDocument()
  })
})
