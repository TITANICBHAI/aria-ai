import React, { useState } from "react";
import {
  FlatList,
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";

import { useAgent } from "@/context/AgentContext";
import { useColors } from "@/hooks/useColors";
import { ActionLog, MemoryEntry } from "@/native-bindings/AgentCoreBridge";

type Tab = "actions" | "memory";

function ActionItem({ item, colors }: { item: ActionLog; colors: any }) {
  const iconMap: Record<ActionLog["type"], React.ComponentProps<typeof Feather>["name"]> = {
    tap: "mouse-pointer",
    swipe: "move",
    text: "type",
    scroll: "chevrons-down",
    intent: "share",
    observe: "eye",
  };

  return (
    <View
      style={[
        styles.logRow,
        {
          backgroundColor: colors.surface1,
          borderColor: item.success ? colors.border : colors.destructive + "22",
          borderLeftColor: item.success ? colors.success : colors.destructive,
          borderLeftWidth: 3,
          borderWidth: 1,
          borderRadius: 10,
        },
      ]}
    >
      <View
        style={[
          styles.logIcon,
          {
            backgroundColor: item.success
              ? colors.success + "15"
              : colors.destructive + "15",
          },
        ]}
      >
        <Feather
          name={iconMap[item.type] ?? "activity"}
          size={14}
          color={item.success ? colors.success : colors.destructive}
        />
      </View>
      <View style={styles.logInfo}>
        <Text style={[styles.logDesc, { color: colors.foreground }]}>
          {item.description}
        </Text>
        <View style={styles.logMeta}>
          <Text style={[styles.logApp, { color: colors.primary }]}>{item.app}</Text>
          <Text style={[styles.logTime, { color: colors.mutedForeground }]}>
            {new Date(item.timestamp).toLocaleTimeString()}
          </Text>
          {item.rewardSignal !== undefined && (
            <Text
              style={[
                styles.reward,
                {
                  color: item.rewardSignal >= 0 ? colors.success : colors.destructive,
                },
              ]}
            >
              r={item.rewardSignal.toFixed(2)}
            </Text>
          )}
        </View>
      </View>
    </View>
  );
}

function MemoryItem({ item, colors }: { item: MemoryEntry; colors: any }) {
  return (
    <View
      style={[
        styles.logRow,
        {
          backgroundColor: colors.surface1,
          borderColor: colors.border,
          borderLeftColor: colors.accent,
          borderLeftWidth: 3,
          borderWidth: 1,
          borderRadius: 10,
        },
      ]}
    >
      <View
        style={[styles.logIcon, { backgroundColor: colors.accent + "15" }]}
      >
        <Feather name="book" size={14} color={colors.accent} />
      </View>
      <View style={styles.logInfo}>
        <Text style={[styles.logDesc, { color: colors.foreground }]}>
          {item.summary}
        </Text>
        <View style={styles.logMeta}>
          <Text style={[styles.logApp, { color: colors.primary }]}>{item.app}</Text>
          <Text style={[styles.logTime, { color: colors.mutedForeground }]}>
            Used {item.usageCount}× · {Math.round(item.confidence * 100)}% conf
          </Text>
        </View>
      </View>
    </View>
  );
}

export default function LogsScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { actionLogs, memoryEntries, clearMemory, refresh } = useAgent();
  const [activeTab, setActiveTab] = useState<Tab>("actions");

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : 0;

  const isEmpty =
    activeTab === "actions"
      ? actionLogs.length === 0
      : memoryEntries.length === 0;

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      {/* Header */}
      <View
        style={[
          styles.header,
          { paddingTop: topPad + 16, backgroundColor: colors.background },
        ]}
      >
        <Text style={[styles.title, { color: colors.foreground }]}>
          Activity
        </Text>

        {/* Tab toggle */}
        <View
          style={[
            styles.tabBar,
            { backgroundColor: colors.surface1, borderRadius: 12 },
          ]}
        >
          {(["actions", "memory"] as Tab[]).map((t) => (
            <TouchableOpacity
              key={t}
              onPress={() => {
                Haptics.selectionAsync();
                setActiveTab(t);
              }}
              style={[
                styles.tab,
                {
                  backgroundColor:
                    activeTab === t ? colors.primary : "transparent",
                  borderRadius: 10,
                },
              ]}
            >
              <Text
                style={[
                  styles.tabText,
                  {
                    color:
                      activeTab === t
                        ? colors.primaryForeground
                        : colors.mutedForeground,
                  },
                ]}
              >
                {t === "actions" ? "Actions" : "Memory"}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Clear button for memory */}
        {activeTab === "memory" && memoryEntries.length > 0 && (
          <TouchableOpacity
            onPress={() => {
              Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
              clearMemory();
            }}
            style={[
              styles.clearBtn,
              {
                backgroundColor: colors.destructive + "15",
                borderRadius: 10,
              },
            ]}
          >
            <Feather name="trash-2" size={15} color={colors.destructive} />
          </TouchableOpacity>
        )}
      </View>

      {/* List */}
      {isEmpty ? (
        <View style={styles.empty}>
          <Feather
            name={activeTab === "actions" ? "activity" : "book"}
            size={40}
            color={colors.mutedForeground}
          />
          <Text style={[styles.emptyTitle, { color: colors.foreground }]}>
            {activeTab === "actions" ? "No actions yet" : "Memory is empty"}
          </Text>
          <Text style={[styles.emptySub, { color: colors.mutedForeground }]}>
            {activeTab === "actions"
              ? "Start the agent to see its actions here"
              : "The agent will store learned patterns here"}
          </Text>
        </View>
      ) : activeTab === "actions" ? (
        <FlatList
          data={actionLogs}
          keyExtractor={(i) => i.id}
          contentContainerStyle={[
            styles.list,
            { paddingBottom: bottomPad + 100 },
          ]}
          renderItem={({ item }) => (
            <ActionItem item={item} colors={colors} />
          )}
          showsVerticalScrollIndicator={false}
        />
      ) : (
        <FlatList
          data={memoryEntries}
          keyExtractor={(i) => i.id}
          contentContainerStyle={[
            styles.list,
            { paddingBottom: bottomPad + 100 },
          ]}
          renderItem={({ item }) => (
            <MemoryItem item={item} colors={colors} />
          )}
          showsVerticalScrollIndicator={false}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    paddingHorizontal: 20,
    paddingBottom: 12,
    gap: 12,
  },
  title: { fontSize: 28, fontFamily: "Inter_700Bold" },
  tabBar: {
    flexDirection: "row",
    padding: 4,
    gap: 4,
  },
  tab: {
    flex: 1,
    paddingVertical: 8,
    alignItems: "center",
  },
  tabText: {
    fontSize: 13,
    fontFamily: "Inter_600SemiBold",
  },
  clearBtn: {
    alignSelf: "flex-end",
    padding: 10,
  },
  empty: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    paddingHorizontal: 40,
  },
  emptyTitle: { fontSize: 18, fontFamily: "Inter_600SemiBold", marginTop: 8 },
  emptySub: {
    fontSize: 13,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
    lineHeight: 20,
  },
  list: { paddingHorizontal: 16, paddingTop: 8, gap: 8 },
  logRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 10,
    padding: 12,
  },
  logIcon: {
    width: 32,
    height: 32,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
  },
  logInfo: { flex: 1, gap: 4 },
  logDesc: { fontSize: 13, fontFamily: "Inter_400Regular", lineHeight: 18 },
  logMeta: { flexDirection: "row", gap: 10, alignItems: "center", flexWrap: "wrap" },
  logApp: { fontSize: 11, fontFamily: "Inter_600SemiBold" },
  logTime: { fontSize: 11, fontFamily: "Inter_400Regular" },
  reward: { fontSize: 11, fontFamily: "Inter_700Bold" },
});
