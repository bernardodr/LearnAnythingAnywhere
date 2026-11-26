import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect, router, useFocusEffect } from 'expo-router';
import { useCallback } from 'react';
import { Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';

import { ConfirmEmail } from '@/components/ConfirmEmail';
import { Shelf, type ShelfView } from '@/components/Shelf';
import { Button, IconButton, Kicker, Note, Page } from '@/components/ui';
import { apiGet } from '@/lib/api';
import { useArrange } from '@/lib/arrange';
import { getPref, setPref } from '@/lib/prefs';
import { useSession } from '@/lib/session';
import type { Textbook } from '@/lib/types';
import { colors, fonts, minTouch, radius, themed, type } from '@/theme';

const SHELF_VIEW = 'laa.shelfView';

const STARTERS = ['Bouldering', 'Sourdough bread', 'Chess openings', 'Spanish for travel', 'Watercolour painting', 'Python'];

/**
 * Screen 2: the library (`docs/DESIGN.md`, the approved mockup). A quiet, curated grid of the learner's books, in the
 * order they arranged (or as spines), with "Create a textbook" pinned as the one dominant action. Settings and
 * signing out are behind the settings icon. With no books yet, it invites a first topic. Loads `/textbooks`; leads to
 * a book's page (or its preface, if that's still in progress), to settings, and to `/new`.
 */
export default function Library() {
  const { ready, user, token } = useSession();
  const { width } = useWindowDimensions();
  const queryClient = useQueryClient();
  const books = useQuery({
    queryKey: ['textbooks'],
    queryFn: () => apiGet<Textbook[]>('/textbooks', token),
    enabled: !!token,
  });
  const { arrange, failed: arrangeFailed } = useArrange();
  // Back from reading, the reading ribbon may have moved to another book: refresh when the library comes into view.
  const { refetch } = books;
  useFocusEffect(
    useCallback(() => {
      if (token) void refetch();
    }, [token, refetch]),
  );
  // Covers or spines: remembered on this device.
  const shelfView = useQuery({
    queryKey: ['pref', 'shelf-view'],
    queryFn: async (): Promise<ShelfView> => ((await getPref(SHELF_VIEW)) === 'spines' ? 'spines' : 'covers'),
    staleTime: Infinity,
  });

  if (!ready) return null;
  if (!user) return <Redirect href="/" />;

  const column = Math.min(560, width - 48);
  const shelved = books.data ?? [];

  const open = (book: Textbook) =>
    book.status === 'INTERVIEW'
      ? router.push({ pathname: '/textbooks/[id]/preface', params: { id: book.id } })
      : router.push({ pathname: '/textbooks/[id]', params: { id: book.id } });

  return (
    <Page footer={<Button label="+  Create a textbook" onPress={() => router.push('/new')} accessibilityLabel="Create a textbook" />}>
      <Kicker style={styles.masthead}>Learn Anything Anywhere</Kicker>
      <View style={styles.titleRow}>
        <View style={styles.titleText}>
          <Text style={styles.title} accessibilityRole="header">
            Your library
          </Text>
          <Text style={styles.subtitle}>A little more curious, every day.</Text>
        </View>
        <IconButton icon="settings" label="Settings and account" onPress={() => router.push('/settings')} />
      </View>

      <ConfirmEmail />
      {books.isError ? <Note tone="error">{books.error.message}</Note> : null}

      {books.isSuccess && shelved.length === 0 ? (
        <View style={styles.emptyBlock}>
          <View style={styles.emptyBook} accessible={false} />
          <Text style={styles.empty}>Your library is waiting for its first book. What are you curious about?</Text>
          <Kicker style={styles.startWith}>Start with</Kicker>
          <View style={styles.chips}>
            {STARTERS.map((topic) => (
              <Pressable
                key={topic}
                accessibilityRole="button"
                accessibilityLabel={`Start a textbook about ${topic}`}
                onPress={() => router.push({ pathname: '/new', params: { topic } })}
                style={({ pressed }) => [styles.chip, pressed && { opacity: 0.8 }]}>
                <Text style={styles.chipText}>{topic}</Text>
              </Pressable>
            ))}
          </View>
        </View>
      ) : null}

      {arrangeFailed ? <Note tone="error">Your library’s new order couldn’t be saved, so it’s back as it was. Please try again.</Note> : null}
      {shelved.length > 0 && shelfView.data ? (
        <Shelf books={shelved} width={column} view={shelfView.data} onOpen={open} onArrange={arrange}
          onView={(view) => {
            queryClient.setQueryData(['pref', 'shelf-view'], view);
            void setPref(SHELF_VIEW, view);
          }} />
      ) : null}
    </Page>
  );
}

const styles = themed(() => StyleSheet.create({
  masthead: { color: colors.ink, textAlign: 'center', letterSpacing: 3, marginTop: 4, marginBottom: 28 },
  titleRow: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12, marginBottom: 20 },
  titleText: { flex: 1 },
  title: { ...type.display },
  subtitle: { ...type.ui, fontSize: 16, color: colors.muted, marginTop: 6 },
  emptyBlock: { marginTop: 12 },
  // An empty place where the first book will stand: a cover's outline, dashed.
  emptyBook: {
    width: 132,
    height: 132 * 1.36,
    borderRadius: 4,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: colors.control,
    alignSelf: 'flex-start',
  },
  empty: { ...type.body, marginTop: 24 },
  startWith: { marginTop: 28, marginBottom: 12 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  chip: {
    minHeight: minTouch,
    paddingHorizontal: 16,
    justifyContent: 'center',
    borderRadius: radius,
    borderWidth: 1,
    borderColor: colors.rule,
    backgroundColor: colors.field,
  },
  chipText: { fontFamily: fonts.sans, fontSize: 15, color: colors.ink },
}));
