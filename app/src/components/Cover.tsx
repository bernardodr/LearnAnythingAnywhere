import type { ReactNode } from 'react';
import { type AccessibilityActionEvent, Pressable, StyleSheet, Text, View } from 'react-native';

import type { Textbook } from '@/lib/types';
import { colors, fonts } from '@/theme';

const ROMAN = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI', 'XII'];

/** "IV" for volume 4 (plain digits past XII). */
export function volumeNumeral(volume: number) {
  return ROMAN[volume - 1] ?? String(volume);
}

/** What a screen reader says for a book on the shelf, whichever way it's shown. */
export function bookLabel(book: Textbook) {
  const inProgress = book.status === 'INTERVIEW';
  const caption = inProgress ? 'Preface in progress' : (book.missionHeadline ?? '');
  return {
    label: `${book.title}, volume ${book.volume}. ${caption}`,
    hint: inProgress ? 'Continues the preface interview' : 'Opens the contents page',
  };
}

/** Screen readers' actions for moving a book along the shelf. */
export const MOVE_ACTIONS = [
  { name: 'moveEarlier', label: 'Move earlier on the shelf' },
  { name: 'moveLater', label: 'Move later on the shelf' },
];

/** What a cover can be asked to do besides opening: moving it along the shelf (screen readers' actions). */
export type CoverMove = 'earlier' | 'later';

/**
 * A cloth-bound cover on the shelf (BRIEF screen 2): cloth colour, inset border, title, volume, and its caption, or
 * a `footer` in its place (the shelf's arrange buttons). `onMove` offers screen readers "Move earlier/later" actions.
 */
export function Cover({
  book,
  onPress,
  width,
  onMove,
  footer,
  dimOnPress = true,
}: {
  book: Textbook;
  onPress: () => void;
  width: number;
  onMove?: (to: CoverMove) => void;
  footer?: ReactNode;
  /** The shelf gives its own feedback (the book presses in, or lifts), so it turns the dimming off. */
  dimOnPress?: boolean;
}) {
  const inProgress = book.status === 'INTERVIEW';
  const caption = inProgress ? 'Preface in progress' : (book.missionHeadline ?? '');
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={bookLabel(book).label}
      accessibilityHint={bookLabel(book).hint}
      accessibilityActions={onMove ? MOVE_ACTIONS : undefined}
      onAccessibilityAction={onMove ? (e: AccessibilityActionEvent) => onMove(e.nativeEvent.actionName === 'moveEarlier' ? 'earlier' : 'later') : undefined}
      style={({ pressed }) => [{ width }, pressed && dimOnPress && { opacity: 0.85 }]}>
      <View style={[styles.cloth, { backgroundColor: book.coverColor, height: width * 1.38 }]}>
        <View style={styles.inset}>
          <Text style={styles.title} numberOfLines={4}>
            {book.title}
          </Text>
          <View style={styles.bar} />
          <Text style={styles.volume}>Vol. {volumeNumeral(book.volume)}</Text>
        </View>
      </View>
      {footer ?? (
        <Text style={[styles.caption, inProgress && styles.captionMuted]} numberOfLines={3}>
          {caption}
        </Text>
      )}
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
