import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { useColors } from "@/hooks/useColors";

interface Props {
  title: string;
  right?: React.ReactNode;
}

export function SectionHeader({ title, right }: Props) {
  const colors = useColors();
  return (
    <View style={styles.row}>
      <Text style={[styles.title, { color: colors.mutedForeground }]}>
        {title}
      </Text>
      {right}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingBottom: 8,
  },
  title: {
    fontSize: 11,
    fontFamily: "Inter_600SemiBold",
    textTransform: "uppercase",
    letterSpacing: 1,
  },
});
