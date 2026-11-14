import { useQuery } from '@tanstack/react-query';
import { Redirect, router } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Button, Field, Note, Ornament, Page } from '@/components/ui';
import { ApiError, apiGet } from '@/lib/api';
import { useSession } from '@/lib/session';
import { colors, fonts, minTouch, type } from '@/theme';

/** Screen 1: the title page, with dev sign-in (email only, no password; prototype). */
export default function TitlePage() {
  const { ready, user, signIn } = useSession();
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<'LEARNER' | 'PARENT'>('LEARNER');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const health = useQuery({
    queryKey: ['health'],
    queryFn: () => apiGet<{ status: string }>('/health'),
    retry: false,
  });

  if (!ready) return null;
  if (user) return <Redirect href="/library" />;

  const looksLikeEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await signIn(email.trim(), role);
      router.replace('/library');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Sign-in failed. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Page center>
      <View style={styles.titleBlock}>
        <Text style={styles.series} accessibilityRole="header">
          Learn Anything Anywhere
        </Text>
        <Text style={styles.headline}>A textbook for whatever you’re curious about</Text>
        <Ornament />
        <Text style={styles.pitch}>
          Tell us what you want to learn and why. We’ll write you a textbook around your reason, one short lesson
          at a time, with a tutor who helps you find the answers yourself.
        </Text>
      </View>

      <View style={styles.form}>
        <Field
          label="Email"
          hint="Prototype sign-in: no password needed."
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          autoComplete="email"
          autoCorrect={false}
          inputMode="email"
          keyboardType="email-address"
          returnKeyType="go"
          onSubmitEditing={() => looksLikeEmail && !busy && submit()}
        />
        <Text style={styles.label} nativeID="role-label">
          I’m signing in as
        </Text>
        <View style={styles.roles} accessibilityRole="radiogroup" aria-labelledby="role-label">
          {(['LEARNER', 'PARENT'] as const).map((r) => (
            <Pressable
              key={r}
              accessibilityRole="radio"
              accessibilityState={{ checked: role === r }}
              aria-checked={role === r}
              accessibilityLabel={r === 'LEARNER' ? 'A learner' : 'A parent'}
              onPress={() => setRole(r)}
              style={[styles.role, role === r && styles.roleOn]}>
              <Text style={[styles.roleText, role === r && styles.roleTextOn]}>{r === 'LEARNER' ? 'Learner' : 'Parent'}</Text>
            </Pressable>
          ))}
        </View>
        {error ? <Note tone="error">{error}</Note> : null}
        <Button label="Sign in" onPress={submit} disabled={!looksLikeEmail} busy={busy} style={styles.submit} />
      </View>

      <Text style={styles.status} accessibilityLiveRegion="polite">
        Server: {health.isPending ? 'checking…' : health.isSuccess ? 'connected' : 'not reachable'}
      </Text>
    </Page>
  );
}

const styles = StyleSheet.create({
  titleBlock: { alignItems: 'center' },
  series: { ...type.kicker, letterSpacing: 2.4, marginBottom: 20 },
  headline: { ...type.h1, fontSize: 38, lineHeight: 46, textAlign: 'center' },
  pitch: { ...type.body, color: colors.muted, textAlign: 'center', marginBottom: 28 },
  form: { width: '100%', maxWidth: 380, alignSelf: 'center' },
  label: { ...type.kicker, marginBottom: 8 },
  roles: { flexDirection: 'row', gap: 10, marginBottom: 20 },
  role: {
    flex: 1,
    minHeight: minTouch,
    borderWidth: 1,
    borderColor: colors.rule,
    borderRadius: 4,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
  },
  roleOn: { borderColor: colors.accent, backgroundColor: colors.noteFill },
  roleText: { fontFamily: fonts.sans, fontSize: 15, color: colors.ink },
  roleTextOn: { fontFamily: fonts.sansSemiBold, color: colors.accent },
  submit: { marginTop: 4 },
  status: { ...type.kicker, fontSize: 11, marginTop: 40, textAlign: 'center' },
});
