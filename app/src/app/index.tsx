import { useQuery } from '@tanstack/react-query';
import { Redirect, router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button, Field, Note, Ornament, Page, PasswordField, passwordLength } from '@/components/ui';
import { ApiError, apiGet } from '@/lib/api';
import { useSession } from '@/lib/session';
import type { AuthOptions } from '@/lib/types';
import { colors, themed, type } from '@/theme';

type Mode = 'sign-in' | 'sign-up' | 'dev';

/**
 * Screen 1: the title page. Sign in with email and password, or create an account (accounts are for adults; a child uses
 * a parent's). When the server has dev sign-in on (local development), an email-only sign-in is offered too.
 */
export default function TitlePage() {
  const { ready, user, signIn, signUp, devSignIn } = useSession();
  const [mode, setMode] = useState<Mode>('sign-in');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [ownerCode, setOwnerCode] = useState('');
  const [needsOwnerCode, setNeedsOwnerCode] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const health = useQuery({
    queryKey: ['health'],
    queryFn: () => apiGet<{ status: string }>('/health'),
    retry: false,
  });
  const options = useQuery({
    queryKey: ['auth', 'options'],
    queryFn: () => apiGet<AuthOptions>('/auth/options'),
    retry: false,
  });

  if (!ready) return null;
  if (user) return <Redirect href="/library" />;

  const min = options.data?.passwordMinLength ?? 15;
  const looksLikeEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
  const length = passwordLength(password);
  const canSubmit =
    looksLikeEmail &&
    (mode === 'dev' || (mode === 'sign-in' ? password.length > 0 : length >= min)) &&
    (!needsOwnerCode || ownerCode.trim().length > 0);

  function switchTo(next: Mode) {
    setMode(next);
    setError(null);
    setNeedsOwnerCode(false);
    setOwnerCode('');
  }

  async function submit() {
    if (!canSubmit || busy) return;
    setBusy(true);
    setError(null);
    try {
      if (mode === 'sign-in') await signIn(email.trim(), password);
      else if (mode === 'dev') await devSignIn(email.trim());
      else {
        await signUp({
          email: email.trim(),
          password,
          displayName: name.trim() || null,
          ownerSetupCode: needsOwnerCode ? ownerCode.trim() : null,
        });
      }
      router.replace('/library');
    } catch (e) {
      if (e instanceof ApiError && e.code === 'owner-setup-code') setNeedsOwnerCode(true);
      setError(e instanceof ApiError ? e.message : 'Sign-in failed. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  const heading = mode === 'sign-up' ? 'Create your account' : mode === 'dev' ? 'Developer sign-in' : 'Sign in';

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
        <Text style={styles.formTitle} accessibilityRole="header">
          {heading}
        </Text>
        {mode === 'sign-up' ? <Field label="Your name" hint="Optional." value={name} onChangeText={setName} maxLength={80} autoComplete="name" /> : null}
        <Field
          label="Email"
          hint={mode === 'dev' ? 'For local development only: no password needed.' : undefined}
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          autoComplete="email"
          autoCorrect={false}
          inputMode="email"
          keyboardType="email-address"
          returnKeyType={mode === 'dev' ? 'go' : 'next'}
          maxLength={254}
          onSubmitEditing={() => mode === 'dev' && void submit()}
        />
        {mode !== 'dev' ? (
          <PasswordField
            label="Password"
            isNew={mode === 'sign-up'}
            hint={
              mode === 'sign-up'
                ? `At least ${min} characters (${length} so far). Three or four random words work well, and spaces are fine.`
                : undefined
            }
            value={password}
            onChangeText={setPassword}
            maxLength={(options.data?.passwordMaxLength ?? 128) * 2}
            returnKeyType="go"
            onSubmitEditing={() => void submit()}
          />
        ) : null}
        {mode === 'sign-up' ? (
          <Text style={styles.adults}>
            Accounts are for adults. To let a child use yours, set their reading level in Settings and lock it with a
            parent PIN.
          </Text>
        ) : null}
        {needsOwnerCode ? (
          <PasswordField label="Owner setup code" hint="The code from the server’s settings, for the people who run LAA." value={ownerCode}
            onChangeText={setOwnerCode} autoComplete="off" textContentType="none" />
        ) : null}
        {error ? <Note tone="error">{error}</Note> : null}
        <Button
          label={mode === 'sign-up' ? 'Create account' : mode === 'dev' ? 'Sign in without a password' : 'Sign in'}
          onPress={() => void submit()}
          disabled={!canSubmit}
          busy={busy}
          style={styles.submit}
        />
        <View style={styles.switches}>
          {mode === 'sign-in' ? (
            <>
              <Button label="Forgotten your password?" variant="quiet" onPress={() => router.push('/forgot-password')} />
              <Button label="New here? Create an account" variant="quiet" onPress={() => switchTo('sign-up')} />
            </>
          ) : (
            <Button label="Have an account? Sign in" variant="quiet" onPress={() => switchTo('sign-in')} />
          )}
          {options.data?.devLogin && mode !== 'dev' ? (
            <Button label="Developer sign-in" variant="quiet" onPress={() => switchTo('dev')} />
          ) : null}
        </View>
      </View>

      <Text style={styles.status} accessibilityLiveRegion="polite">
        Server: {health.isPending ? 'checking…' : health.isSuccess ? 'connected' : 'not reachable'}
      </Text>
    </Page>
  );
}

const styles = themed(() => StyleSheet.create({
  titleBlock: { alignItems: 'center' },
  series: { ...type.kicker, letterSpacing: 2.4, marginBottom: 20 },
  headline: { ...type.h1, fontSize: 38, lineHeight: 46, textAlign: 'center' },
  pitch: { ...type.body, color: colors.muted, textAlign: 'center', marginBottom: 28 },
  form: { width: '100%', maxWidth: 380, alignSelf: 'center' },
  formTitle: { ...type.h2, marginBottom: 16, textAlign: 'center' },
  switches: { marginTop: 12, alignItems: 'center', gap: 4 },
  adults: { ...type.body, fontSize: 15, color: colors.muted, marginBottom: 16 },
  submit: { marginTop: 4 },
  status: { ...type.kicker, fontSize: 11, marginTop: 40, textAlign: 'center' },
}));
