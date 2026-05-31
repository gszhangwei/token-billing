import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, delay } from 'msw'
import { server } from '../mocks/server'
import { MOCK_CUSTOMER } from '../mocks/handlers'
import { CustomerSwitcher } from './CustomerSwitcher'

describe('CustomerSwitcher', () => {
  it('renders customer select dropdown', () => {
    render(<CustomerSwitcher />)
    expect(screen.getByRole('combobox')).toBeInTheDocument()
    expect(screen.getByText('— Select a customer —')).toBeInTheDocument()
  })

  it('lists all customer options', () => {
    render(<CustomerSwitcher />)
    const select = screen.getByRole('combobox')
    // 6 customers + 1 placeholder option
    expect(select.querySelectorAll('option')).toHaveLength(7)
  })

  it('calls onCustomerChange with selected id', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<CustomerSwitcher onCustomerChange={onChange} />)

    await user.selectOptions(screen.getByRole('combobox'), 'CUST-001')

    expect(onChange).toHaveBeenCalledWith('CUST-001')
  })

  it('shows loading state while fetching customer info', async () => {
    // Delay the response so the loading state is visible long enough to assert
    server.use(
      http.get('/api/customers/:id', async () => {
        await delay(100)
        return HttpResponse.json(MOCK_CUSTOMER)
      }),
    )

    const user = userEvent.setup()
    render(<CustomerSwitcher />)

    // Don't await — fire the event and immediately check the loading state
    const selectPromise = user.selectOptions(screen.getByRole('combobox'), 'CUST-001')
    await waitFor(() => {
      expect(screen.getByText('Loading customer data…')).toBeInTheDocument()
    })
    await selectPromise
  })

  it('renders customer info card after selecting a customer', async () => {
    const user = userEvent.setup()
    render(<CustomerSwitcher />)

    await user.selectOptions(screen.getByRole('combobox'), 'CUST-001')

    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    expect(screen.getByText('Starter')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it('clears customer info when placeholder is re-selected', async () => {
    const user = userEvent.setup()
    render(<CustomerSwitcher />)

    await user.selectOptions(screen.getByRole('combobox'), 'CUST-001')
    await waitFor(() => screen.getByText('Acme Corp'))

    await user.selectOptions(screen.getByRole('combobox'), '')
    expect(screen.queryByText('Acme Corp')).not.toBeInTheDocument()
  })
})
