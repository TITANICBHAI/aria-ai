import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import {
  AgentCoreBridge,
  AgentCoreEmitter,
  DownloadProgress,
} from "@/native-bindings/AgentCoreBridge";

interface Props {
  onModelReady: () => void;
}

type Phase =
  | "checking"
  | "ready"
  | "confirm"
  | "downloading"
  | "error";

export function ModelDownloadScreen({ onModelReady }: Props) {
  const [phase, setPhase] = useState<Phase>("checking");
  const [progress, setProgress] = useState<DownloadProgress>({
    percent: 0,
    downloadedMb: 0,
    totalMb: 870,
    speedMbps: 0,
  });
  const [errorMsg, setErrorMsg] = useState("");
  const barAnim = useRef(new Animated.Value(0)).current;
  const pulseAnim = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, { toValue: 1.08, duration: 900, useNativeDriver: true }),
        Animated.timing(pulseAnim, { toValue: 1, duration: 900, useNativeDriver: true }),
      ])
    ).start();
  }, []);

  useEffect(() => {
    Animated.timing(barAnim, {
      toValue: progress.percent / 100,
      duration: 300,
      useNativeDriver: false,
    }).start();
  }, [progress.percent]);

  useEffect(() => {
    if (Platform.OS !== "android") {
      setPhase("confirm");
      return;
    }
    AgentCoreBridge.checkModelReady().then((ready) => {
      if (ready) {
        setPhase("ready");
        onModelReady();
      } else {
        setPhase("confirm");
      }
    });
  }, []);

  useEffect(() => {
    if (!AgentCoreEmitter) return;

    const progressSub = AgentCoreEmitter.addListener(
      "model_download_progress",
      (data: DownloadProgress) => {
        setProgress(data);
        setPhase("downloading");
      }
    );

    const completeSub = AgentCoreEmitter.addListener(
      "model_download_complete",
      () => {
        setPhase("ready");
        setTimeout(onModelReady, 800);
      }
    );

    const errorSub = AgentCoreEmitter.addListener(
      "model_download_error",
      (data: { error: string }) => {
        setErrorMsg(data.error);
        setPhase("error");
      }
    );

    return () => {
      progressSub.remove();
      completeSub.remove();
      errorSub.remove();
    };
  }, []);

  const startDownload = useCallback(async () => {
    setPhase("downloading");
    setProgress({ percent: 0, downloadedMb: 0, totalMb: 870, speedMbps: 0 });
    await AgentCoreBridge.startModelDownload();
  }, []);

  const cancelDownload = useCallback(async () => {
    await AgentCoreBridge.cancelModelDownload();
    setPhase("confirm");
  }, []);

  return (
    <SafeAreaView style={s.root}>
      <View style={s.container}>

        {/* Logo / icon */}
        <Animated.View style={[s.iconWrap, { transform: [{ scale: pulseAnim }] }]}>
          <View style={s.iconOuter}>
            <View style={s.iconInner}>
              <Text style={s.iconText}>A</Text>
            </View>
          </View>
        </Animated.View>

        <Text style={s.title}>ARIA Agent</Text>
        <Text style={s.subtitle}>On-Device AI · No Cloud · No Tracking</Text>

        {/* Checking */}
        {phase === "checking" && (
          <View style={s.card}>
            <ActivityIndicator color="#00d4ff" />
            <Text style={s.cardText}>Checking model status...</Text>
          </View>
        )}

        {/* Confirm download */}
        {phase === "confirm" && (
          <View style={s.card}>
            <Text style={s.cardTitle}>AI Brain Required</Text>
            <Text style={s.cardBody}>
              ARIA needs to download the{"\n"}
              <Text style={s.highlight}>Llama 3.2-1B Q4_K_M</Text> model
              to reason on your device.
            </Text>

            <View style={s.specRow}>
              <SpecItem label="Size" value="870 MB" />
              <SpecItem label="RAM use" value="~1.7 GB" />
              <SpecItem label="Speed" value="~12 tok/s" />
            </View>

            <Text style={s.note}>
              Downloads once. Stored privately on your device.{"\n"}
              Never leaves your phone after this.
            </Text>

            <TouchableOpacity style={s.downloadBtn} onPress={startDownload}>
              <Text style={s.downloadBtnText}>Download AI Brain</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* Downloading */}
        {phase === "downloading" && (
          <View style={s.card}>
            <Text style={s.cardTitle}>Downloading AI Brain</Text>

            <View style={s.barTrack}>
              <Animated.View
                style={[
                  s.barFill,
                  {
                    width: barAnim.interpolate({
                      inputRange: [0, 1],
                      outputRange: ["0%", "100%"],
                    }),
                  },
                ]}
              />
            </View>

            <Text style={s.percentText}>{progress.percent}%</Text>

            <Text style={s.progressDetail}>
              {progress.downloadedMb.toFixed(0)} MB / {progress.totalMb.toFixed(0)} MB
              {progress.speedMbps > 0 && `  ·  ${progress.speedMbps.toFixed(1)} MB/s`}
            </Text>

            <Text style={s.note}>
              Download will resume if interrupted.{"\n"}
              Keep app open for faster completion.
            </Text>

            <TouchableOpacity style={s.cancelBtn} onPress={cancelDownload}>
              <Text style={s.cancelBtnText}>Pause Download</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* Ready */}
        {phase === "ready" && (
          <View style={s.card}>
            <Text style={s.successIcon}>✓</Text>
            <Text style={s.cardTitle}>Model Ready</Text>
            <Text style={s.cardBody}>Llama 3.2-1B Q4_K_M loaded.</Text>
            <ActivityIndicator color="#00d4ff" style={{ marginTop: 16 }} />
          </View>
        )}

        {/* Error */}
        {phase === "error" && (
          <View style={s.card}>
            <Text style={s.errorIcon}>✕</Text>
            <Text style={s.cardTitle}>Download Failed</Text>
            <Text style={s.errorMsg}>{errorMsg}</Text>
            <TouchableOpacity style={s.downloadBtn} onPress={startDownload}>
              <Text style={s.downloadBtnText}>Retry</Text>
            </TouchableOpacity>
          </View>
        )}
      </View>
    </SafeAreaView>
  );
}

function SpecItem({ label, value }: { label: string; value: string }) {
  return (
    <View style={s.specItem}>
      <Text style={s.specValue}>{value}</Text>
      <Text style={s.specLabel}>{label}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: "#0a0f1e" },
  container: { flex: 1, alignItems: "center", justifyContent: "center", padding: 24 },

  iconWrap: { marginBottom: 24 },
  iconOuter: {
    width: 88, height: 88, borderRadius: 44,
    borderWidth: 2, borderColor: "#00d4ff",
    alignItems: "center", justifyContent: "center",
    backgroundColor: "#0d1526",
  },
  iconInner: {
    width: 64, height: 64, borderRadius: 32,
    backgroundColor: "#00d4ff20",
    alignItems: "center", justifyContent: "center",
  },
  iconText: { color: "#00d4ff", fontSize: 32, fontWeight: "700" },

  title: { color: "#e2e8f0", fontSize: 28, fontWeight: "700", marginBottom: 4 },
  subtitle: { color: "#64748b", fontSize: 13, marginBottom: 32 },

  card: {
    width: "100%", backgroundColor: "#111827",
    borderRadius: 16, borderWidth: 1, borderColor: "#1e293b",
    padding: 24, alignItems: "center",
  },
  cardTitle: { color: "#e2e8f0", fontSize: 18, fontWeight: "600", marginBottom: 12 },
  cardBody: { color: "#94a3b8", fontSize: 14, textAlign: "center", lineHeight: 22, marginBottom: 20 },
  cardText: { color: "#94a3b8", fontSize: 14, marginTop: 12 },
  highlight: { color: "#00d4ff", fontWeight: "600" },

  specRow: { flexDirection: "row", gap: 16, marginBottom: 20 },
  specItem: { alignItems: "center" },
  specValue: { color: "#00d4ff", fontSize: 16, fontWeight: "700" },
  specLabel: { color: "#64748b", fontSize: 11, marginTop: 2 },

  note: { color: "#475569", fontSize: 12, textAlign: "center", lineHeight: 18, marginBottom: 20 },

  barTrack: {
    width: "100%", height: 6, backgroundColor: "#1e293b",
    borderRadius: 3, overflow: "hidden", marginBottom: 12,
  },
  barFill: { height: "100%", backgroundColor: "#00d4ff", borderRadius: 3 },
  percentText: { color: "#e2e8f0", fontSize: 32, fontWeight: "700", marginBottom: 4 },
  progressDetail: { color: "#64748b", fontSize: 13, marginBottom: 20 },

  successIcon: { color: "#22c55e", fontSize: 40, marginBottom: 12 },
  errorIcon: { color: "#ef4444", fontSize: 40, marginBottom: 12 },
  errorMsg: { color: "#94a3b8", fontSize: 13, textAlign: "center", marginBottom: 20 },

  downloadBtn: {
    backgroundColor: "#00d4ff", borderRadius: 10,
    paddingVertical: 14, paddingHorizontal: 32, width: "100%", alignItems: "center",
  },
  downloadBtnText: { color: "#0a0f1e", fontSize: 16, fontWeight: "700" },

  cancelBtn: {
    borderWidth: 1, borderColor: "#334155", borderRadius: 10,
    paddingVertical: 12, paddingHorizontal: 32, width: "100%", alignItems: "center",
  },
  cancelBtnText: { color: "#94a3b8", fontSize: 14 },
});
