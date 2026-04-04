import React from "react";
import type { ActionLog } from "@workspace/schemas";
import { ARIAColors } from "../theme";
import { formatRelativeTime, formatReward } from "@workspace/shared-utils";

interface AgentLogRowProps {
  log: ActionLog;
  style?: React.CSSProperties;
}

const TYPE_ICON: Record<ActionLog["type"], string> = {
  tap:     "👆",
  swipe:   "👋",
  text:    "⌨️",
  scroll:  "↕️",
  intent:  "📡",
  observe: "👁",
};

/**
 * AgentLogRow — a single row in the agent activity log.
 * Shared between the web dashboard and any future views.
 */
export function AgentLogRow({ log, style }: AgentLogRowProps) {
  const iconColor = log.success ? ARIAColors.Success : ARIAColors.Error;
  return (
    <div
      style={{
        display: "flex",
        alignItems: "flex-start",
        gap: 10,
        padding: "8px 12px",
        borderBottom: `1px solid ${ARIAColors.Border}`,
        ...style,
      }}
    >
      <span style={{ fontSize: 16, lineHeight: 1.4 }}>{TYPE_ICON[log.type] ?? "•"}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, color: ARIAColors.TextPrimary, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
          {log.description || log.type}
        </div>
        <div style={{ fontSize: 11, color: ARIAColors.TextSecondary, marginTop: 2 }}>
          {log.app} · {formatRelativeTime(log.timestamp)}
          {log.rewardSignal !== undefined && (
            <span style={{ marginLeft: 8, color: log.rewardSignal >= 0 ? ARIAColors.Success : ARIAColors.Error }}>
              {formatReward(log.rewardSignal)}
            </span>
          )}
        </div>
      </div>
      <span style={{ fontSize: 11, color: iconColor, fontWeight: 600, flexShrink: 0 }}>
        {log.success ? "✓" : "✗"}
      </span>
    </div>
  );
}
