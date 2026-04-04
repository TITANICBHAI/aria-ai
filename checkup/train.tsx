/**
 * Training Screen
 *
 * Two training modalities:
 *
 *  1. IRL Video Training — pick a recorded video from the device gallery,
 *     set the task goal + target app, then call processIrlVideo(). The Kotlin
 *     side extracts (state→action) tuples frame-by-frame using OCR + a11y tree
 *     and stores them in ExperienceStore for the next RL cycle.
 *
 *  2. RL Cycle (on-device REINFORCE) — trigger runRlCycle() which pulls
 *     untrainedSuccesses from ExperienceStore, runs the REINFORCE / Adam update
 *     on PolicyNetwork, and saves a new LoRA adapter via LoraTrainer.
 *
 *  Shortcut card to the Object Labeler (screenshot annotation) is also here.
 */

import React, { useCallback, useEffect, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import * as ImagePicker from "expo-image-picker";
import { router } from "expo-router";

import { useColors } from "@/hooks/useColors";
import { AgentCoreBridge } from "@/native-bindings/AgentCoreBridge";
import { SectionHeader } from "@/components/SectionHeader";

// ─── Types ────────────────────────────────────────────────────────────────────

interface LearningStatus {
  loraVersion: number;
  latestAdapterPath: string;
  adapterExists: boolean;
  untrainedSamples: number;
  policyReady: boolean;
}

interface IrlResult {
  framesProcessed: number;
  tuplesExtracted: number;
  llmAssistedCount: number;
  errorMessage: string;
}

interface RlResult {
  success: boolean;
  samplesUsed: number;
  adapterPath: string;
  loraVersion: number;
  errorMessage: string;
}

// ─── Small helper components ──────────────────────────────────────────────────

function StatBadge({
  label,
  value,
  accent,
  colors,
}: {
  label: string;
  value: string | number;
  accent?: boolean;
  colors: any;
}) {
  return (
    <View
      style={[
        styles.statBadge,
        {
          backgroundColor: accent ? colors.primary + "18" : colors.muted,
          borderColor: accent ? colors.primary + "40" : colors.border,
        },
      ]}
    >
      <Text style={[styles.statValue, { color: accent ? colors.primary : colors.foreground }]}>
        {value}
      </Text>
      <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{label}</Text>
    </View>
  );
}

function SectionCard({
  children,
  colors,
}: {
  children: React.ReactNode;
  colors: any;
}) {
  return (
    <View
      style={[
        styles.card,
        { backgroundColor: colors.surface1, borderColor: colors.border },
      ]}
    >
      {children}
    </View>
  );
}

// ─── Screen ───────────────────────────────────────────────────────────────────

export default function TrainScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const topPad = Platform.OS === "web" ? 67 : insets.top;

  // ── RL status ──────────────────────────────────────────────────────────────
  const [learningStatus, setLearningStatus] = useState<LearningStatus | null>(null);
  const [loadingStatus, setLoadingStatus] = useState(false);

  const fetchStatus = useCallback(async () => {
    setLoadingStatus(true);
    try {
      const s = await AgentCoreBridge.getLearningStatus();
      setLearningStatus(s);
    } catch {
      // ignore on web preview
    } finally {
      setLoadingStatus(false);
    }
  }, []);

  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  // ── RL cycle ───────────────────────────────────────────────────────────────
  const [runningRl, setRunningRl] = useState(false);
  const [rlResult, setRlResult] = useState<RlResult | null>(null);

  const handleRunRlCycle = async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
    setRunningRl(true);
    setRlResult(null);
    try {
      const result = await AgentCoreBridge.runRlCycle();
      setRlResult(result);
      await fetchStatus();
      if (result.success) {
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      } else {
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      }
    } catch (e: any) {
      Alert.alert("RL Cycle Failed", e?.message ?? "Unknown error");
    } finally {
      setRunningRl(false);
    }
  };

  // ── IRL video training ─────────────────────────────────────────────────────
  const [videoUri, setVideoUri] = useState<string | null>(null);
  const [videoName, setVideoName] = useState<string>("");
  const [irlGoal, setIrlGoal] = useState("");
  const [irlApp, setIrlApp] = useState("");
  const [runningIrl, setRunningIrl] = useState(false);
  const [irlResult, setIrlResult] = useState<IrlResult | null>(null);

  const handlePickVideo = async () => {
    try {
      const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (status !== "granted") {
        Alert.alert(
          "Permission needed",
          "Allow media library access so ARIA can read the training video."
        );
        return;
      }
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: "videos",
        allowsEditing: false,
        quality: 1,
      });
      if (!result.canceled && result.assets?.[0]) {
        const asset = result.assets[0];
        setVideoUri(asset.uri);
        setVideoName(asset.fileName ?? asset.uri.split("/").pop() ?? "video.mp4");
        setIrlResult(null);
      }
    } catch (e: any) {
      Alert.alert("Could not open gallery", e?.message ?? "Unknown error");
    }
  };

  const handleRunIrl = async () => {
    if (!videoUri) {
      Alert.alert("No video", "Pick a video from your gallery first.");
      return;
    }
    if (!irlGoal.trim()) {
      Alert.alert("Goal required", "Enter the task goal shown in the video.");
      return;
    }
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
    setRunningIrl(true);
    setIrlResult(null);
    try {
      const result = await AgentCoreBridge.processIrlVideo(
        videoUri,
        irlGoal.trim(),
        irlApp.trim() || "com.android.chrome"
      );
      setIrlResult({
        framesProcessed: result.framesProcessed,
        tuplesExtracted: result.tuplesExtracted,
        llmAssistedCount: result.llmAssistedCount,
        errorMessage: result.errorMessage,
      });
      await fetchStatus();
      if (!result.errorMessage) {
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      }
    } catch (e: any) {
      Alert.alert("IRL Processing Failed", e?.message ?? "Unknown error");
    } finally {
      setRunningIrl(false);
    }
  };

  // ─────────────────────────────────────────────────────────────────────────

  return (
    <ScrollView
      style={[styles.root, { backgroundColor: colors.background }]}
      contentContainerStyle={[
        styles.content,
        { paddingTop: topPad + 16, paddingBottom: 100 },
      ]}
      showsVerticalScrollIndicator={false}
    >
      {/* Header */}
      <View style={styles.headerRow}>
        <View>
          <Text style={[styles.title, { color: colors.foreground }]}>Training</Text>
          <Text style={[styles.subtitle, { color: colors.mutedForeground }]}>
            Teach ARIA from video or on-device RL
          </Text>
        </View>
        <TouchableOpacity
          onPress={fetchStatus}
          style={[styles.refreshBtn, { backgroundColor: colors.muted }]}
          disabled={loadingStatus}
        >
          {loadingStatus ? (
            <ActivityIndicator size={14} color={colors.mutedForeground} />
          ) : (
            <Feather name="refresh-cw" size={14} color={colors.mutedForeground} />
          )}
        </TouchableOpacity>
      </View>

      {/* ── RL STATUS ─────────────────────────────────────────────────── */}
      <View style={styles.sectionGap}>
        <SectionHeader title="On-Device RL Status" />
      </View>
      <SectionCard colors={colors}>
        <View style={styles.statRow}>
          <StatBadge
            label="LoRA Version"
            value={learningStatus?.loraVersion ?? "—"}
            accent={!!learningStatus?.adapterExists}
            colors={colors}
          />
          <StatBadge
            label="Untrained Samples"
            value={learningStatus?.untrainedSamples ?? "—"}
            accent={(learningStatus?.untrainedSamples ?? 0) > 0}
            colors={colors}
          />
          <StatBadge
            label="Policy"
            value={learningStatus?.policyReady ? "Ready" : "Not ready"}
            accent={learningStatus?.policyReady}
            colors={colors}
          />
        </View>

        {learningStatus?.latestAdapterPath ? (
          <Text
            style={[styles.adapterPath, { color: colors.mutedForeground }]}
            numberOfLines={1}
          >
            {learningStatus.latestAdapterPath.split("/").pop()}
          </Text>
        ) : null}

        {/* RL result */}
        {rlResult ? (
          <View
            style={[
              styles.resultBox,
              {
                backgroundColor: rlResult.success
                  ? colors.success + "12"
                  : colors.destructive + "12",
                borderColor: rlResult.success
                  ? colors.success + "40"
                  : colors.destructive + "40",
              },
            ]}
          >
            {rlResult.success ? (
              <>
                <Text style={[styles.resultTitle, { color: colors.success }]}>
                  ✓ RL Cycle complete
                </Text>
                <Text style={[styles.resultDetail, { color: colors.foreground }]}>
                  {rlResult.samplesUsed} samples used · LoRA v{rlResult.loraVersion}
                </Text>
              </>
            ) : (
              <>
                <Text style={[styles.resultTitle, { color: colors.destructive }]}>
                  ✗ RL Cycle failed
                </Text>
                <Text style={[styles.resultDetail, { color: colors.mutedForeground }]}>
                  {rlResult.errorMessage || "Unknown error"}
                </Text>
              </>
            )}
          </View>
        ) : null}

        <TouchableOpacity
          style={[
            styles.primaryBtn,
            {
              backgroundColor: runningRl ? colors.muted : colors.primary,
              opacity: runningRl ? 0.7 : 1,
            },
          ]}
          onPress={handleRunRlCycle}
          disabled={runningRl}
          activeOpacity={0.8}
        >
          {runningRl ? (
            <ActivityIndicator size={16} color="#fff" style={{ marginRight: 8 }} />
          ) : (
            <Feather name="zap" size={16} color="#fff" style={{ marginRight: 8 }} />
          )}
          <Text style={styles.primaryBtnText}>
            {runningRl ? "Running RL cycle…" : "Run RL Cycle Now"}
          </Text>
        </TouchableOpacity>

        <Text style={[styles.hint, { color: colors.mutedForeground }]}>
          Triggers REINFORCE + Adam update on PolicyNetwork using stored experience tuples.
          Best run when charging and idle — happens automatically overnight.
        </Text>
      </SectionCard>

      {/* ── IRL VIDEO TRAINING ────────────────────────────────────────── */}
      <View style={styles.sectionGap}>
        <SectionHeader title="Video Training (IRL)" />
      </View>
      <SectionCard colors={colors}>
        <Text style={[styles.cardDesc, { color: colors.mutedForeground }]}>
          Record yourself completing a task on your device, then feed the video here.
          ARIA extracts (state→action) tuples using OCR + accessibility tree and stores
          them as training data for the next RL cycle.
        </Text>

        {/* Video picker */}
        <TouchableOpacity
          style={[
            styles.videoPicker,
            {
              backgroundColor: colors.muted,
              borderColor: videoUri ? colors.primary + "60" : colors.border,
              borderStyle: videoUri ? "solid" : "dashed",
            },
          ]}
          onPress={handlePickVideo}
          activeOpacity={0.7}
        >
          <Feather
            name={videoUri ? "film" : "upload"}
            size={22}
            color={videoUri ? colors.primary : colors.mutedForeground}
          />
          <Text
            style={[
              styles.videoPickerText,
              { color: videoUri ? colors.foreground : colors.mutedForeground },
            ]}
            numberOfLines={1}
          >
            {videoUri ? videoName : "Tap to pick a video from gallery"}
          </Text>
          {videoUri ? (
            <TouchableOpacity
              onPress={() => { setVideoUri(null); setVideoName(""); setIrlResult(null); }}
              hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}
            >
              <Feather name="x" size={16} color={colors.mutedForeground} />
            </TouchableOpacity>
          ) : null}
        </TouchableOpacity>

        {/* Goal input */}
        <Text style={[styles.inputLabel, { color: colors.foreground }]}>Task Goal</Text>
        <TextInput
          style={[
            styles.input,
            {
              backgroundColor: colors.muted,
              borderColor: colors.border,
              color: colors.foreground,
            },
          ]}
          placeholder="e.g. Open YouTube and play trending video"
          placeholderTextColor={colors.mutedForeground}
          value={irlGoal}
          onChangeText={setIrlGoal}
          multiline
          numberOfLines={2}
        />

        {/* App package */}
        <Text style={[styles.inputLabel, { color: colors.foreground }]}>
          App Package{" "}
          <Text style={{ color: colors.mutedForeground, fontWeight: "400" }}>(optional)</Text>
        </Text>
        <TextInput
          style={[
            styles.input,
            {
              backgroundColor: colors.muted,
              borderColor: colors.border,
              color: colors.foreground,
            },
          ]}
          placeholder="e.g. com.google.android.youtube"
          placeholderTextColor={colors.mutedForeground}
          value={irlApp}
          onChangeText={setIrlApp}
          autoCapitalize="none"
          autoCorrect={false}
        />

        {/* IRL result */}
        {irlResult ? (
          <View
            style={[
              styles.resultBox,
              {
                backgroundColor: irlResult.errorMessage
                  ? colors.destructive + "12"
                  : colors.success + "12",
                borderColor: irlResult.errorMessage
                  ? colors.destructive + "40"
                  : colors.success + "40",
              },
            ]}
          >
            {irlResult.errorMessage ? (
              <>
                <Text style={[styles.resultTitle, { color: colors.destructive }]}>
                  ✗ Processing failed
                </Text>
                <Text style={[styles.resultDetail, { color: colors.mutedForeground }]}>
                  {irlResult.errorMessage}
                </Text>
              </>
            ) : (
              <>
                <Text style={[styles.resultTitle, { color: colors.success }]}>
                  ✓ Video processed
                </Text>
                <View style={styles.irlStatRow}>
                  <Text style={[styles.irlStat, { color: colors.foreground }]}>
                    <Text style={{ fontWeight: "700" }}>{irlResult.framesProcessed}</Text> frames
                  </Text>
                  <Text style={[styles.irlStat, { color: colors.foreground }]}>
                    <Text style={{ fontWeight: "700" }}>{irlResult.tuplesExtracted}</Text> tuples
                  </Text>
                  <Text style={[styles.irlStat, { color: colors.foreground }]}>
                    <Text style={{ fontWeight: "700" }}>{irlResult.llmAssistedCount}</Text> LLM-assisted
                  </Text>
                </View>
                <Text style={[styles.hint, { color: colors.mutedForeground, marginTop: 4 }]}>
                  Tuples stored — run an RL cycle to train on them.
                </Text>
              </>
            )}
          </View>
        ) : null}

        <TouchableOpacity
          style={[
            styles.primaryBtn,
            {
              backgroundColor: runningIrl || !videoUri ? colors.muted : colors.primary,
              opacity: runningIrl || !videoUri ? 0.6 : 1,
            },
          ]}
          onPress={handleRunIrl}
          disabled={runningIrl || !videoUri}
          activeOpacity={0.8}
        >
          {runningIrl ? (
            <ActivityIndicator size={16} color="#fff" style={{ marginRight: 8 }} />
          ) : (
            <Feather name="play" size={16} color="#fff" style={{ marginRight: 8 }} />
          )}
          <Text style={styles.primaryBtnText}>
            {runningIrl ? "Extracting tuples…" : "Process Video"}
          </Text>
        </TouchableOpacity>
      </SectionCard>

      {/* ── SCREENSHOT LABELING SHORTCUT ──────────────────────────────── */}
      <View style={styles.sectionGap}>
        <SectionHeader title="Screenshot Labeling" />
      </View>
      <TouchableOpacity
        activeOpacity={0.8}
        onPress={() => {
          Haptics.selectionAsync();
          router.push("/labeler");
        }}
      >
        <SectionCard colors={colors}>
          <View style={styles.shortcutRow}>
            <View
              style={[styles.shortcutIcon, { backgroundColor: colors.primary + "18" }]}
            >
              <Feather name="tag" size={20} color={colors.primary} />
            </View>
            <View style={styles.shortcutText}>
              <Text style={[styles.shortcutTitle, { color: colors.foreground }]}>
                Object Labeler
              </Text>
              <Text style={[styles.shortcutDesc, { color: colors.mutedForeground }]}>
                Capture the screen, tap UI elements to annotate them, enrich with the LLM.
                Labels are injected into every future agent prompt.
              </Text>
            </View>
            <Feather name="chevron-right" size={18} color={colors.mutedForeground} />
          </View>
        </SectionCard>
      </TouchableOpacity>
    </ScrollView>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { paddingHorizontal: 16 },

  headerRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between",
    marginBottom: 4,
  },
  title: { fontSize: 26, fontFamily: "Inter_700Bold" },
  subtitle: { fontSize: 13, fontFamily: "Inter_400Regular", marginTop: 2 },
  refreshBtn: {
    width: 32,
    height: 32,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 6,
  },

  sectionGap: { marginTop: 24 },

  card: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
    marginTop: 8,
    gap: 12,
  },
  cardDesc: { fontSize: 13, fontFamily: "Inter_400Regular", lineHeight: 19 },

  statRow: { flexDirection: "row", gap: 8 },
  statBadge: {
    flex: 1,
    borderRadius: 10,
    borderWidth: 1,
    paddingVertical: 10,
    paddingHorizontal: 8,
    alignItems: "center",
    gap: 2,
  },
  statValue: { fontSize: 16, fontFamily: "Inter_700Bold" },
  statLabel: { fontSize: 10, fontFamily: "Inter_400Regular", textAlign: "center" },

  adapterPath: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
    marginTop: -4,
  },

  resultBox: {
    borderRadius: 10,
    borderWidth: 1,
    padding: 12,
    gap: 4,
  },
  resultTitle: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  resultDetail: { fontSize: 12, fontFamily: "Inter_400Regular" },

  irlStatRow: { flexDirection: "row", gap: 16, marginTop: 2 },
  irlStat: { fontSize: 12, fontFamily: "Inter_400Regular" },

  primaryBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 12,
    paddingVertical: 13,
    paddingHorizontal: 20,
  },
  primaryBtnText: {
    color: "#fff",
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },

  hint: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
    lineHeight: 16,
    marginTop: -4,
  },

  videoPicker: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderRadius: 10,
    borderWidth: 1.5,
    padding: 14,
  },
  videoPickerText: {
    flex: 1,
    fontSize: 13,
    fontFamily: "Inter_400Regular",
  },

  inputLabel: {
    fontSize: 13,
    fontFamily: "Inter_500Medium",
    marginBottom: -6,
  },
  input: {
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 13,
    fontFamily: "Inter_400Regular",
    minHeight: 44,
  },

  shortcutRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  shortcutIcon: {
    width: 44,
    height: 44,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  shortcutText: { flex: 1, gap: 3 },
  shortcutTitle: { fontSize: 14, fontFamily: "Inter_600SemiBold" },
  shortcutDesc: { fontSize: 12, fontFamily: "Inter_400Regular", lineHeight: 17 },
});
