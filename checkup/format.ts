/**
 * packages/shared-utils/src/format.ts
 *
 * Display formatting utilities shared between the mobile app (JS layer)
 * and the web dashboard. Pure functions — no side effects, no imports.
 */

/**
 * Format a byte count as a human-readable string.
 * formatBytes(870_000_000) → "870.0 MB"
 * formatBytes(1_234_567)   → "1.2 MB"
 * formatBytes(512)         → "512 B"
 */
export function formatBytes(bytes: number, decimals = 1): string {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const dm = Math.max(0, decimals);
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const val = parseFloat((bytes / Math.pow(k, i)).toFixed(dm));
  return `${val} ${sizes[Math.min(i, sizes.length - 1)]}`;
}

/**
 * Format a megabyte count as "X.X MB" or "X.X GB".
 * formatMb(870)   → "870.0 MB"
 * formatMb(2000)  → "2.0 GB"
 */
export function formatMb(mb: number, decimals = 1): string {
  if (mb >= 1024) return `${(mb / 1024).toFixed(decimals)} GB`;
  return `${mb.toFixed(decimals)} MB`;
}

/**
 * Format a tokens-per-second rate.
 * formatTokenRate(0)    → "— tok/s"
 * formatTokenRate(12.4) → "12.4 tok/s"
 */
export function formatTokenRate(toksPerSec: number): string {
  if (toksPerSec <= 0) return "— tok/s";
  return `${toksPerSec.toFixed(1)} tok/s`;
}

/**
 * Format a Unix millisecond timestamp as a relative string.
 * formatRelativeTime(Date.now() - 90_000) → "1m 30s ago"
 * formatRelativeTime(Date.now() - 3600_000) → "1h ago"
 */
export function formatRelativeTime(timestampMs: number): string {
  const diffMs  = Date.now() - timestampMs;
  const diffSec = Math.floor(diffMs / 1000);
  if (diffSec < 60)   return `${diffSec}s ago`;
  const diffMin = Math.floor(diffSec / 60);
  const remSec  = diffSec % 60;
  if (diffMin < 60)   return remSec > 0 ? `${diffMin}m ${remSec}s ago` : `${diffMin}m ago`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24)    return `${diffHr}h ago`;
  return `${Math.floor(diffHr / 24)}d ago`;
}

/**
 * Format a duration in milliseconds as "Xm Ys".
 * formatDuration(90_500)  → "1m 30s"
 * formatDuration(5_000)   → "5s"
 */
export function formatDuration(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  if (min === 0) return `${sec}s`;
  return sec > 0 ? `${min}m ${sec}s` : `${min}m`;
}

/**
 * Format a 0–1 ratio as a percentage string.
 * formatPercent(0.924) → "92.4%"
 */
export function formatPercent(ratio: number, decimals = 1): string {
  return `${(ratio * 100).toFixed(decimals)}%`;
}

/**
 * Format a reward value with sign.
 * formatReward(1.5)  → "+1.50"
 * formatReward(-0.3) → "-0.30"
 */
export function formatReward(reward: number): string {
  const sign = reward >= 0 ? "+" : "";
  return `${sign}${reward.toFixed(2)}`;
}

/**
 * Format a download speed in MB/s.
 * formatSpeed(2.35) → "2.35 MB/s"
 * formatSpeed(0)    → "—"
 */
export function formatSpeed(mbps: number): string {
  if (mbps <= 0) return "—";
  return `${mbps.toFixed(2)} MB/s`;
}

/**
 * Clamp a number between min and max.
 */
export function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

/**
 * Truncate a string to maxLength with ellipsis.
 * truncate("Hello World", 8) → "Hello..."
 */
export function truncate(str: string, maxLength: number): string {
  if (str.length <= maxLength) return str;
  return str.slice(0, maxLength - 3) + "...";
}
