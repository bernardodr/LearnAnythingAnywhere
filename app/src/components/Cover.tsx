import { Pressable, StyleSheet, Text, View } from 'react-native';

import type { Textbook } from '@/lib/types';
import { colors, fonts } from '@/theme';

const ROMAN = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI', 'XII'];

/** A cloth-bound cover on the shelf (BRIEF screen 2): cloth colour, inset border, title, volume. */
export function Cover({ book, onPress, width }: { book: Textbook; onPress: () => void; width: number }) {
  const inProgress = book.status === 'INTERVIEW';
  const caption = inProgress ? 'Preface in progress' : (book.missionHeadline ?? '');
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${book.title}, volume ${book.volume}. ${caption}`}
      accessibilityHint={inProgress ? 'Continues the preface interview' : 'Opens the contents page'}
      style={({ pressed }) => [{ width }, pressed && { opacity: 0.85 }]}>
      <View style={[styles.cloth, { backgroundColor: book.coverColor, height: width * 1.38 }]}>
        <View style={styles.inset}>
          <Text style={styles.title} numberOfLines={4}>
            {book.title}
          </Text>
          <View style={styles.bar} />
          <Text style={styles.volume}>Vol. {ROMAN[book.volume - 1] ?? book.volume}</Text>
        </View>
      </View>
      <Text style={[styles.caption, inProgress && styles.captionMuted]} numberOfLines={3}>
        {caption}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  cloth: {
    borderRadius: 3,
    padding: 8,
    boxShadow: '2px 4px 8px rgba(31, 27, 22, 0.18)',
  },
  inset: {
    flex: 1,
    borderWidth: 1,
    borderColor: 'rgba(255,253,247,0.55)',
    borderRadius: 2,
    padding: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  title: {
    fontFamily: fonts.serifSemiBold,
    fontSize: 19,
    lineHeight: 24,
    color: colors.paper,
    textAlign: 'center',
  },
  bar: { width: 28, height: 1, backgroundColor: 'rgba(255,253,247,0.7)', marginVertical: 12 },
  volume: {
    fontFamily: fonts.sansSemiBold,
    fontSize: 11,
    letterSpacing: 1.4,
    textTransform: 'uppercase',
    color: colors.paper,
  },
  caption: { fontFamily: fonts.serifItalic, fontSize: 15, lineHeight: 21, color: colors.ink, marginTop: 10 },
  captionMuted: { color: colors.muted },
});
