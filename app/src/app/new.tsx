import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { BookFace, COVER_RATIO, coverForVolume, volumeNumeral } from '@/components/Cover';
import { Button, Field, Kicker, Note, Page, TopBar } from '@/components/ui';
import { ApiError, apiGet, apiPost } from '@/lib/api';
import { useSession } from '@/lib/session';
import type { CreateTextbookResponse, Textbook } from '@/lib/types';
import { colors, fonts, type } from '@/theme';

const PREVIEW_WIDTH = 124;

/**
 * Starting a book (`docs/DESIGN.md`): "What would you like to learn?", the topic, and a preview of the new book's cover
 * taking shape as the topic is typed, in the cloth its volume will get. The drawing on the cover is chosen at the end of
 * the preface, so until then the preview carries the plain ornament. "Begin the preface" is the one dominant action,
 * pinned at the bottom.
 *
 * Loads `/textbooks` (usually already loaded by the library) to know the next volume. Sends the topic to
 * `POST /textbooks`, which runs the topic gate (BRIEF section 8): a declined topic creates nothing and shows the kind
 * message in its place; otherwise it leads to the new book's preface. A `topic` parameter (a starter topic tapped in an
 * empty library) fills the field.
 */
export default function NewTextbook() {
  const { ready, user, token } = useSession();
  const params = useLocalSearchParams<{ topic?: string }>();
  const queryClient = useQueryClient();
  const books = useQuery({
    queryKey: ['textbooks'],
    queryFn: () => apiGet<Textbook[]>('/textbooks', token),
    enabled: !!token,
  });
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

  // The volume this book will be: the server gives the next number after the learner's highest.
  const volume = books.data ? Math.max(0, ...books.data.map((b) => b.volume)) + 1 : null;
  // As the server will title it: spaces tidied and the first letter capitalised (bouldering becomes Bouldering).
  const title = topic.replace(/\s+/g, ' ').trim();
  const canBegin = title !== '' && !busy;

  return (
    <Page footer={<Button label="Begin the preface" onPress={submit} disabled={!title} busy={busy} />}>
      <TopBar backLabel="Back to your library" />
      <Kicker>A new textbook</Kicker>
      <Text style={styles.title} accessibilityRole="header">
        What would you like to learn?
      </Text>

      {/* The drawn cover is decorative to screen readers; the volume and the note beside it say what matters. */}
      <View style={styles.preview}>
        {title && volume ? (
          <BookFace book={{ title: title[0].toUpperCase() + title.slice(1), coverColor: coverForVolume(volume), coverMotif: 'ornament', status: 'ACTIVE' }}
            width={PREVIEW_WIDTH} />
        ) : (
          // An empty place where the new book will stand, as in an empty library.
          <View style={styles.emptyBook} accessible={false} />
        )}
        <View style={styles.previewText}>
          {volume ? <Kicker>Volume {volumeNumeral(volume)}</Kicker> : null}
          <Text style={styles.previewNote}>
            {title ? 'Its cover drawing is chosen at the end of the preface.' : 'Your new book’s cover appears here as you type.'}
          </Text>
        </View>
      </View>

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
        onSubmitEditing={() => canBegin && submit()}
      />
      {declined ? (
        <View accessibilityLiveRegion="polite">
          <Note>{declined}</Note>
        </View>
      ) : null}
      {error ? <Note tone="error">{error}</Note> : null}
      <Text style={styles.next}>
        Next comes the preface: a few questions about why you want to learn this. Your textbook is written around your
        answers.
      </Text>
    </Page>
  );
}

const styles = StyleSheet.create({
  title: { ...type.h1, marginTop: 8 },
  preview: { flexDirection: 'row', alignItems: 'flex-end', gap: 20, marginTop: 28, marginBottom: 28 },
  emptyBook: {
    width: PREVIEW_WIDTH,
    height: PREVIEW_WIDTH * COVER_RATIO,
    borderRadius: 4,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: colors.control,
  },
  previewText: { flex: 1, gap: 6, paddingBottom: 4 },
  previewNote: { fontFamily: fonts.serifItalic, fontSize: 15, lineHeight: 21, color: colors.muted },
  next: { ...type.small, fontSize: 15, lineHeight: 22, color: colors.muted },
});
