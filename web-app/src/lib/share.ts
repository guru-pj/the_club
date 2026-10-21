import { apiUrl } from '../api/client'
import { isNativeApp, webAppUrl } from './platform'

/**
 * The public web address for an in-app path. The native apps load the UI from the device, so their own origin
 * (https://localhost, capacitor://…) is no use to anyone else: they use the configured web app URL, or the
 * server's address, which also serves the web app.
 */
export function publicUrl(path: string, origin: string, nativeApp = isNativeApp, configured = webAppUrl,
                          serverBase = apiUrl('')): string {
  const base = configured || (nativeApp ? serverBase : origin)
  return `${base.replace(/\/$/, '')}${path}`
}

export function postPath(clubId: string, postId: string): string {
  return `/clubs/${clubId}/posts/${postId}`
}

/**
 * Opens the system share sheet where there is one (phones, Safari), otherwise copies the link.
 * Resolves to what happened, so the caller can say "Link copied".
 */
export async function shareLink(title: string, url: string, text?: string): Promise<'shared' | 'copied' | 'cancelled'> {
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ title, text, url })
      return 'shared'
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return 'cancelled'
      // not allowed here (e.g. no user gesture): fall back to copying
    }
  }
  try {
    await navigator.clipboard.writeText(url)
  } catch {
    const field = document.createElement('textarea')
    field.value = url
    field.setAttribute('readonly', '')
    field.style.position = 'fixed'
    field.style.opacity = '0'
    document.body.appendChild(field)
    field.select()
    document.execCommand('copy')
    field.remove()
  }
  return 'copied'
}
