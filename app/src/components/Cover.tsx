import { useId } from 'react';
import { type AccessibilityActionEvent, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';

import { Motif, motifOrDefault } from '@/components/Motif';
import type { Textbook } from '@/lib/types';
import { colors, coverInk, fonts, themed } from '@/theme';

/** Every cover's shape: height = width × this (DESIGN.md, "Books"). */
export const COVER_RATIO = 1.36;

const ROMAN = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI', 'XII'];

/** "IV" for volume 4 (plain digits past XII). */
export function volumeNumeral(volume: number) {
  return ROMAN[volume - 1] ?? String(volume);
}

/**
 * The cloth a learner's `volume`th book gets: the four cover colours in turn, exactly as the server hands them out
 * (`Textbook.COVER_COLORS`; `CoverColorsTest` keeps the two lists the same). The new-book screen uses it to preview the
 * cover before the book exists.
 */
export function coverForVolume(volume: number) {
  const cloths = Object.values(colors.covers);
  return cloths[(Math.max(1, volume) - 1) % cloths.length];
}

/** What a book is bound in: leather or linen, photographed materials laid over the cover's colour. */
export type Material = 'leather' | 'linen';

// Overlays made by scripts/cover-textures.cjs from CC0 materials (app/assets/covers/README.md): seamless 512-pixel
// tiles of black and white grain at partial strength, shown at 256 points.
const MATERIALS: Record<Material, number> = {
  leather: require('../../assets/covers/leather.png'),
  linen: require('../../assets/covers/linen.png'),
};
const TILE = 256;

/**
 * The material a cloth colour is bound in (DESIGN.md "Books", the owner's choice): forest green and terracotta are
 * leather, ochre and dusty blue are linen. Colours go to books in turn, so a library mixes the two like a real
 * bookcase. Any other colour (one from before the palette changed) is linen.
 */
export function materialFor(cloth: string): Material {
  const c = cloth.toUpperCase();
  return c === colors.covers.forest.toUpperCase() || c === colors.covers.terracotta.toUpperCase() ? 'leather' : 'linen';
}

/** A small number from text, so each book gets its own cut of the material (where its grain starts). */
function seed(text: string) {
  let h = 7;
  for (const ch of text) h = (h * 31 + ch.charCodeAt(0)) % 9973;
  return h;
}

/**
 * The grain of a book's material, filling its parent (the cloth colour shows through): tiles of the material's
 * overlay, offset per book so no two covers show the same grain in the same place. Decorative, and never touchable.
 *
 * @param cloth the cover colour, which decides leather or linen
 * @param width the area to cover, in points; @param height likewise
 * @param cut a stable text for the book (its title), which picks where the grain starts
 * @param radius the corners to clip to, matching the book's
 */
export function MaterialGrain({ cloth, width, height, cut, radius = 0 }: {
  cloth: string; width: number; height: number; cut: string; radius?: number;
}) {
  const source = MATERIALS[materialFor(cloth)];
  const n = seed(cut);
  const offX = n % TILE, offY = Math.floor(n / TILE) % TILE;
  const tiles = [];
  for (let y = -offY; y < height; y += TILE) {
    for (let x = -offX; x < width; x += TILE) {
      tiles.push(<Image key={`${x},${y}`} source={source} resizeMode="stretch" style={{ position: 'absolute', left: x, top: y, width: TILE, height: TILE }} />);
    }
  }
  return (
    <View pointerEvents="none" style={[StyleSheet.absoluteFill, { overflow: 'hidden', borderRadius: radius }]}
      accessible={false} importantForAccessibility="no-hide-descendants">
      {tiles}
    </View>
  );
}

/**
 * The book the learner is reading: the one whose lesson they opened last (`lastReadAt`), which wears the reading ribbon
 * (issue #81). Null if they haven't opened a lesson yet.
 */
export function readingNow(books: Pick<Textbook, 'id' | 'lastReadAt'>[]): string | null {
  let latest: { id: string; at: string } | null = null;
  for (const b of books) if (b.lastReadAt && (!latest || b.lastReadAt > latest.at)) latest = { id: b.id, at: b.lastReadAt };
  return latest?.id ?? null;
}

/**
 * What a screen reader says for a book, whichever way it's shown.
 *
 * @param reading this is the book being read (it wears the ribbon), which the label says first
 */
export function bookLabel(book: Textbook, reading = false) {
  const inProgress = book.status === 'INTERVIEW';
  const caption = inProgress ? 'Preface in progress' : (book.missionHeadline ?? '');
  return {
    label: `${book.title}, volume ${book.volume}. ${reading ? 'The book you’re reading. ' : ''}${caption}`,
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
 * Uses relative luminance, so a cover colour from before the palette change still gets a readable title. Always the
 * light palette's ink and ivory: a book looks the same in dark mode.
 */
export function titleColorOn(cloth: string) {
  const channel = (i: number) => {
    const v = parseInt(cloth.slice(i, i + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  };
  const lum = /^#[0-9a-fA-F]{6}$/.test(cloth) ? 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5) : 0;
  return lum > 0.3 ? coverInk.ink : coverInk.ivory;
}

/**
 * A book's front, drawn as a physical book (DESIGN.md, "Books"): its cloth colour bound in leather or linen (a
 * photographed material's grain), a soft light from the top left, a hint of spine depth down the left edge, a natural
 * shadow, the title set top left, and its one subject motif lower down. Decorative to screen readers: whatever holds it
 * (`Cover`, the book page) carries the label.
 *
 * @param book the textbook: its title, cloth (`coverColor`) and motif (`coverMotif`)
 * @param width the cover's width; the height follows from `COVER_RATIO`, and the type and motif scale with it
 * @param ribbon a ribbon bookmark out of the top: only on the book being read (`readingNow`)
 */
export function BookFace({ book, width, ribbon = false }: {
  book: Pick<Textbook, 'title' | 'coverColor' | 'coverMotif'>;
  width: number;
  ribbon?: boolean;
}) {
  // Gradient ids must be unique on a web page with many covers.
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
      <MaterialGrain cloth={book.coverColor} width={width} height={height} cut={book.title} radius={3} />
      <Svg width={width} height={height} style={StyleSheet.absoluteFill}>
        <Defs>
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
        <Rect width={width} height={height} fill={`url(#light${id})`} />
        <Rect width={width} height={height} fill={`url(#spine${id})`} />
      </Svg>
      {ribbon ? <View style={[styles.ribbon, { left: width * 0.14, width: Math.max(6, width * 0.05) }]} /> : null}
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
 * A book in the library (DESIGN.md, "Books"): its cover, and under it a quiet one-line caption: "Continue reading" on
 * the book being read (which wears the ribbon, as in the mockup), "Preface in progress" on a book still in its preface.
 * `onMove` offers screen readers "Move earlier/later".
 */
export function Cover({
  book,
  onPress,
  width,
  onMove,
  reading = false,
  dimOnPress = true,
}: {
  book: Textbook;
  onPress: () => void;
  width: number;
  onMove?: (to: CoverMove) => void;
  /** This is the book being read (`readingNow`): the ribbon, and "Continue reading". */
  reading?: boolean;
  /** The library gives its own feedback (the book presses in, or lifts), so it turns the dimming off. */
  dimOnPress?: boolean;
}) {
  const inProgress = book.status === 'INTERVIEW';
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={bookLabel(book, reading).label}
      accessibilityHint={bookLabel(book, reading).hint}
      accessibilityActions={onMove ? MOVE_ACTIONS : undefined}
      onAccessibilityAction={onMove ? (e: AccessibilityActionEvent) => onMove(e.nativeEvent.actionName === 'moveEarlier' ? 'earlier' : 'later') : undefined}
      style={({ pressed }) => [{ width }, pressed && dimOnPress && { opacity: 0.9 }]}>
      <BookFace book={book} width={width} ribbon={reading} />
      <Text style={styles.caption} numberOfLines={1}>{reading ? 'Continue reading' : inProgress ? 'Preface in progress' : ' '}</Text>
    </Pressable>
  );
}

const styles = themed(() => StyleSheet.create({
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
  // The reading ribbon: a bookmark coming out of the top of the book being read.
  ribbon: { position: 'absolute', top: -8, height: 26, backgroundColor: colors.ribbon, borderRadius: 1 },
  caption: { fontFamily: fonts.serifItalic, fontSize: 15, lineHeight: 21, color: colors.accent, marginTop: 12, textAlign: 'center' },
}));
