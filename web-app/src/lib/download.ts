import { isMobileApp } from './platform'

/**
 * Saves a downloaded file under its name: the browser's download, or in the Android and iOS apps (whose web views
 * ignore downloads) the share sheet, where it can be saved to Files or opened in another app.
 */
export async function saveFile(blob: Blob, fileName: string): Promise<void> {
  if (isMobileApp()) return shareFile(blob, fileName)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}

async function shareFile(blob: Blob, fileName: string) {
  const [{ Directory, Filesystem }, { Share }] = await Promise.all([import('@capacitor/filesystem'), import('@capacitor/share')])
  const data = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result).replace(/^data:[^,]*,/, ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(blob)
  })
  // the app's cache: private to the app, and cleared by the system when space runs low
  const { uri } = await Filesystem.writeFile({ path: fileName, data, directory: Directory.Cache })
  try {
    await Share.share({ title: fileName, files: [uri], dialogTitle: fileName })
  } catch (e) {
    if (!/cancel/i.test(e instanceof Error ? e.message : String(e))) throw e
  }
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
