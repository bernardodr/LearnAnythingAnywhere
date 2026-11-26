import { Inter_400Regular, Inter_500Medium, Inter_600SemiBold } from '@expo-google-fonts/inter';
import {
  SourceSerif4_400Regular,
  SourceSerif4_400Regular_Italic,
  SourceSerif4_600SemiBold,
  SourceSerif4_600SemiBold_Italic,
  useFonts,
} from '@expo-google-fonts/source-serif-4';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { router, Stack, usePathname } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { Platform, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { useAppearance } from '@/lib/appearance';
import { SessionProvider } from '@/lib/session';
import { colors, setScheme } from '@/theme';

SplashScreen.preventAutoHideAsync();

/**
 * The app's screens in the palette in use (issue #85): light or dark, from the learner's choice in settings or the
 * device's setting. The palette is switched before anything below draws, and a change redraws every screen (the
 * styles are made per palette; a redraw is how they're picked up). On phones the redraw starts the screens afresh,
 * so the learner is taken back to the screen they were on; on the web the address does that by itself.
 */
function Screens() {
  const { scheme, ready } = useAppearance();
  setScheme(scheme);
  const pathname = usePathname();
  const where = useRef(pathname);
  const drawnIn = useRef(scheme);
  useEffect(() => {
    if (drawnIn.current !== scheme) {
      drawnIn.current = scheme;
      if (Platform.OS !== 'web' && where.current && where.current !== '/') router.replace(where.current as never);
    } else {
      where.current = pathname;
    }
  }, [scheme, pathname]);
  if (!ready) return <View style={{ flex: 1, backgroundColor: colors.paper }} />;
  return (
    <>
      <StatusBar style={scheme === 'dark' ? 'light' : 'dark'} />
      <Stack key={scheme} screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.paper } }} />
    </>
  );
}

export default function RootLayout() {
  const [queryClient] = useState(() => new QueryClient());
  const [fontsLoaded, fontError] = useFonts({
    // The fonts DESIGN.md names, under the names theme.ts uses.
    SourceSerif4_400Regular,
    SourceSerif4_400Regular_Italic,
    SourceSerif4_600SemiBold,
    SourceSerif4_600SemiBold_Italic,
    Inter_400Regular,
    Inter_500Medium,
    Inter_600SemiBold,
  });

  useEffect(() => {
    if (fontsLoaded || fontError) SplashScreen.hideAsync();
  }, [fontsLoaded, fontError]);

  if (!fontsLoaded && !fontError) return null;

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <Screens />
        </SessionProvider>
      </QueryClientProvider>
    </GestureHandlerRootView>
  );
}
