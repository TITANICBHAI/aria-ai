import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { Feather } from "@expo/vector-icons";
import { useColors } from "@/hooks/useColors";

interface Props {
  icon: React.ComponentProps<typeof Feather>["name"];
  label: string;
  sublabel: string;
  ready: boolean;
}

export function ModuleRow({ icon, label, sublabel, ready }: Props) {
  const colors = useColors();
  return (
    <View
      style={[
        styles.row,
        {
          backgroundColor: colors.surface1,
          borderColor: ready ? colors.success + "22" : colors.border,
          borderWidth: 1,
          borderRadius: 12,
        },
      ]}
    >
      <View
        style={[
          styles.iconBox,
          {
            backgroundColor: ready
              ? colors.success + "15"
              : colors.muted,
          },
        ]}
      >
        <Feather
          name={icon}
          size={18}
          color={ready ? colors.success : colors.mutedForeground}
        />
      </View>
      <View style={styles.info}>
        <Text style={[styles.label, { color: colors.foreground }]}>{label}</Text>
        <Text style={[styles.sub, { color: colors.mutedForeground }]}>
          {sublabel}
        </Text>
      </View>
      <View
        style={[
          styles.badge,
          {
            backgroundColor: ready
              ? colors.success + "20"
              : colors.muted,
          },
        ]}
      >
        <Text
          style={[
            styles.badgeText,
            { color: ready ? colors.success : colors.mutedForeground },
          ]}
        >
          {ready ? "READY" : "OFFLINE"}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    padding: 14,
    gap: 12,
  },
  iconBox: {
    width: 38,
    height: 38,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
  },
  info: {
    flex: 1,
    gap: 2,
  },
  label: {
    fontSize: 14,
    fontFamily: "Inter_600SemiBold",
  },
  sub: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  badgeText: {
    fontSize: 10,
    fontFamily: "Inter_700Bold",
    letterSpacing: 0.5,
  },
});
