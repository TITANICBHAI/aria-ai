/**
 * packages/ui-core/src/theme.ts
 *
 * ARIA Agent design tokens — shared between the web dashboard and the mobile app JS layer.
 * These match the dark space theme used in the React Native shell and Jetpack Compose UI.
 *
 * Kotlin counterpart: ARIATheme.kt (ARIAColors object)
 */

export const ARIAColors = {
  Background:   "#0a0f1e",   // navy — primary background
  Surface:      "#111827",   // card / panel backgrounds
  SurfaceAlt:   "#1e2a3a",   // slightly lighter surface (hover, active states)
  Border:       "#1e2a3a",   // card borders
  Primary:      "#00d4ff",   // cyan — primary accent (links, active indicators)
  Accent:       "#7c3aed",   // violet — secondary accent (LoRA, ML labels)
  Success:      "#10b981",   // green — success states
  Warning:      "#f59e0b",   // amber — warning states (thermal MODERATE)
  Error:        "#ef4444",   // red — error / critical states
  TextPrimary:  "#e5e7eb",   // near-white primary text
  TextSecondary:"#9ca3af",   // muted secondary text
  TextDisabled: "#4b5563",   // disabled / placeholder text
} as const;

export type ARIAColorKey = keyof typeof ARIAColors;

/** CSS custom-property map — inject into :root for web dashboard */
export const ARIACssVars = Object.entries(ARIAColors).reduce<Record<string, string>>(
  (acc, [key, value]) => {
    acc[`--aria-${key.replace(/([A-Z])/g, "-$1").toLowerCase()}`] = value;
    return acc;
  },
  {}
);
