import React from "react";
import type { AgentStatus } from "@workspace/schemas";
import { ARIAColors } from "../theme";

interface StatusPillProps {
  status: AgentStatus;
  pulsing?: boolean;
  className?: string;
}

const LABEL: Record<AgentStatus, string> = {
  idle:    "Idle",
  running: "Running",
  paused:  "Paused",
  error:   "Error",
};

const COLOR: Record<AgentStatus, string> = {
  idle:    ARIAColors.TextSecondary,
  running: ARIAColors.Primary,
  paused:  ARIAColors.Warning,
  error:   ARIAColors.Error,
};

/**
 * StatusPill — compact coloured badge showing the agent's current status.
 * Shared between the web dashboard and any future server-side rendered views.
 */
export function StatusPill({ status, pulsing = false, className = "" }: StatusPillProps) {
  const color = COLOR[status];
  return (
    <span
      className={className}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: "2px 10px",
        borderRadius: 12,
        fontSize: 12,
        fontWeight: 600,
        letterSpacing: 0.5,
        background: `${color}22`,
        border: `1px solid ${color}55`,
        color,
      }}
    >
      <span
        style={{
          width: 7,
          height: 7,
          borderRadius: "50%",
          background: color,
          animation: pulsing && status === "running" ? "aria-pulse 1.4s ease-in-out infinite" : undefined,
        }}
      />
      {LABEL[status]}
    </span>
  );
}
