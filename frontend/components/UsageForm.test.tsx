import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { UsageForm } from './UsageForm'
import type { BillingResult, BillingError } from './UsageForm'
import { MOCK_BILL } from '../mocks/handlers'

function renderForm(customerId = 'CUST-001') {
  const onResult = vi.fn()
  const onError = vi.fn()
  render(<UsageForm customerId={customerId} onResult={onResult} onError={onError} />)
  return { onResult, onError }
}

describe('UsageForm — field rendering', () => {
  it('renders all three fields and a submit button', () => {
    renderForm()
    expect(screen.getByLabelText(/prompt tokens/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/completion tokens/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/idempotency-key/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /calculate bill/i })).toBeInTheDocument()
  })

  it('disables submit when fields are empty', () => {
    renderForm()
    expect(screen.getByRole('button', { name: /calculate bill/i })).toBeDisabled()
  })
})

describe('UsageForm — token field validation', () => {
  it('shows error when prompt tokens is empty on blur', async () => {
    const user = userEvent.setup()
    renderForm()
    const input = screen.getByLabelText(/prompt tokens/i)
    await user.type(input, '5')
    await user.clear(input)
    expect(screen.getByText('Required')).toBeInTheDocument()
  })

  it('shows error for negative prompt token value', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText(/prompt tokens/i), '-1')
    expect(screen.getByLabelText(/prompt tokens/i)).toHaveClass('error')
  })

  it('shows error for decimal token value', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText(/prompt tokens/i), '1.5')
    expect(screen.getByLabelText(/prompt tokens/i)).toHaveClass('error')
  })

  it('enables submit when both token fields are valid', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText(/prompt tokens/i), '100')
    await user.type(screen.getByLabelText(/completion tokens/i), '50')
    expect(screen.getByRole('button', { name: /calculate bill/i })).not.toBeDisabled()
  })
})

describe('UsageForm — Idempotency-Key field validation', () => {
  it('accepts an empty key (field is optional)', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText(/prompt tokens/i), '100')
    await user.type(screen.getByLabelText(/completion tokens/i), '50')
    // Leave idempotency key blank — submit should stay enabled
    expect(screen.getByRole('button', { name: /calculate bill/i })).not.toBeDisabled()
  })

  it('accepts an 8-character alphanumeric key', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText(/idempotency-key/i), 'AbCd1234')
    expect(screen.getByLabelText(/idempotency-key/i)).not.toHaveClass('error')
  })

  it('accepts a 255-character key (upper boundary)', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText(/idempotency-key/i), 'a'.repeat(255))
    expect(screen.getByLabelText(/idempotency-key/i)).not.toHaveClass('error')
  })

  it('shows error for a 7-character key (too short)', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText(/idempotency-key/i), 'abc1234')
    expect(screen.getByLabelText(/idempotency-key/i)).toHaveClass('error')
  })

  it('shows error for a 256-character key (too long)', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText(/idempotency-key/i), 'a'.repeat(256))
    expect(screen.getByLabelText(/idempotency-key/i)).toHaveClass('error')
  })

  it('shows error for a key with special chars like @', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText(/idempotency-key/i), 'req@key12345')
    expect(screen.getByLabelText(/idempotency-key/i)).toHaveClass('error')
  })

  it('accepts key with hyphens and underscores', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText(/idempotency-key/i), 'req-abc_12345678')
    expect(screen.getByLabelText(/idempotency-key/i)).not.toHaveClass('error')
  })
})

describe('UsageForm — form submission', () => {
  it('calls onResult with billing data on successful submission', async () => {
    const user = userEvent.setup()
    const { onResult } = renderForm()

    await user.type(screen.getByLabelText(/prompt tokens/i), '100')
    await user.type(screen.getByLabelText(/completion tokens/i), '50')
    await user.click(screen.getByRole('button', { name: /calculate bill/i }))

    await waitFor(() => {
      expect(onResult).toHaveBeenCalledWith(
        expect.objectContaining({ billId: MOCK_BILL.billId }),
        false,
      )
    })
  })

  it('calls onError when backend returns RFC 7807 404 error', async () => {
    server.use(
      http.post('/api/billing-proxy', () => {
        return HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Customer not found',
            status: 404,
            detail: 'No customer with id CUST-MISSING',
          },
          { status: 404 },
        )
      }),
    )

    const user = userEvent.setup()
    const { onError } = renderForm('CUST-MISSING')

    await user.type(screen.getByLabelText(/prompt tokens/i), '100')
    await user.type(screen.getByLabelText(/completion tokens/i), '50')
    await user.click(screen.getByRole('button', { name: /calculate bill/i }))

    await waitFor(() => {
      expect(onError).toHaveBeenCalledWith(
        expect.objectContaining({ title: 'Customer not found', status: 404 }),
        404,
      )
    })
  })

  it('calls onResult with idempotentReplayed=true when header is set', async () => {
    server.use(
      http.post('/api/billing-proxy', () => {
        return HttpResponse.json(MOCK_BILL, {
          status: 200,
          headers: { 'Idempotent-Replayed': 'true' },
        })
      }),
    )

    const user = userEvent.setup()
    const { onResult } = renderForm()

    await user.type(screen.getByLabelText(/prompt tokens/i), '100')
    await user.type(screen.getByLabelText(/completion tokens/i), '50')
    await user.click(screen.getByRole('button', { name: /calculate bill/i }))

    await waitFor(() => {
      expect(onResult).toHaveBeenCalledWith(expect.anything(), true)
    })
  })
})
