'use client'

import { useState, type FormEvent, type ChangeEvent } from 'react'
import { validateIdempotencyKey, validateTokens } from '@/lib/validation'

export interface BillingResult {
  billId: string
  customerId: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  tokensFromQuota: number
  overageTokens: number
  totalCharge: string
  currency: string
  calculatedAt: string
}

export interface BillingError {
  type?: string
  title?: string
  status?: number
  detail?: string
}

interface UsageFormProps {
  customerId: string
  onResult: (result: BillingResult, idempotentReplayed: boolean) => void
  onError: (error: BillingError, status: number) => void
}

export function UsageForm({ customerId, onResult, onError }: UsageFormProps) {
  const [promptTokens, setPromptTokens] = useState('')
  const [completionTokens, setCompletionTokens] = useState('')
  const [idempotencyKey, setIdempotencyKey] = useState('')

  const [promptError, setPromptError] = useState('')
  const [completionError, setCompletionError] = useState('')
  const [idempotencyError, setIdempotencyError] = useState('')

  const [submitting, setSubmitting] = useState(false)

  function handlePromptChange(e: ChangeEvent<HTMLInputElement>) {
    setPromptTokens(e.target.value)
    setPromptError(validateTokens(e.target.value))
  }

  function handleCompletionChange(e: ChangeEvent<HTMLInputElement>) {
    setCompletionTokens(e.target.value)
    setCompletionError(validateTokens(e.target.value))
  }

  function handleIdempotencyChange(e: ChangeEvent<HTMLInputElement>) {
    setIdempotencyKey(e.target.value)
    setIdempotencyError(validateIdempotencyKey(e.target.value))
  }

  function isFormValid(): boolean {
    return (
      !validateTokens(promptTokens) &&
      !validateTokens(completionTokens) &&
      !validateIdempotencyKey(idempotencyKey) &&
      promptTokens !== '' &&
      completionTokens !== ''
    )
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()

    // Run full validation on submit
    const pe = validateTokens(promptTokens)
    const ce = validateTokens(completionTokens)
    const ie = validateIdempotencyKey(idempotencyKey)
    setPromptError(pe)
    setCompletionError(ce)
    setIdempotencyError(ie)
    if (pe || ce || ie) return

    setSubmitting(true)
    try {
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      }
      if (idempotencyKey) {
        headers['Idempotency-Key'] = idempotencyKey
      }

      const res = await fetch('/api/billing-proxy', {
        method: 'POST',
        headers,
        body: JSON.stringify({
          customerId,
          promptTokens: Number(promptTokens),
          completionTokens: Number(completionTokens),
        }),
      })

      const idempotentReplayed = res.headers.get('Idempotent-Replayed') === 'true'
      const body = await res.json()

      if (res.ok) {
        onResult(body as BillingResult, idempotentReplayed)
      } else {
        onError(body as BillingError, res.status)
      }
    } catch (err) {
      onError({
        title: 'Submission Failed',
        detail: err instanceof Error ? err.message : 'An unexpected error occurred',
      }, 500)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div style={{ display: 'grid', gap: '1rem' }}>
        {/* Prompt Tokens */}
        <div>
          <label className="label" htmlFor="prompt-tokens">
            Prompt Tokens
          </label>
          <input
            id="prompt-tokens"
            type="number"
            min={0}
            step={1}
            className={`input${promptError ? ' error' : ''}`}
            value={promptTokens}
            onChange={handlePromptChange}
            placeholder="e.g. 1000"
            disabled={submitting}
          />
          {promptError && <p className="field-error">{promptError}</p>}
        </div>

        {/* Completion Tokens */}
        <div>
          <label className="label" htmlFor="completion-tokens">
            Completion Tokens
          </label>
          <input
            id="completion-tokens"
            type="number"
            min={0}
            step={1}
            className={`input${completionError ? ' error' : ''}`}
            value={completionTokens}
            onChange={handleCompletionChange}
            placeholder="e.g. 500"
            disabled={submitting}
          />
          {completionError && <p className="field-error">{completionError}</p>}
        </div>

        {/* Idempotency Key */}
        <div>
          <label className="label" htmlFor="idempotency-key">
            Idempotency-Key{' '}
            <span style={{ fontWeight: 400, textTransform: 'none', fontSize: '0.7rem', color: 'var(--color-text-muted)' }}>
              (optional)
            </span>
          </label>
          <input
            id="idempotency-key"
            type="text"
            className={`input${idempotencyError ? ' error' : ''}`}
            value={idempotencyKey}
            onChange={handleIdempotencyChange}
            placeholder="e.g. req-abc-12345678"
            disabled={submitting}
          />
          {idempotencyError && <p className="field-error">{idempotencyError}</p>}
        </div>

        {/* Submit */}
        <button
          type="submit"
          className="btn btn-primary"
          disabled={submitting || !isFormValid()}
          style={{ marginTop: '0.25rem' }}
        >
          {submitting ? (
            <>
              <div className="spinner" />
              Calculating…
            </>
          ) : (
            'Calculate Bill'
          )}
        </button>
      </div>
    </form>
  )
}
