import { useState, useEffect, useCallback } from "react";

/**
 * usePolling — fetch data on an interval, return loading/error/data state.
 *
 * @param fetcher  Async function that returns the data
 * @param interval Polling interval in milliseconds (default: 5000)
 */
export function usePolling<T>(
  fetcher: () => Promise<T>,
  interval = 5000
): { data: T | null; loading: boolean; error: string | null; refresh: () => void } {
  const [data, setData]       = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState<string | null>(null);

  const run = useCallback(async () => {
    try {
      const result = await fetcher();
      setData(result);
      setError(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Unknown error");
    } finally {
      setLoading(false);
    }
  }, [fetcher]);

  useEffect(() => {
    run();
    const id = setInterval(run, interval);
    return () => clearInterval(id);
  }, [run, interval]);

  return { data, loading, error, refresh: run };
}
