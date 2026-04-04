import React from "react";
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  ScrollViewProps,
  StyleSheet,
} from "react-native";

type Props = ScrollViewProps & {
  keyboardShouldPersistTaps?: "handled" | "always" | "never";
};

export function KeyboardAwareScrollViewCompat({
  children,
  keyboardShouldPersistTaps = "handled",
  style,
  contentContainerStyle,
  ...props
}: Props) {
  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === "ios" ? "padding" : "height"}
    >
      <ScrollView
        keyboardShouldPersistTaps={keyboardShouldPersistTaps}
        style={style}
        contentContainerStyle={contentContainerStyle}
        {...props}
      >
        {children}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
});
