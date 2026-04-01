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
import { Platform } from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { KeyboardProvider } from "react-native-keyboard-controller";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { ErrorBoundary } from "@/components/ErrorBoundary";
import { AgentProvider } from "@/context/AgentContext";
import { AgentCoreBridge } from "@/native-bindings/AgentCoreBridge";
import { ModelDownloadScreen } from "@/components/ModelDownloadScreen";

SplashScreen.preventAutoHideAsync();

const queryClient = new QueryClient();

function RootLayoutNav() {
  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
    </Stack>
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
      SplashScreen.hideAsync();
    }
  }, [fontsLoaded, fontError]);

  useEffect(() => {
    if (!fontsLoaded && !fontError) return;
    if (Platform.OS !== "android") {
      setModelReady(true);
      return;
    }
    AgentCoreBridge.checkModelReady().then((ready) => {
      setModelReady(ready);
    });
  }, [fontsLoaded, fontError]);

  if (!fontsLoaded && !fontError) return null;
  if (modelReady === null) return null;

  return (
    <SafeAreaProvider>
      <ErrorBoundary>
        <QueryClientProvider client={queryClient}>
          <AgentProvider>
            <GestureHandlerRootView>
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
