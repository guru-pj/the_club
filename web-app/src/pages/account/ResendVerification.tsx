import { useState } from 'react'
import { accountApi } from '../../api/endpoints'
import { Button } from '../../components/ui'
import { useToast } from '../../components/Toast'

/** "Resend verification email" for an address; the server answers 202 whether or not the account exists. */
export function ResendVerification({ email, label = 'Resend verification email' }: { email: string; label?: string }) {
  const toast = useToast()
  const [sending, setSending] = useState(false)
  const [sent, setSent] = useState(false)

  const resend = async () => {
    setSending(true)
    try {
      await accountApi.resendVerification(email)
      setSent(true)
      toast.success('Verification email sent')
    } catch (e) {
      toast.error(e)
    } finally {
      setSending(false)
    }
  }

  return (
    <Button variant="secondary" onClick={resend} loading={sending} disabled={!email || sent}>
      {sent ? 'Email sent — check your inbox' : label}
    </Button>
  )
}
