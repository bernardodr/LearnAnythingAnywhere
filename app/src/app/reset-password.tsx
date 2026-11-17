import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button, Kicker, Note, Page, PasswordField, passwordLength } from '@/components/ui';
import { ApiError, apiPost } from '@/lib/api';
import { useSession } from '@/lib/session';
import type { SignInResponse } from '@/lib/types';
import { type } from '@/theme';

const MIN_PASSWORD = 15;

/** Opened from a reset email: choose a new password, which signs you in here and out everywhere else. */
export default function ResetPassword() {
  const { token } = useLocalSearchParams<{ token?: string }>();
  const { renew } = useSession();
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const length = passwordLength(password);

  async function save() {
    if (length < MIN_PASSWORD || busy) return;
    setBusy(true);
    setError(null);
    try {
      const res = await apiPost<SignInResponse>('/auth/password-reset/confirm', { token, newPassword: password });
      await renew(res);
      router.replace('/library');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'The password couldn’t be saved. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Page center>
      <View style={styles.form}>
        <Kicker>Learn Anything Anywhere</Kicker>
        <Text style={styles.title} accessibilityRole="header">
          Choose a new password
        </Text>
        {!token ? (
          <>
            <Note tone="error">This link is missing its code. Ask for a new one.</Note>
            <Button label="Ask for a new link" onPress={() => router.replace('/forgot-password')} />
          </>
        ) : (
          <>
            <Text style={styles.intro}>Saving it signs you in here and out everywhere else.</Text>
            <PasswordField label="New password" isNew value={password} onChangeText={setPassword} returnKeyType="go"
              onSubmitEditing={() => void save()}
              hint={`At least ${MIN_PASSWORD} characters (${length} so far). Three or four random words work well, and spaces are fine.`} />
            {error ? <Note tone="error">{error}</Note> : null}
            <Button label="Save new password" onPress={() => void save()} disabled={length < MIN_PASSWORD} busy={busy} />
          </>
        )}
        <Button label="Back to sign in" variant="quiet" onPress={() => router.replace('/')} style={styles.back} />
      </View>
    </Page>
  );
}

const styles = StyleSheet.create({
  form: { width: '100%', maxWidth: 380, alignSelf: 'center' },
  title: { ...type.h1, marginTop: 8, marginBottom: 12 },
  intro: { ...type.body, marginBottom: 20 },
  back: { alignSelf: 'center', marginTop: 16 },
});
