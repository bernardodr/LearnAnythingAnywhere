import { type ReactNode, useId } from 'react';
import { type AccessibilityActionEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Svg, { Defs, Line, LinearGradient, Pattern, Rect, Stop } from 'react-native-svg';

import { Motif, motifOrDefault } from '@/components/Motif';
import type { Textbook } from '@/lib/types';
import { colors, fonts } from '@/theme';

/** Every cover's shape: height = width × this (DESIGN.md, "Books"). */
export const COVER_RATIO = 1.36;

const ROMAN = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI', 'XII'];

/** "IV" for volume 4 (plain digits past XII). */
export function volumeNumeral(volume: number) {
  return ROMAN[volume - 1] ?? String(volume);
}

/** What a screen reader says for a book, whichever way it's shown. */
export function bookLabel(book: Textbook) {
  const inProgress = book.status === 'INTERVIEW';
  const caption = inProgress ? 'Preface in progress' : (book.missionHeadline ?? '');
  return {
    label: `${book.title}, volume ${book.volume}. ${caption}`,
    hint: inProgress ? 'Continues the preface interview' : 'Opens the book',
  };
}

/** Screen readers' actions for moving a book in the library. */
export const MOVE_ACTIONS = [
  { name: 'moveEarlier', label: 'Move earlier in your library' },
  { name: 'moveLater', label: 'Move later in your library' },
];

/** What a cover can be asked to do besides opening: moving it in the library (screen readers' actions). */
export type CoverMove = 'earlier' | 'later';

/**
 * Ink or ivory for a title on this cloth, whichever contrasts more: ochre takes ink, the darker cloths take ivory.
 * Uses relative luminance, so a cover colour from before the palette change still gets a readable title.
 */
export function titleColorOn(cloth: string) {
  const channel = (i: number) => {
    const v = parseInt(cloth.slice(i, i + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  };
  const lum = /^#[0-9a-fA-F]{6}$/.test(cloth) ? 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5) : 0;
  return lum > 0.3 ? colors.ink : colors.paper;
}

/**
 * A book's front, drawn as a physical book (DESIGN.md, "Books"): the cloth colour with a fine weave, a soft light
 * from the top left, a hint of spine depth down the left edge, a natural shadow, the title set top left, and its one
 * subject motif lower down. A ribbon marks a preface still in progress. Decorative to screen readers: whatever holds
 * it (`Cover`, the book page) carries the label.
 *
 * @param book the textbook: its title, cloth (`coverColor`), motif (`coverMotif`) and status
 * @param width the cover's width; the height follows from `COVER_RATIO`, and the type and motif scale with it
 */
export function BookFace({ book, width }: { book: Pick<Textbook, 'title' | 'coverColor' | 'coverMotif' | 'status'>; width: number }) {
  // Pattern and gradient ids must be unique on a web page with many covers.
  const id = useId().replace(/:/g, '');
  const height = width * COVER_RATIO;
  const ink = titleColorOn(book.coverColor);
  // As large as the cover allows (about 0.15 of its width), but never so large that the longest word breaks: a
  // binder would set "Computers" smaller rather than split it. Widths are estimated from the serif's average glyph.
  const longestWord = Math.max(...book.title.split(/\s+/).map((w) => w.length), 1);
  const textWidth = width * 0.75;
  const titleSize = Math.max(12, Math.min(width * (book.title.length > 26 ? 0.125 : 0.15), textWidth / (longestWord * 0.64)));
  const motifSize = width * 0.5;
  return (
    <View style={[styles.face, { width, height, backgroundColor: book.coverColor }]} accessible={false}
      importantForAccessibility="no-hide-descendants">
      <Svg width={width} height={height} style={StyleSheet.absoluteFill}>
        <Defs>
          {/* The weave: fine threads one way light, the other dark, too faint to read as a pattern. */}
          <Pattern id={`weave${id}`} width={3} height={3} patternUnits="userSpaceOnUse">
            <Line x1={0} y1={0.5} x2={3} y2={0.5} stroke="#FFFFFF" strokeOpacity={0.07} strokeWidth={0.8} />
            <Line x1={0.5} y1={0} x2={0.5} y2={3} stroke="#000000" strokeOpacity={0.06} strokeWidth={0.8} />
          </Pattern>
          {/* Light from the top left, falling off towards the bottom right. */}
          <LinearGradient id={`light${id}`} x1="0" y1="0" x2="1" y2="1">
            <Stop offset="0" stopColor="#FFFFFF" stopOpacity={0.12} />
            <Stop offset="0.55" stopColor="#FFFFFF" stopOpacity={0} />
            <Stop offset="1" stopColor="#000000" stopOpacity={0.14} />
          </LinearGradient>
          {/* The spine's hinge: a darker band, then a thin crease of light where the board bends. */}
          <LinearGradient id={`spine${id}`} x1="0" y1="0" x2="1" y2="0">
            <Stop offset="0" stopColor="#000000" stopOpacity={0.3} />
            <Stop offset="0.055" stopColor="#000000" stopOpacity={0.12} />
            <Stop offset="0.07" stopColor="#FFFFFF" stopOpacity={0.14} />
            <Stop offset="0.085" stopColor="#000000" stopOpacity={0.06} />
            <Stop offset="0.12" stopColor="#000000" stopOpacity={0} />
          </LinearGradient>
        </Defs>
        <Rect width={width} height={height} fill={`url(#weave${id})`} />
        <Rect width={width} height={height} fill={`url(#light${id})`} />
        <Rect width={width} height={height} fill={`url(#spine${id})`} />
      </Svg>
      {book.status === 'INTERVIEW' ? <View style={[styles.ribbon, { left: width * 0.14, width: Math.max(6, width * 0.05) }]} /> : null}
      <View style={[styles.inner, { paddingTop: width * 0.13, paddingLeft: width * 0.17, paddingRight: width * 0.08 }]}>
        <Text style={[styles.title, { color: ink, fontSize: titleSize, lineHeight: titleSize * 1.12 }]} numberOfLines={5}>
          {book.title}
        </Text>
      </View>
      <View style={[styles.motif, { bottom: width * 0.12, left: width * 0.04 + (width - motifSize) / 2 }]}>
        <Motif name={motifOrDefault(book.coverMotif)} size={motifSize} color={ink} strokeWidth={width < 120 ? 2.4 : 1.7} />
      </View>
    </View>
  );
}

/**
 * A book in the library (DESIGN.md, "Books"): its cover, and under it a quiet one-line caption for a preface still in
 * progress, or a `footer` in its place (`null` for nothing: the library puts its arrange buttons outside the cover,
 * where they can't start a drag). `onMove` offers screen readers "Move earlier/later".
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
  /** The library gives its own feedback (the book presses in, or lifts), so it turns the dimming off. */
  dimOnPress?: boolean;
}) {
  const inProgress = book.status === 'INTERVIEW';
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={bookLabel(book).label}
      accessibilityHint={bookLabel(book).hint}
      accessibilityActions={onMove ? MOVE_ACTIONS : undefined}
      onAccessibilityAction={onMove ? (e: AccessibilityActionEvent) => onMove(e.nativeEvent.actionName === 'moveEarlier' ? 'earlier' : 'later') : undefined}
      style={({ pressed }) => [{ width }, pressed && dimOnPress && { opacity: 0.9 }]}>
      <BookFace book={book} width={width} />
      {footer !== undefined ? footer : <Text style={styles.caption} numberOfLines={1}>{inProgress ? 'Preface in progress' : ' '}</Text>}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  face: {
    borderTopLeftRadius: 2,
    borderBottomLeftRadius: 2,
    borderTopRightRadius: 4,
    borderBottomRightRadius: 4,
    overflow: 'visible',
    // A soft natural shadow: a close contact shadow, and a wider one falling down and away.
    boxShadow: '0px 1px 2px rgba(41, 40, 35, 0.22), 2px 12px 18px -8px rgba(41, 40, 35, 0.45)',
  },
  inner: { flex: 1 },
  title: { fontFamily: fonts.serifSemiBold, letterSpacing: -0.2 },
  motif: { position: 'absolute' },
  // A ribbon bookmark coming out of the top of the book.
  ribbon: { position: 'absolute', top: -8, height: 26, backgroundColor: colors.ribbon, borderRadius: 1 },
  caption: { fontFamily: fonts.serifItalic, fontSize: 15, lineHeight: 21, color: colors.accent, marginTop: 12, textAlign: 'center' },
});
