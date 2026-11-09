import { useQueryClient } from '@tanstack/react-query';
import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button, Field, goBack, Kicker, Note, Ornament, Page } from '@/components/ui';
import { ApiError, apiPost } from '@/lib/api';
import { useSession } from '@/lib/session';
import type { CreateTextbookResponse } from '@/lib/types';
import { colors, type } from '@/theme';

/** New textbook: the topic, then the topic gate (BRIEF section 8). A decline creates nothing. */
export default function NewTextbook() {
  const { ready, user, token } = useSession();
  const params = useLocalSearchParams<{ topic?: string }>();
  const queryClient = useQueryClient();
  const [topic, setTopic] = useState(params.topic ?? '');
  const [busy, setBusy] = useState(false);
  const [declined, setDeclined] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!ready) return null;
  if (!user) return <Redirect href="/" />;

  async function submit() {
    setBusy(true);
    setError(null);
    setDeclined(null);
    try {
      const res = await apiPost<CreateTextbookResponse>('/textbooks', { topic: topic.trim() }, token);
      if (!res.textbook) {
        setDeclined(res.message);
        return;
      }
      await queryClient.invalidateQueries({ queryKey: ['textbooks'] });
      router.replace({ pathname: '/textbooks/[id]/preface', params: { id: res.textbook.id } });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Page>
      <Button label="‹ Your shelf" variant="quiet" onPress={goBack} style={styles.back}
        accessibilityLabel="Back to your shelf" />
      <Kicker>A new textbook</Kicker>
      <Text style={styles.title} accessibilityRole="header">
        What would you like to learn?
      </Text>
      <Ornament />
      <Field
        label="Topic"
        hint="A skill, a subject, a hobby: anything you’re curious about."
        value={topic}
        onChangeText={(t) => {
          setTopic(t);
          setDeclined(null);
        }}
        maxLength={120}
        autoFocus={!params.topic}
        returnKeyType="go"
        onSubmitEditing={() => topic.trim() && !busy && submit()}
      />
      {declined ? (
        <View accessibilityLiveRegion="polite">
          <Note>{declined}</Note>
        </View>
      ) : null}
      {error ? <Note tone="error">{error}</Note> : null}
      <Button label="Begin the preface" onPress={submit} disabled={!topic.trim()} busy={busy} />
      <Text style={styles.next}>
        Next, a short interview about why you want to learn this. Your textbook is written around your answer.
      </Text>
    </Page>
  );
}

const styles = StyleSheet.create({
  back: { alignSelf: 'flex-start', marginLeft: -8, marginBottom: 12 },
  title: { ...type.h1, marginTop: 8 },
  next: { ...type.body, fontSize: 15, lineHeight: 22, color: colors.muted, marginTop: 16 },
});
