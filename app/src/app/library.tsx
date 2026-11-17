import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect, router } from 'expo-router';
import { useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';

import { ConfirmEmail } from '@/components/ConfirmEmail';
import { Shelf, type ShelfView } from '@/components/Shelf';
import { Button, Kicker, Note, Page } from '@/components/ui';
import { apiGet, apiPut } from '@/lib/api';
import { getPref, setPref } from '@/lib/prefs';
import { useSession } from '@/lib/session';
import type { Textbook } from '@/lib/types';
import { colors, fonts, minTouch, type } from '@/theme';

const SHELF_VIEW = 'laa.shelfView';

const STARTERS = ['Bouldering', 'Sourdough bread', 'Chess openings', 'Spanish for travel', 'Watercolour painting', 'Python'];

/** Screen 2: the library. Covers on shelves, arranged by hand, or an empty shelf with "Start with" chips. */
export default function Library() {
  const { ready, user, token, signOut } = useSession();
  const { width } = useWindowDimensions();
  const queryClient = useQueryClient();
  const books = useQuery({
    queryKey: ['textbooks'],
    queryFn: () => apiGet<Textbook[]>('/textbooks', token),
    enabled: !!token,
  });
  const [arrangeError, setArrangeError] = useState(false);
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

  /** The new order shows at once; the server keeps it, and if it can't, the shelf goes back to what it has. */
  const arrange = (ids: string[]) => {
    const byId = new Map(shelved.map((b) => [b.id, b]));
    queryClient.setQueryData<Textbook[]>(['textbooks'], ids.flatMap((id) => byId.get(id) ?? []));
    setArrangeError(false);
    apiPut('/textbooks/order', { ids }, token).catch(() => {
      setArrangeError(true);
      void queryClient.invalidateQueries({ queryKey: ['textbooks'] });
    });
  };

  const open = (book: Textbook) =>
    book.status === 'INTERVIEW'
      ? router.push({ pathname: '/textbooks/[id]/preface', params: { id: book.id } })
      : router.push({ pathname: '/textbooks/[id]', params: { id: book.id } });

  return (
    <Page>
      <View style={styles.header}>
        <Kicker>Learn Anything Anywhere</Kicker>
        <View style={styles.headerActions}>
          {user.role === 'OWNER' && Platform.OS === 'web' ? (
            <Button label="Owner console" variant="quiet" onPress={() => router.push('/owner')} />
          ) : null}
          <Button label="Settings" variant="quiet" onPress={() => router.push('/settings')} accessibilityLabel="Reader settings" />
          <Button label="Sign out" variant="quiet" onPress={() => void signOut().then(() => router.replace('/'))}
            accessibilityLabel={`Sign out ${user.email}`} />
        </View>
      </View>
      <Text style={styles.title} accessibilityRole="header">
        Your shelf
      </Text>

      <ConfirmEmail />
      {books.isError ? <Note tone="error">{books.error.message}</Note> : null}

      {books.isSuccess && shelved.length === 0 ? (
        <View>
          <View style={styles.emptyShelf} accessible={false}>
            <View style={styles.bookend} />
          </View>
          <Text style={styles.empty}>Nothing on your shelf yet. What are you curious about?</Text>
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

      {arrangeError ? <Note tone="error">Your shelf’s new order couldn’t be saved, so it’s back as it was. Please try again.</Note> : null}
      {shelved.length > 0 && shelfView.data ? (
        <Shelf books={shelved} width={column} view={shelfView.data} onOpen={open} onArrange={arrange}
          onView={(view) => {
            queryClient.setQueryData(['pref', 'shelf-view'], view);
            void setPref(SHELF_VIEW, view);
          }} />
      ) : null}

      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Start a new textbook"
        onPress={() => router.push('/new')}
        style={({ pressed }) => [styles.plus, pressed && { opacity: 0.85 }]}>
        <Text style={styles.plusText}>+</Text>
      </Pressable>
    </Page>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  headerActions: { flexDirection: 'row', alignItems: 'center' },
  title: { ...type.h1, marginTop: 8, marginBottom: 24 },
  emptyShelf: {
    height: 120,
    borderBottomWidth: 10,
    borderBottomColor: '#C8B89A',
    borderRadius: 2,
    justifyContent: 'flex-end',
    paddingLeft: 16,
  },
  bookend: { width: 14, height: 70, backgroundColor: colors.rule, borderTopLeftRadius: 3, borderTopRightRadius: 3 },
  empty: { ...type.body, color: colors.muted, marginTop: 20 },
  startWith: { marginTop: 24, marginBottom: 12 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  chip: {
    minHeight: minTouch,
    paddingHorizontal: 16,
    justifyContent: 'center',
    borderRadius: 22,
    borderWidth: 1,
    borderColor: colors.rule,
    backgroundColor: '#FFFFFF',
  },
  chipText: { fontFamily: fonts.sans, fontSize: 15, color: colors.ink },
  plus: {
    alignSelf: 'center',
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: colors.accent,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 32,
    boxShadow: '0px 3px 8px rgba(31, 27, 22, 0.2)',
  },
  plusText: { fontFamily: fonts.sans, fontSize: 34, lineHeight: 38, color: colors.paper },
});
