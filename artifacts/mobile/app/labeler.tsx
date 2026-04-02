/**
 * Object Labeler Screen
 *
 * Lets the user annotate UI elements on a captured screenshot.
 * Each annotation (pin) has a name, context, and element type.
 * The local LLM enriches annotations with meaning, interaction hints,
 * and reasoning context — which are then injected into every future
 * agent prompt when the same screen is observed.
 *
 * Flow:
 *   1. Press "Capture Screen" — grabs the current foreground app screen
 *   2. Tap anywhere on the screenshot image to place a pin
 *   3. Fill in the pin details in the editor panel below
 *   4. Press "Enrich All" — LLM generates semantic metadata for every pin
 *   5. Press "Save" — persists to SQLite, wired into AgentLoop's prompt builder
 */

import React, { useCallback, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Image,
  Platform,
  Pressable,
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
import { router } from "expo-router";

import { useColors } from "@/hooks/useColors";
import {
  AgentCoreBridge,
  DetectedObject,
  ElementType,
  ObjectLabel,
  ScreenCapture,
} from "@/native-bindings/AgentCoreBridge";

const ELEMENT_TYPES: ElementType[] = [
  "button", "input", "text", "toggle",
  "link", "icon", "image", "container", "unknown",
];

/**
 * Infer a rough ElementType from a COCO detection label.
 * Used to pre-fill the element type when Auto-detect creates pins.
 */
function inferElementType(cocoLabel: string): ElementType {
  const l = cocoLabel.toLowerCase();
  if (l.includes("cell phone") || l.includes("remote") || l.includes("keyboard") || l.includes("mouse")) return "button";
  if (l.includes("book") || l.includes("laptop") || l.includes("tv") || l.includes("monitor")) return "image";
  if (l.includes("person") || l.includes("face")) return "icon";
  return "unknown";
}

function makeLabel(
  appPackage: string,
  screenHash: string,
  x: number,
  y: number
): ObjectLabel {
  return {
    id: `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    appPackage,
    screenHash,
    x,
    y,
    name: "",
    context: "",
    elementType: "button",
    ocrText: "",
    meaning: "",
    interactionHint: "",
    reasoningContext: "",
    importanceScore: 5,
    additionalFields: {},
    isEnriched: false,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  };
}

export default function LabelerScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();

  const [capture, setCapture] = useState<ScreenCapture | null>(null);
  const [labels, setLabels] = useState<ObjectLabel[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [capturing, setCapturing] = useState(false);
  const [detecting, setDetecting] = useState(false);
  const [enriching, setEnriching] = useState(false);
  const [saving, setSaving] = useState(false);
  const [imgSize, setImgSize] = useState({ width: 1, height: 1 });

  const selectedLabel = labels.find((l) => l.id === selectedId) ?? null;

  const updateLabel = useCallback((id: string, patch: Partial<ObjectLabel>) => {
    setLabels((prev) =>
      prev.map((l) => (l.id === id ? { ...l, ...patch, updatedAt: Date.now() } : l))
    );
  }, []);

  const handleCapture = async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    setCapturing(true);
    try {
      const result = await AgentCoreBridge.captureScreenForLabeling();
      const existing = await AgentCoreBridge.getObjectLabels(
        result.appPackage,
        result.screenHash
      );
      setCapture(result);
      setLabels(existing);
      setSelectedId(null);
    } catch (e: any) {
      Alert.alert("Capture Failed", e?.message ?? "Could not capture screen.");
    } finally {
      setCapturing(false);
    }
  };

  const handleImageTap = (evt: any) => {
    if (!capture) return;
    const { locationX, locationY } = evt.nativeEvent;
    const { width, height } = imgSize;
    const normX = Math.max(0, Math.min(1, locationX / width));
    const normY = Math.max(0, Math.min(1, locationY / height));
    const newLabel = makeLabel(capture.appPackage, capture.screenHash, normX, normY);
    setLabels((prev) => [...prev, newLabel]);
    setSelectedId(newLabel.id);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  };

  const handleDelete = (id: string) => {
    setLabels((prev) => prev.filter((l) => l.id !== id));
    if (selectedId === id) setSelectedId(null);
    AgentCoreBridge.deleteObjectLabel(id).catch(() => {});
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
  };

  const handleEnrichAll = async () => {
    if (!capture) return;
    if (labels.length === 0) {
      Alert.alert("No pins", "Place at least one pin on the screenshot first.");
      return;
    }
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
    setEnriching(true);
    try {
      const screenContext = `${capture.ocrText}\n${capture.a11yTree}`;
      const enriched = await AgentCoreBridge.enrichLabelsWithLLM(labels, screenContext);
      setLabels(enriched);
    } catch (e: any) {
      Alert.alert("Enrichment Failed", e?.message ?? "LLM enrichment error.");
    } finally {
      setEnriching(false);
    }
  };

  const handleSave = async () => {
    if (!capture) return;
    if (labels.some((l) => !l.name.trim())) {
      Alert.alert("Missing names", "All pins must have a name before saving.");
      return;
    }
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    setSaving(true);
    try {
      await AgentCoreBridge.saveObjectLabels(
        capture.appPackage,
        capture.screenHash,
        labels
      );
      Alert.alert(
        "Saved",
        `${labels.length} label${labels.length !== 1 ? "s" : ""} saved. The agent will use these on future observations.`,
        [{ text: "Done", onPress: () => router.back() }]
      );
    } catch (e: any) {
      Alert.alert("Save Failed", e?.message ?? "Could not save labels.");
    } finally {
      setSaving(false);
    }
  };

  // ── Auto-detect: run MediaPipe EfficientDet-Lite0 on the captured screenshot ──
  // Places pins at detected bounding box centers; pre-fills name from COCO category.
  // Model downloads once in background (~4.4MB) on first use.
  const handleAutoDetect = async () => {
    if (!capture?.imageUri) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
    setDetecting(true);
    try {
      const isReady = await AgentCoreBridge.isDetectorModelReady();
      if (!isReady) {
        const ok = await AgentCoreBridge.downloadDetectorModel();
        if (!ok) {
          Alert.alert(
            "Download Failed",
            "Could not download the object detection model (~4.4 MB). Check your connection and try again."
          );
          return;
        }
      }
      const detections = await AgentCoreBridge.detectObjectsInImage(capture.imageUri);
      if (detections.length === 0) {
        Alert.alert(
          "No Objects Detected",
          "EfficientDet found no UI elements above 40% confidence. Try adding pins manually."
        );
        return;
      }
      const newLabels: ObjectLabel[] = detections.map((det) => ({
        ...makeLabel(capture.appPackage, capture.screenHash, det.normX, det.normY),
        name: det.label.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
        elementType: inferElementType(det.label),
        importanceScore: Math.max(1, Math.min(10, Math.round(det.confidence * 10))),
      }));
      setLabels((prev) => [...prev, ...newLabels]);
      Alert.alert(
        `${detections.length} Object${detections.length !== 1 ? "s" : ""} Detected`,
        "Pins placed at detected elements. Review names, then tap Enrich All to add semantic context."
      );
    } catch (e: any) {
      Alert.alert("Detection Failed", e?.message ?? "Auto-detect error.");
    } finally {
      setDetecting(false);
    }
  };


  const topPad = Platform.OS === "web" ? 67 : insets.top;

  return (
    <View style={[styles.root, { backgroundColor: colors.background }]}>
      {/* ── Header ──────────────────────────────────────────────────────────── */}
      <View
        style={[
          styles.header,
          { paddingTop: topPad + 12, backgroundColor: colors.surface1, borderBottomColor: colors.border },
        ]}
      >
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Feather name="arrow-left" size={22} color={colors.foreground} />
        </TouchableOpacity>
        <View style={styles.headerCenter}>
          <Text style={[styles.headerTitle, { color: colors.foreground }]}>
            Object Labeler
          </Text>
          <Text style={[styles.headerSub, { color: colors.mutedForeground }]}>
            {capture
              ? `${capture.appPackage.split(".").pop()} · ${labels.length} pin${labels.length !== 1 ? "s" : ""}`
              : "Teach the agent about UI elements"}
          </Text>
        </View>
        <TouchableOpacity
          onPress={handleCapture}
          disabled={capturing}
          style={[styles.captureBtn, { backgroundColor: colors.primary }]}
        >
          {capturing ? (
            <ActivityIndicator size="small" color={colors.primaryForeground} />
          ) : (
            <Feather name="camera" size={18} color={colors.primaryForeground} />
          )}
        </TouchableOpacity>
      </View>

      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={[
          styles.body,
          { paddingBottom: insets.bottom + 20 },
        ]}
        keyboardShouldPersistTaps="handled"
      >
        {/* ── Screenshot + Pins ──────────────────────────────────────────── */}
        {capture ? (
          <View style={styles.screenshotContainer}>
            <Pressable
              onPress={handleImageTap}
              onLayout={(e) => {
                const { width, height } = e.nativeEvent.layout;
                setImgSize({ width, height });
              }}
              style={styles.screenshotWrapper}
            >
              {capture.imageUri ? (
                <Image
                  source={{ uri: `file://${capture.imageUri}` }}
                  style={styles.screenshot}
                  resizeMode="contain"
                />
              ) : (
                <View
                  style={[
                    styles.screenshot,
                    styles.screenshotPlaceholder,
                    { backgroundColor: colors.surface2 },
                  ]}
                >
                  <Feather name="monitor" size={48} color={colors.mutedForeground} />
                  <Text style={[styles.placeholderText, { color: colors.mutedForeground }]}>
                    Web preview — tap to place pins
                  </Text>
                </View>
              )}

              {/* Pins */}
              {labels.map((label) => (
                <PinMarker
                  key={label.id}
                  label={label}
                  isSelected={label.id === selectedId}
                  containerSize={imgSize}
                  colors={colors}
                  onPress={() => setSelectedId(label.id === selectedId ? null : label.id)}
                />
              ))}
            </Pressable>

            <Text style={[styles.tapHint, { color: colors.mutedForeground }]}>
              Tap the screenshot to place a pin · tap a pin to edit
            </Text>
          </View>
        ) : (
          <EmptyState
            colors={colors}
            capturing={capturing}
            onCapture={handleCapture}
          />
        )}

        {/* ── Pin Editor ─────────────────────────────────────────────────── */}
        {selectedLabel && (
          <PinEditor
            label={selectedLabel}
            colors={colors}
            onChange={(patch) => updateLabel(selectedLabel.id, patch)}
            onDelete={() => handleDelete(selectedLabel.id)}
          />
        )}

        {/* ── Saved Labels List (collapsed) ──────────────────────────────── */}
        {labels.length > 0 && !selectedLabel && (
          <LabelList
            labels={labels}
            colors={colors}
            onSelect={(id) => setSelectedId(id)}
            onDelete={handleDelete}
          />
        )}

        {/* ── Actions ────────────────────────────────────────────────────── */}
        {capture && (
          <View style={styles.actions}>
            {/* Auto-detect: MediaPipe EfficientDet-Lite0 INT8 — places pins at visual element centers */}
            <TouchableOpacity
              onPress={handleAutoDetect}
              disabled={detecting || enriching || saving}
              style={[
                styles.actionBtn,
                {
                  backgroundColor: detecting
                    ? colors.surface2
                    : colors.secondary + "22",
                  borderColor: colors.secondary + "55",
                  flex: 1,
                },
              ]}
            >
              {detecting ? (
                <ActivityIndicator size="small" color={colors.mutedForeground} />
              ) : (
                <Feather name="aperture" size={16} color={colors.mutedForeground} />
              )}
              <Text style={[styles.actionBtnText, { color: colors.mutedForeground }]}>
                {detecting ? "Detecting…" : "Auto-detect"}
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              onPress={handleEnrichAll}
              disabled={enriching || saving || labels.length === 0}
              style={[
                styles.actionBtn,
                {
                  backgroundColor: enriching
                    ? colors.surface2
                    : colors.accent + "22",
                  borderColor: colors.accent + "55",
                  flex: 1,
                },
              ]}
            >
              {enriching ? (
                <ActivityIndicator size="small" color={colors.accent} />
              ) : (
                <Feather name="cpu" size={16} color={colors.accent} />
              )}
              <Text style={[styles.actionBtnText, { color: colors.accent }]}>
                {enriching ? "Enriching…" : "Enrich All"}
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              onPress={handleSave}
              disabled={saving || enriching || labels.length === 0}
              style={[
                styles.actionBtn,
                {
                  backgroundColor: saving ? colors.surface2 : colors.primary,
                  flex: 1,
                },
              ]}
            >
              {saving ? (
                <ActivityIndicator size="small" color={colors.primaryForeground} />
              ) : (
                <Feather name="save" size={16} color={colors.primaryForeground} />
              )}
              <Text
                style={[
                  styles.actionBtnText,
                  { color: saving ? colors.mutedForeground : colors.primaryForeground },
                ]}
              >
                {saving ? "Saving…" : "Save Labels"}
              </Text>
            </TouchableOpacity>
          </View>
        )}

        {/* ── Context Preview ─────────────────────────────────────────────── */}
        {capture && (capture.ocrText || capture.a11yTree) && (
          <ContextPreview capture={capture} colors={colors} />
        )}
      </ScrollView>
    </View>
  );
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function PinMarker({
  label, isSelected, containerSize, colors, onPress,
}: {
  label: ObjectLabel;
  isSelected: boolean;
  containerSize: { width: number; height: number };
  colors: any;
  onPress: () => void;
}) {
  const left = label.x * containerSize.width - 14;
  const top  = label.y * containerSize.height - 14;
  return (
    <TouchableOpacity
      onPress={onPress}
      style={[
        styles.pin,
        {
          left,
          top,
          backgroundColor: isSelected
            ? colors.primary
            : label.isEnriched
            ? colors.success
            : colors.accent,
          borderColor: colors.background,
        },
      ]}
    >
      <Text style={styles.pinText}>
        {label.name ? label.name.charAt(0).toUpperCase() : "?"}
      </Text>
    </TouchableOpacity>
  );
}

function PinEditor({
  label, colors, onChange, onDelete,
}: {
  label: ObjectLabel;
  colors: any;
  onChange: (patch: Partial<ObjectLabel>) => void;
  onDelete: () => void;
}) {
  return (
    <View
      style={[
        styles.editor,
        { backgroundColor: colors.surface1, borderColor: colors.border },
      ]}
    >
      <View style={styles.editorHeader}>
        <Text style={[styles.editorTitle, { color: colors.foreground }]}>
          Edit Pin
        </Text>
        <TouchableOpacity onPress={onDelete}>
          <Feather name="trash-2" size={18} color={colors.destructive} />
        </TouchableOpacity>
      </View>

      {/* Name */}
      <Text style={[styles.fieldLabel, { color: colors.mutedForeground }]}>
        Element Name *
      </Text>
      <TextInput
        value={label.name}
        onChangeText={(v) => onChange({ name: v })}
        placeholder="e.g. Checkout Button"
        placeholderTextColor={colors.mutedForeground}
        style={[
          styles.textInput,
          {
            color: colors.foreground,
            backgroundColor: colors.surface2,
            borderColor: colors.border,
          },
        ]}
      />

      {/* Context */}
      <Text style={[styles.fieldLabel, { color: colors.mutedForeground }]}>
        Purpose / Context
      </Text>
      <TextInput
        value={label.context}
        onChangeText={(v) => onChange({ context: v })}
        placeholder="What does this element do?"
        placeholderTextColor={colors.mutedForeground}
        multiline
        numberOfLines={2}
        style={[
          styles.textInput,
          styles.textInputMulti,
          {
            color: colors.foreground,
            backgroundColor: colors.surface2,
            borderColor: colors.border,
          },
        ]}
      />

      {/* Element Type */}
      <Text style={[styles.fieldLabel, { color: colors.mutedForeground }]}>
        Element Type
      </Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.typeScroll}>
        {ELEMENT_TYPES.map((t) => (
          <TouchableOpacity
            key={t}
            onPress={() => onChange({ elementType: t })}
            style={[
              styles.typeChip,
              {
                backgroundColor:
                  label.elementType === t ? colors.primary : colors.surface2,
                borderColor:
                  label.elementType === t ? colors.primary : colors.border,
              },
            ]}
          >
            <Text
              style={[
                styles.typeChipText,
                {
                  color:
                    label.elementType === t
                      ? colors.primaryForeground
                      : colors.mutedForeground,
                },
              ]}
            >
              {t}
            </Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {/* Importance score */}
      <Text style={[styles.fieldLabel, { color: colors.mutedForeground }]}>
        Importance: {label.importanceScore}/10
      </Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.typeScroll}>
        {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((n) => (
          <TouchableOpacity
            key={n}
            onPress={() => onChange({ importanceScore: n })}
            style={[
              styles.scoreChip,
              {
                backgroundColor:
                  label.importanceScore === n ? colors.accent : colors.surface2,
                borderColor:
                  label.importanceScore === n ? colors.accent : colors.border,
              },
            ]}
          >
            <Text
              style={[
                styles.typeChipText,
                {
                  color:
                    label.importanceScore === n
                      ? colors.primaryForeground
                      : colors.mutedForeground,
                },
              ]}
            >
              {n}
            </Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {/* LLM-generated fields (read-only, shown after enrichment) */}
      {label.isEnriched && (
        <View
          style={[
            styles.enrichedBox,
            { backgroundColor: colors.success + "12", borderColor: colors.success + "33" },
          ]}
        >
          <View style={styles.enrichedHeader}>
            <Feather name="star" size={13} color={colors.success} />
            <Text style={[styles.enrichedTitle, { color: colors.success }]}>
              LLM Enriched
            </Text>
          </View>
          {label.meaning && (
            <EnrichedField label="Meaning" value={label.meaning} colors={colors} />
          )}
          {label.interactionHint && (
            <EnrichedField label="Agent hint" value={label.interactionHint} colors={colors} />
          )}
          {label.reasoningContext && (
            <EnrichedField label="Prompt note" value={label.reasoningContext} colors={colors} />
          )}
        </View>
      )}
    </View>
  );
}

function EnrichedField({
  label, value, colors,
}: { label: string; value: string; colors: any }) {
  return (
    <View style={styles.enrichedField}>
      <Text style={[styles.enrichedFieldLabel, { color: colors.mutedForeground }]}>
        {label}:
      </Text>
      <Text style={[styles.enrichedFieldValue, { color: colors.foreground }]}>
        {value}
      </Text>
    </View>
  );
}

function LabelList({
  labels, colors, onSelect, onDelete,
}: {
  labels: ObjectLabel[];
  colors: any;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
}) {
  return (
    <View style={styles.labelList}>
      <Text style={[styles.listTitle, { color: colors.mutedForeground }]}>
        Pinned Elements
      </Text>
      {labels.map((label) => (
        <TouchableOpacity
          key={label.id}
          onPress={() => onSelect(label.id)}
          style={[
            styles.labelRow,
            { backgroundColor: colors.surface1, borderColor: colors.border },
          ]}
        >
          <View
            style={[
              styles.labelDot,
              {
                backgroundColor: label.isEnriched ? colors.success : colors.accent,
              },
            ]}
          />
          <View style={{ flex: 1 }}>
            <Text style={[styles.labelRowName, { color: colors.foreground }]}>
              {label.name || "(unnamed)"}
            </Text>
            <Text style={[styles.labelRowSub, { color: colors.mutedForeground }]}>
              {label.elementType} · importance {label.importanceScore}/10
              {label.isEnriched ? " · ★ enriched" : ""}
            </Text>
          </View>
          <TouchableOpacity
            onPress={() => onDelete(label.id)}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Feather name="x" size={16} color={colors.mutedForeground} />
          </TouchableOpacity>
        </TouchableOpacity>
      ))}
    </View>
  );
}

function EmptyState({
  colors, capturing, onCapture,
}: { colors: any; capturing: boolean; onCapture: () => void }) {
  return (
    <TouchableOpacity
      onPress={onCapture}
      disabled={capturing}
      style={[
        styles.emptyState,
        { backgroundColor: colors.surface1, borderColor: colors.border },
      ]}
    >
      {capturing ? (
        <ActivityIndicator size="large" color={colors.primary} />
      ) : (
        <Feather name="camera" size={40} color={colors.mutedForeground} />
      )}
      <Text style={[styles.emptyTitle, { color: colors.foreground }]}>
        {capturing ? "Capturing screen…" : "Capture a Screen to Label"}
      </Text>
      <Text style={[styles.emptySub, { color: colors.mutedForeground }]}>
        Switch to the app you want to annotate, then come back and press Capture.
        The agent will learn from every pin you place.
      </Text>
    </TouchableOpacity>
  );
}

function ContextPreview({
  capture, colors,
}: { capture: ScreenCapture; colors: any }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <View
      style={[
        styles.contextBox,
        { backgroundColor: colors.surface1, borderColor: colors.border },
      ]}
    >
      <TouchableOpacity
        onPress={() => setExpanded((v) => !v)}
        style={styles.contextHeader}
      >
        <Feather name="eye" size={14} color={colors.mutedForeground} />
        <Text style={[styles.contextTitle, { color: colors.mutedForeground }]}>
          Screen Context (used by LLM enrichment)
        </Text>
        <Feather
          name={expanded ? "chevron-up" : "chevron-down"}
          size={14}
          color={colors.mutedForeground}
        />
      </TouchableOpacity>
      {expanded && (
        <Text
          style={[styles.contextText, { color: colors.foreground }]}
          numberOfLines={20}
        >
          {capture.ocrText || capture.a11yTree || "(no context available)"}
        </Text>
      )}
    </View>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: {
    flexDirection: "row",
    alignItems: "flex-end",
    paddingHorizontal: 16,
    paddingBottom: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    gap: 12,
  },
  backBtn: { padding: 4, marginBottom: 2 },
  headerCenter: { flex: 1 },
  headerTitle: { fontSize: 17, fontFamily: "Inter_700Bold" },
  headerSub: { fontSize: 12, fontFamily: "Inter_400Regular", marginTop: 2 },
  captureBtn: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 2,
  },

  body: { padding: 16, gap: 16 },

  screenshotContainer: { gap: 6 },
  screenshotWrapper: {
    width: "100%",
    aspectRatio: 9 / 19,
    borderRadius: 14,
    overflow: "hidden",
    position: "relative",
  },
  screenshot: { width: "100%", height: "100%" },
  screenshotPlaceholder: {
    alignItems: "center",
    justifyContent: "center",
    gap: 12,
  },
  placeholderText: {
    fontSize: 13,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
    paddingHorizontal: 20,
  },
  tapHint: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
    opacity: 0.7,
  },

  pin: {
    position: "absolute",
    width: 28,
    height: 28,
    borderRadius: 14,
    borderWidth: 2,
    alignItems: "center",
    justifyContent: "center",
    zIndex: 10,
  },
  pinText: {
    fontSize: 12,
    fontFamily: "Inter_700Bold",
    color: "#fff",
  },

  editor: {
    borderRadius: 14,
    borderWidth: 1,
    padding: 16,
    gap: 8,
  },
  editorHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 4,
  },
  editorTitle: { fontSize: 15, fontFamily: "Inter_600SemiBold" },

  fieldLabel: { fontSize: 12, fontFamily: "Inter_500Medium" },
  textInput: {
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    fontFamily: "Inter_400Regular",
  },
  textInputMulti: { minHeight: 56, textAlignVertical: "top" },

  typeScroll: { flexGrow: 0 },
  typeChip: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 6,
    marginRight: 6,
  },
  scoreChip: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
    marginRight: 6,
    minWidth: 32,
    alignItems: "center",
  },
  typeChipText: { fontSize: 12, fontFamily: "Inter_500Medium" },

  enrichedBox: {
    borderRadius: 10,
    borderWidth: 1,
    padding: 12,
    gap: 6,
    marginTop: 4,
  },
  enrichedHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginBottom: 4,
  },
  enrichedTitle: { fontSize: 12, fontFamily: "Inter_600SemiBold" },
  enrichedField: { gap: 2 },
  enrichedFieldLabel: { fontSize: 11, fontFamily: "Inter_500Medium" },
  enrichedFieldValue: { fontSize: 13, fontFamily: "Inter_400Regular", lineHeight: 18 },

  labelList: { gap: 8 },
  listTitle: { fontSize: 12, fontFamily: "Inter_500Medium", marginBottom: 2 },
  labelRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
  },
  labelDot: { width: 10, height: 10, borderRadius: 5 },
  labelRowName: { fontSize: 14, fontFamily: "Inter_500Medium" },
  labelRowSub: { fontSize: 11, fontFamily: "Inter_400Regular", marginTop: 1 },

  actions: {
    flexDirection: "row",
    gap: 12,
  },
  actionBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingVertical: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: "transparent",
  },
  actionBtnText: { fontSize: 14, fontFamily: "Inter_600SemiBold" },

  emptyState: {
    borderRadius: 16,
    borderWidth: 1,
    borderStyle: "dashed",
    alignItems: "center",
    paddingVertical: 48,
    paddingHorizontal: 24,
    gap: 12,
  },
  emptyTitle: { fontSize: 17, fontFamily: "Inter_600SemiBold", textAlign: "center" },
  emptySub: {
    fontSize: 13,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
    lineHeight: 20,
  },

  contextBox: {
    borderRadius: 12,
    borderWidth: 1,
    overflow: "hidden",
  },
  contextHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    padding: 12,
  },
  contextTitle: { flex: 1, fontSize: 12, fontFamily: "Inter_500Medium" },
  contextText: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
    padding: 12,
    paddingTop: 0,
    lineHeight: 17,
  },
});
