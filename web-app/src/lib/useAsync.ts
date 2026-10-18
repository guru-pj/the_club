import { useCallback, useEffect, useRef, useState } from 'react'

export interface AsyncState<T> {
  data: T | undefined
  error: Error | undefined
  loading: boolean
  reload: () => void
  setData: (updater: T | ((prev: T | undefined) => T)) => void
}

/** Runs `fn` on mount and whenever `deps` change; ignores results from stale runs. */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setDataState] = useState<T>()
  const [error, setError] = useState<Error>()
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)
  const run = useRef(0)

  useEffect(() => {
    const id = ++run.current
    setLoading(true)
    setError(undefined)
    fn()
      .then((value) => {
        if (id === run.current) setDataState(value)
      })
      .catch((e: Error) => {
        if (id === run.current) setError(e)
      })
      .finally(() => {
        if (id === run.current) setLoading(false)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  const reload = useCallback(() => setTick((t) => t + 1), [])
  const setData = useCallback((updater: T | ((prev: T | undefined) => T)) => {
    setDataState((prev) => (typeof updater === 'function' ? (updater as (p: T | undefined) => T)(prev) : updater))
  }, [])
  return { data, error, loading, reload, setData }
}
