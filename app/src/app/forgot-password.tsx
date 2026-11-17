import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button, Field, Kicker, Note, Page } from '@/components/ui';
import { ApiError, apiPost } from '@/lib/api';
import { type } from '@/theme';

/** Asks for a password-reset link. The answer is the same whether or not the email has an account. */
export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(null);
  const looksLikeEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());

  async function send() {
    if (!looksLikeEmail || busy) return;
    setBusy(true);
    setStatus(null);
    try {
      const res = await apiPost<{ message: string }>('/auth/password-reset', { email: email.trim() });
      setStatus({ ok: true, text: res.message });
    } catch (e) {
      setStatus({ ok: false, text: e instanceof ApiError ? e.message : 'That didn’t work. Please try again.' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Page center>
      <View style={styles.form}>
        <Kicker>Learn Anything Anywhere</Kicker>
        <Text style={styles.title} accessibilityRole="header">
          Forgotten your password?
        </Text>
        <Text style={styles.intro}>
          Type the email you sign in with. We’ll send it a link to choose a new password.
        </Text>
        <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" autoComplete="email" autoCorrect={false}
          inputMode="email" keyboardType="email-address" maxLength={254} returnKeyType="send" onSubmitEditing={() => void send()} />
        {status ? <Note tone={status.ok ? 'note' : 'error'}>{status.text}</Note> : null}
        <Button label="Send reset link" onPress={() => void send()} disabled={!looksLikeEmail} busy={busy} />
        <Text style={styles.small}>A child’s password is reset by their parent, in Settings.</Text>
        <Button label="Back to sign in" variant="quiet" onPress={() => router.replace('/')} style={styles.back} />
      </View>
    </Page>
  );
}

const styles = StyleSheet.create({
  form: { width: '100%', maxWidth: 380, alignSelf: 'center' },
  title: { ...type.h1, marginTop: 8, marginBottom: 12 },
  intro: { ...type.body, marginBottom: 20 },
  small: { ...type.body, fontSize: 14, marginTop: 16 },
  back: { alignSelf: 'center', marginTop: 8 },
});
