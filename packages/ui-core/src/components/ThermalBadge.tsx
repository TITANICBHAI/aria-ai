import React from "react";
import type { ThermalLevel } from "@workspace/schemas";
import { ARIAColors } from "../theme";

interface ThermalBadgeProps {
  level: ThermalLevel;
  showLabel?: boolean;
  className?: string;
}

const THERMAL_COLOR: Record<ThermalLevel, string> = {
  safe:     ARIAColors.Success,
  light:    ARIAColors.Success,
  moderate: ARIAColors.Warning,
  severe:   ARIAColors.Error,
  critical: ARIAColors.Error,
};

const THERMAL_LABEL: Record<ThermalLevel, string> = {
  safe:     "Safe",
  light:    "Light",
  moderate: "Moderate",
  severe:   "Severe ⚠",
  critical: "Critical !!",
};

/**
 * ThermalBadge — shows thermal throttle level as a coloured indicator.
 */
export function ThermalBadge({ level, showLabel = true, className = "" }: ThermalBadgeProps) {
  const color = THERMAL_COLOR[level];
  return (
    <span
      className={className}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 5,
        fontSize: 11,
        fontWeight: 600,
        color,
      }}
    >
      <span style={{ width: 6, height: 6, borderRadius: "50%", background: color }} />
      {showLabel && THERMAL_LABEL[level]}
    </span>
  );
}
