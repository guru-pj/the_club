import { ApiError } from '../api/client'

/** Validation message for one field from an ApiError (server-side bean validation). */
export function fieldError(error: unknown, field: string): string | undefined {
  return error instanceof ApiError ? error.fieldErrors[field] : undefined
}
