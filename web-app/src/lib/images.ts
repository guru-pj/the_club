export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024

/** Checks a chosen file before uploading, with the same rules the server applies; null when it's fine. */
export function imageProblem(file: { type: string; size: number }): string | null {
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) return 'Choose a JPEG, PNG or WebP image'
  if (file.size > MAX_IMAGE_BYTES) return 'Images can be at most 5 MB'
  return null
}
