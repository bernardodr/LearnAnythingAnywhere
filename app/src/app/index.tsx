import { useQuery } from '@tanstack/react-query';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { apiGet } from '@/lib/api';
import { colors, fonts, minTouch, type } from '@/theme';

/** Screen 1: the title page. Dev sign-in is wired up in M1. */
export default function TitlePage() {
  const health = useQuery({
    queryKey: ['health'],
    queryFn: () => apiGet<{ status: string }>('/health'),
    retry: false,
  });

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.page}>
        <View style={styles.column}>
          <Text style={styles.series} accessibilityRole="header">
            Learn Anything Anywhere
          </Text>
          <Text style={styles.headline}>A textbook for whatever you’re curious about</Text>

          <View style={styles.ornament} accessible={false}>
            <View style={styles.rule} />
            <Text style={styles.fleuron}>❦</Text>
            <View style={styles.rule} />
          </View>

          <Text style={styles.pitch}>
            Tell us what you want to learn and why. We’ll write you a textbook around your reason, one short
            lesson at a time, with a tutor who helps you find the answers yourself.
          </Text>

          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Sign in"
            accessibilityState={{ disabled: true }}
            disabled
            style={styles.button}>
            <Text style={styles.buttonText}>Sign in</Text>
          </Pressable>
          <Text style={styles.note}>Dev sign-in arrives in the next milestone.</Text>

          <Text style={styles.status} accessibilityLiveRegion="polite">
            Server:{' '}
            {health.isPending ? 'checking…' : health.isSuccess ? 'connected' : 'not reachable'}
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.paper },
  page: { flexGrow: 1, justifyContent: 'center', alignItems: 'center', padding: 32 },
  column: { maxWidth: 520, width: '100%', alignItems: 'center' },
  series: { ...type.kicker, fontSize: 12, letterSpacing: 2.4, marginBottom: 20 },
  headline: { ...type.h1, fontSize: 38, lineHeight: 46, textAlign: 'center' },
  ornament: { flexDirection: 'row', alignItems: 'center', marginVertical: 28, width: 200 },
  rule: { flex: 1, height: StyleSheet.hairlineWidth, backgroundColor: colors.rule },
  fleuron: { fontFamily: fonts.serif, fontSize: 18, color: colors.accent, marginHorizontal: 12 },
  pitch: { ...type.body, color: colors.muted, textAlign: 'center', marginBottom: 32 },
  button: {
    minHeight: minTouch,
    minWidth: 200,
    paddingHorizontal: 24,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 4,
    backgroundColor: colors.accent,
    opacity: 0.6,
  },
  buttonText: { fontFamily: fonts.sansSemiBold, fontSize: 15, color: colors.paper, letterSpacing: 0.5 },
  note: { fontFamily: fonts.sans, fontSize: 13, color: colors.muted, marginTop: 10 },
  status: { ...type.kicker, fontSize: 11, marginTop: 40 },
});
