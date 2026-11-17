import { useQuery } from '@tanstack/react-query';
import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button } from '@/components/ui';
import { ApiError, apiGet, apiPost } from '@/lib/api';
import { useSession } from '@/lib/session';
import type { User } from '@/lib/types';
import { colors, fonts } from '@/theme';

/**
 * On the shelf until the account's email is confirmed: starting a textbook waits until then. Rechecks whenever the
 * shelf comes back into view (the link may have been opened in another tab or on another device).
 */
export function ConfirmEmail() {
  const { token } = useSession();
  const me = useQuery({ queryKey: ['auth', 'me'], queryFn: () => apiGet<User>('/auth/me', token), enabled: !!token });
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const refetch = me.refetch;
  useFocusEffect(
    useCallback(() => {
      void refetch();
    }, [refetch]),
  );

  if (!me.data || me.data.emailVerified) return null;

  async function resend() {
    setBusy(true);
    setStatus(null);
    try {
      const res = await apiPost<{ sent: boolean }>('/auth/verify-email/resend', undefined, token);
      setStatus(res.sent ? 'Sent. Check your inbox (and spam folder) for the newest link.' : 'Your email is already confirmed.');
      if (!res.sent) void refetch();
    } catch (e) {
      setStatus(e instanceof ApiError ? e.message : 'That didn’t work. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.box} accessibilityRole="summary">
      <Text style={styles.title}>Confirm your email</Text>
      <Text style={styles.text}>
        We sent a link to {me.data.email}. You can look around, but starting a textbook waits until you open it.
      </Text>
      {status ? (
        <Text style={styles.text} accessibilityLiveRegion="polite">
          {status}
        </Text>
      ) : null}
      <Button label="Send the link again" variant="secondary" onPress={() => void resend()} busy={busy} style={styles.button} />
    </View>
  );
}

const styles = StyleSheet.create({
  box: { backgroundColor: colors.noteFill, borderLeftWidth: 3, borderLeftColor: colors.accent, borderRadius: 4, padding: 16, marginBottom: 20 },
  title: { fontFamily: fonts.serifSemiBold, fontSize: 18, color: colors.ink, marginBottom: 6 },
  text: { fontFamily: fonts.sans, fontSize: 15, lineHeight: 22, color: colors.ink, marginBottom: 8 },
  button: { alignSelf: 'flex-start', marginTop: 4 },
});
