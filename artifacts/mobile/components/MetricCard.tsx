import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { useColors } from "@/hooks/useColors";

interface Props {
  label: string;
  value: string;
  subLabel?: string;
  accent?: boolean;
}

export function MetricCard({ label, value, subLabel, accent }: Props) {
  const colors = useColors();
  return (
    <View
      style={[
        styles.card,
        {
          backgroundColor: colors.card,
          borderColor: accent ? colors.primary + "33" : colors.border,
          borderWidth: 1,
          borderRadius: 12,
        },
      ]}
    >
      <Text style={[styles.label, { color: colors.mutedForeground }]}>
        {label}
      </Text>
      <Text
        style={[
          styles.value,
          { color: accent ? colors.primary : colors.foreground },
        ]}
      >
        {value}
      </Text>
      {subLabel ? (
        <Text style={[styles.sub, { color: colors.mutedForeground }]}>
          {subLabel}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    flex: 1,
    padding: 14,
    gap: 4,
  },
  label: {
    fontSize: 11,
    fontFamily: "Inter_500Medium",
    textTransform: "uppercase",
    letterSpacing: 0.8,
  },
  value: {
    fontSize: 22,
    fontFamily: "Inter_700Bold",
  },
  sub: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
  },
});
