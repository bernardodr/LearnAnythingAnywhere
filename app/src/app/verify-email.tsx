import { useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { Button, Kicker, Note, Page } from '@/components/ui';
import { apiPost } from '@/lib/api';
import { useSession } from '@/lib/session';
import { type } from '@/theme';

/** Opened from a verification email: confirms the address once, then points to the shelf or to sign in. */
export default function VerifyEmail() {
  const { token } = useLocalSearchParams<{ token?: string }>();
  const { ready, user } = useSession();
  const queryClient = useQueryClient();
  // A link works once, so this runs once: never refetched.
  const verify = useQuery({
    queryKey: ['verify-email', token],
    queryFn: async () => {
      const res = await apiPost<{ verified: boolean }>('/auth/verify-email', { token });
      await queryClient.invalidateQueries({ queryKey: ['auth', 'me'] });
      return res;
    },
    enabled: !!token,
    retry: false,
    staleTime: Infinity,
    gcTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });

  return (
    <Page center>
      <View style={styles.box}>
        <Kicker>Learn Anything Anywhere</Kicker>
        <Text style={styles.title} accessibilityRole="header">
          Confirming your email
        </Text>
        {!token ? <Note tone="error">This link is missing its code. Sign in and send a new one from your shelf.</Note> : null}
        {verify.isPending && token ? <Text style={styles.intro}>Just a moment…</Text> : null}
        {verify.isError ? <Note tone="error">{verify.error.message}</Note> : null}
        {verify.isSuccess ? <Note>Your email is confirmed. You can start a textbook now.</Note> : null}
        {ready && (verify.isSuccess || verify.isError || !token) ? (
          user ? (
            <Button label="Go to your shelf" onPress={() => router.replace('/library')} />
          ) : (
            <Button label="Sign in" onPress={() => router.replace('/')} />
          )
        ) : null}
      </View>
    </Page>
  );
}

const styles = StyleSheet.create({
  box: { width: '100%', maxWidth: 420, alignSelf: 'center' },
  title: { ...type.h1, marginTop: 8, marginBottom: 12 },
  intro: { ...type.body },
});
