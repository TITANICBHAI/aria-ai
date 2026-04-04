import React, { useEffect, useRef } from "react";
import { Animated, StyleSheet, View } from "react-native";
import { useColors } from "@/hooks/useColors";
import { AgentStatus } from "@/native-bindings/AgentCoreBridge";

interface Props {
  status: AgentStatus;
  size?: number;
}

export function StatusDot({ status, size = 10 }: Props) {
  const colors = useColors();
  const pulse = useRef(new Animated.Value(1)).current;

  const dotColor =
    status === "running"
      ? colors.success
      : status === "paused"
      ? colors.warning
      : status === "error"
      ? colors.destructive
      : colors.mutedForeground;

  useEffect(() => {
    if (status === "running") {
      const anim = Animated.loop(
        Animated.sequence([
          Animated.timing(pulse, {
            toValue: 1.6,
            duration: 800,
            useNativeDriver: true,
          }),
          Animated.timing(pulse, {
            toValue: 1,
            duration: 800,
            useNativeDriver: true,
          }),
        ])
      );
      anim.start();
      return () => anim.stop();
    } else {
      pulse.setValue(1);
    }
  }, [status, pulse]);

  return (
    <View style={[styles.container, { width: size + 8, height: size + 8 }]}>
      <Animated.View
        style={[
          styles.ring,
          {
            width: size + 8,
            height: size + 8,
            borderRadius: (size + 8) / 2,
            borderColor: dotColor,
            opacity: status === "running" ? 0.3 : 0,
            transform: [{ scale: pulse }],
          },
        ]}
      />
      <View
        style={[
          styles.dot,
          {
            width: size,
            height: size,
            borderRadius: size / 2,
            backgroundColor: dotColor,
          },
        ]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: "center",
    justifyContent: "center",
  },
  ring: {
    position: "absolute",
    borderWidth: 1.5,
  },
  dot: {},
});
