import { useQuery } from '@tanstack/react-query';
import { Redirect, router } from 'expo-router';
import { Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';

import { Cover } from '@/components/Cover';
import { Button, Kicker, Note, Page } from '@/components/ui';
import { apiGet } from '@/lib/api';
import { useSession } from '@/lib/session';
import type { Textbook } from '@/lib/types';
import { colors, fonts, minTouch, type } from '@/theme';

const STARTERS = ['Bouldering', 'Sourdough bread', 'Chess openings', 'Spanish for travel', 'Watercolour painting', 'Python'];

/** Screen 2: the library. Covers on a shelf, or an empty shelf with "Start with" chips. */
export default function Library() {
  const { ready, user, token, signOut } = useSession();
  const { width } = useWindowDimensions();
  const books = useQuery({
    queryKey: ['textbooks'],
    queryFn: () => apiGet<Textbook[]>('/textbooks', token),
    enabled: !!token,
  });

  if (!ready) return null;
  if (!user) return <Redirect href="/" />;

  const column = Math.min(560, width - 48);
  const perRow = column >= 480 ? 3 : 2;
  const coverWidth = (column - (perRow - 1) * 20) / perRow;
  const rows = chunk(books.data ?? [], perRow);

  const open = (book: Textbook) =>
    book.status === 'INTERVIEW'
      ? router.push({ pathname: '/textbooks/[id]/preface', params: { id: book.id } })
      : router.push({ pathname: '/textbooks/[id]', params: { id: book.id } });

  return (
    <Page>
      <View style={styles.header}>
        <Kicker>Learn Anything Anywhere</Kicker>
        <Button label="Sign out" variant="quiet" onPress={() => void signOut().then(() => router.replace('/'))}
          accessibilityLabel={`Sign out ${user.email}`} />
      </View>
      <Text style={styles.title} accessibilityRole="header">
        Your shelf
      </Text>

      {books.isError ? <Note tone="error">{books.error.message}</Note> : null}

      {books.isSuccess && rows.length === 0 ? (
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

      {rows.map((row, i) => (
        <View key={i} style={styles.row}>
          <View style={styles.books}>
            {row.map((book) => (
              <Cover key={book.id} book={book} width={coverWidth} onPress={() => open(book)} />
            ))}
          </View>
          <View style={styles.shelf} accessible={false} />
        </View>
      ))}

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

function chunk<T>(items: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
  return out;
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
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
  row: { marginBottom: 28 },
  books: { flexDirection: 'row', gap: 20, alignItems: 'flex-start' },
  shelf: { height: 8, backgroundColor: '#C8B89A', borderRadius: 2, marginTop: 12 },
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
