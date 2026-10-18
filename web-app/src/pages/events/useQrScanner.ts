import { Html5Qrcode } from 'html5-qrcode'
import { useCallback, useEffect, useRef, useState } from 'react'

type ScannerState = 'idle' | 'starting' | 'scanning' | 'error'

/**
 * Wraps html5-qrcode's camera scanner. The camera is always released on stop and on unmount.
 */
export function useQrScanner(elementId: string, onScan: (text: string) => void) {
  const scanner = useRef<Html5Qrcode | null>(null)
  const onScanRef = useRef(onScan)
  const [state, setState] = useState<ScannerState>('idle')
  const [error, setError] = useState<string>()

  useEffect(() => {
    onScanRef.current = onScan
  }, [onScan])

  const stop = useCallback(async () => {
    const current = scanner.current
    scanner.current = null
    if (current) {
      try {
        if (current.isScanning) await current.stop()
        current.clear()
      } catch {
        // already stopped
      }
    }
    setState('idle')
  }, [])

  const start = useCallback(async () => {
    if (scanner.current) return
    setError(undefined)
    setState('starting')
    const instance = new Html5Qrcode(elementId, { verbose: false })
    scanner.current = instance
    try {
      await instance.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 250, height: 250 } },
        (text) => onScanRef.current(text),
        () => undefined, // per-frame "no code found" noise
      )
      if (scanner.current !== instance) {
        // stopped or unmounted while the camera was starting: release it now
        await instance.stop().catch(() => undefined)
        instance.clear()
        return
      }
      setState('scanning')
    } catch (e) {
      if (scanner.current !== instance) return
      scanner.current = null
      try {
        instance.clear()
      } catch {
        // nothing rendered
      }
      setState('error')
      setError(describeCameraError(e))
    }
  }, [elementId])

  useEffect(() => () => {
    void stop()
  }, [stop])

  return { state, error, start, stop }
}

function describeCameraError(e: unknown): string {
  const text = e instanceof Error ? `${e.name} ${e.message}` : String(e)
  if (/NotAllowed|Permission/i.test(text)) return 'Camera permission was denied. Allow camera access in your browser, or enter the ticket code below.'
  if (/NotFound|no camera|Requested device not found/i.test(text)) return 'No camera was found on this device. Enter the ticket code below.'
  if (/NotReadable|in use/i.test(text)) return 'The camera is being used by another app. Close it and try again.'
  if (!window.isSecureContext) return 'The camera only works over HTTPS (or on localhost). Enter the ticket code below.'
  return `Could not start the camera: ${text}`
}
