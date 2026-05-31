import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'Token Billing Dashboard',
  description: 'LLM API Token Usage Billing & Quota Management',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body>
        <div className="container" style={{ paddingTop: '2rem', paddingBottom: '4rem' }}>
          {children}
        </div>
      </body>
    </html>
  )
}
