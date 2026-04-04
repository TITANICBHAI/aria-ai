import {
  Inter_400Regular,
  Inter_500Medium,
  Inter_600SemiBold,
  Inter_700Bold,
  useFonts,
} from "@expo-google-fonts/inter";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Stack } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import React, { useEffect, useState } from "react";
import { ActivityIndicator, PermissionsAndroid, Platform, View } from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { KeyboardProvider } from "react-native-keyboard-controller";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { ErrorBoundary } from "@/components/ErrorBoundary";
import { FloatingChatBubble } from "@/components/FloatingChatBubble";
import { ModelDownloadScreen } from "@/components/ModelDownloadScreen";
import { AgentProvider } from "@/context/AgentContext";
import { AgentCoreBridge } from "@/native-bindings/AgentCoreBridge";

SplashScreen.preventAutoHideAsync().catch(() => {});

const queryClient = new QueryClient();

function RootLayoutNav() {
  return (
    <View style={{ flex: 1 }}>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      </Stack>
      <FloatingChatBubble />
    </View>
  );
}

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts({
    Inter_400Regular,
    Inter_500Medium,
    Inter_600SemiBold,
    Inter_700Bold,
  });

  const [modelReady, setModelReady] = useState<boolean | null>(null);

  useEffect(() => {
    if (fontsLoaded || fontError) {
      SplashScreen.hideAsync().catch(() => {});
    }
  }, [fontsLoaded, fontError]);

  // Request POST_NOTIFICATIONS at runtime on Android 13+ (API 33+).
  // This is required for foreground-service status alerts and download progress.
  useEffect(() => {
    if (Platform.OS !== "android") return;
    try {
      if (
        typeof PermissionsAndroid.request === "function" &&
        PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS
      ) {
        PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS
        ).catch(() => {});
      }
    } catch { /* API < 33 — permission not needed */ }
  }, []);

  useEffect(() => {
    if (!fontsLoaded && !fontError) return;
    if (Platform.OS !== "android") {
      setModelReady(true);
      return;
    }
    AgentCoreBridge.checkModelReady()
      .then((ready) => {
        setModelReady(ready);
      })
      .catch(() => {
        setModelReady(false);
      });
  }, [fontsLoaded, fontError]);

  if ((!fontsLoaded && !fontError) || modelReady === null) {
    return (
      <View style={{ flex: 1, backgroundColor: "#0a0f1e", justifyContent: "center", alignItems: "center" }}>
        <ActivityIndicator size="large" color="#6366f1" />
      </View>
    );
  }

  return (
    <SafeAreaProvider>
      <ErrorBoundary>
        <QueryClientProvider client={queryClient}>
          <AgentProvider>
            <GestureHandlerRootView style={{ flex: 1 }}>
              <KeyboardProvider>
                {modelReady ? (
                  <RootLayoutNav />
                ) : (
                  <ModelDownloadScreen onModelReady={() => setModelReady(true)} />
                )}
              </KeyboardProvider>
            </GestureHandlerRootView>
          </AgentProvider>
        </QueryClientProvider>
      </ErrorBoundary>
    </SafeAreaProvider>
  );
}
