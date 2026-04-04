import React from "react";
import { ARIAColors } from "../theme";

interface MetricCardProps {
  label: string;
  value: string | number;
  unit?: string;
  sub?: string;
  accent?: string;
  className?: string;
}

/**
 * MetricCard — compact stat card (value + label + optional sub-text).
 * Used in the dashboard grid for token rate, step count, success rate, etc.
 */
export function MetricCard({
  label,
  value,
  unit,
  sub,
  accent = ARIAColors.Primary,
  className = "",
}: MetricCardProps) {
  return (
    <div
      className={className}
      style={{
        background: ARIAColors.Surface,
        border: `1px solid ${ARIAColors.Border}`,
        borderRadius: 10,
        padding: "14px 16px",
        minWidth: 100,
      }}
    >
      <div style={{ fontSize: 11, color: ARIAColors.TextSecondary, marginBottom: 6, textTransform: "uppercase", letterSpacing: 0.8 }}>
        {label}
      </div>
      <div style={{ fontSize: 22, fontWeight: 700, color: accent, lineHeight: 1.1 }}>
        {value}
        {unit && <span style={{ fontSize: 13, fontWeight: 400, color: ARIAColors.TextSecondary, marginLeft: 4 }}>{unit}</span>}
      </div>
      {sub && (
        <div style={{ fontSize: 11, color: ARIAColors.TextDisabled, marginTop: 4 }}>{sub}</div>
      )}
    </div>
  );
}
