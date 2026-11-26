import { useEffect, useRef } from 'react';
import { type AccessibilityActionEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { GestureDetector, useHoverGesture, usePanGesture, useSimultaneousGestures } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { bookLabel, Cover, COVER_RATIO, type CoverMove, MaterialGrain, MOVE_ACTIONS, readingNow, titleColorOn, volumeNumeral } from '@/components/Cover';
import type { Textbook } from '@/lib/types';
import { colors, fonts, minTouch, themed, type } from '@/theme';

/** Covers: the full covers in a grid. Spines: books standing side by side, spine out, on a fine ledge. */
export type ShelfView = 'covers' | 'spines';

// Covers
const GAP = 24;
/** The one-line caption under a cover: its top margin plus a line. */
const CAPTION_H = 33;
const ROW_GAP = 28;
// Spines
const SPINE_START = 0;
const SPINE_GAP = 3;
const SPINE_MAX_H = 190;
const SPINE_ROW_GAP = 30;
/** The ledge the spines stand on: a fine line with room for its shadow. */
const BOARD_H = 6;

// The feel of it: quick and firm, on a solid shelf. Neighbours snap aside; a held book lifts off the shelf and leans a
// little with your hand; a dropped one lands in its place and stops dead, and the books beside it give a small knock.
// Nothing bounces or wobbles. Every spring names its mass: Reanimated 4 defaults it to 4, which halves the damping.
/** Books making room: about 0.15 s, a hair of overshoot (damping ratio 0.94). */
const GLIDE = { mass: 1, stiffness: 600, damping: 46 };
/** Picking up, hover, a chosen spine rising: 0.92. */
const LIFT = { mass: 1, stiffness: 800, damping: 52 };
/** Landing, and anything coming back upright: a fast spring (0.7) clamped at its place, so it arrives in about a
 * tenth of a second and stops dead there instead of passing it. */
const SETTLE = { mass: 1, stiffness: 900, damping: 42, overshootClamping: true };

type Size = { w: number; h: number };

type Layout = {
  /** 0: covers, 1: spines (a number, so worklets compare it cheaply). */
  view: number;
  width: number;
  perRow: number;
  coverW: number;
  clothH: number;
  rowH: number;
  spineRowH: number;
  sizes: Record<string, Size>;
};

/** A book's spine: its own thickness and height, the same every time (from its id), so the shelf looks hand-filled. */
function spineSize(id: string): Size {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
  return { w: 44 + (h % 15), h: 150 + ((h >>> 5) % 41) };
}

/** Where a book stands, and on which shelf, with the shelf in `order`. */
function place(id: string, order: string[], l: Layout) {
  'worklet';
  const i = order.indexOf(id);
  if (l.view === 0) {
    const row = Math.floor(i / l.perRow);
    return { x: (i % l.perRow) * (l.coverW + GAP), y: row * l.rowH, row };
  }
  let x = SPINE_START;
  let row = 0;
  for (let k = 0; k < order.length; k++) {
    const s = l.sizes[order[k]];
    if (!s) continue;
    if (x + s.w > l.width && x > SPINE_START) {
      row += 1;
      x = SPINE_START;
    }
    if (order[k] === id) return { x, y: row * l.spineRowH + (SPINE_MAX_H - s.h), row };
    x += s.w + SPINE_GAP;
  }
  return { x: 0, y: 0, row: 0 };
}

/** The place a held book is over: the index it should move to. */
function target(id: string, cx: number, cy: number, order: string[], l: Layout) {
  'worklet';
  const count = order.length;
  if (l.view === 0) {
    const rows = Math.max(1, Math.ceil(count / l.perRow));
    const col = Math.min(l.perRow - 1, Math.max(0, Math.floor((cx + GAP / 2) / (l.coverW + GAP))));
    const row = Math.min(rows - 1, Math.max(0, Math.floor(cy / l.rowH)));
    return Math.min(count - 1, row * l.perRow + col);
  }
  const want = Math.max(0, Math.floor(cy / l.spineRowH));
  let x = SPINE_START;
  let row = 0;
  let first = -1;
  let last = -1;
  for (let k = 0; k < count; k++) {
    const s = l.sizes[order[k]];
    if (!s) continue;
    if (x + s.w > l.width && x > SPINE_START) {
      row += 1;
      x = SPINE_START;
    }
    if (row === want) {
      if (first < 0) first = k;
      last = k;
      if (cx >= x && cx < x + s.w + SPINE_GAP) return k;
    }
    x += s.w + SPINE_GAP;
  }
  if (want > row) return count - 1;
  if (last < 0) return order.indexOf(id);
  return cx < SPINE_START ? first : last;
}

function moved(ids: string[], from: number, to: number) {
  'worklet';
  const next = ids.slice();
  const [id] = next.splice(from, 1);
  next.splice(to, 0, id);
  return next;
}

type Shared = {
  order: SharedValue<string[]>;
  layout: SharedValue<Layout>;
  active: SharedValue<string | null>;
  /** The latest drop: which book, and a counter so every drop is news. */
  drop: SharedValue<{ n: number; id: string }>;
};

/**
 * The shelf (BRIEF screen 2), arranged by hand, shown as covers or as spines standing on the shelf. Drag a book (on a
 * phone, press and hold first) and the others make room; the order is the learner's, kept by the server.
 * Without dragging (the owner's choice, 2026-11-26: no Arrange mode here): screen readers get "Move earlier / later"
 * actions on every book, and each book's page has the same two moves in its ••• menu. Reduced motion keeps the moves
 * and drops the flourishes.
 */
export function Shelf({
  books,
  width,
  view,
  onView,
  onOpen,
  onArrange,
}: {
  books: Textbook[];
  width: number;
  view: ShelfView;
  onView: (view: ShelfView) => void;
  onOpen: (book: Textbook) => void;
  onArrange: (ids: string[]) => void;
}) {
  const key = books.map((b) => b.id).join(',');
  const ids = key ? key.split(',') : [];
  const perRow = width >= 480 ? 3 : 2;
  const coverW = (width - (perRow - 1) * GAP) / perRow;
  const clothH = coverW * COVER_RATIO;
  const rowH = clothH + CAPTION_H + ROW_GAP;
  const spineRowH = SPINE_MAX_H + BOARD_H + SPINE_ROW_GAP;
  const sizes: Record<string, Size> = {};
  for (const id of ids) sizes[id] = spineSize(id);
  const now: Layout = { view: view === 'covers' ? 0 : 1, width, perRow, coverW, clothH, rowH, spineRowH, sizes };
  const rows =
    view === 'covers' ? Math.max(1, Math.ceil(ids.length / perRow)) : ids.length ? place(ids[ids.length - 1], ids, now).row + 1 : 1;

  // The one book wearing the reading ribbon: the last one read.
  const reading = readingNow(books);
  const order = useSharedValue<string[]>(ids);
  const layout = useSharedValue<Layout>(now);
  const active = useSharedValue<string | null>(null);
  const drop = useSharedValue({ n: 0, id: '' });
  const shared: Shared = { order, layout, active, drop };
  const reduced = useReducedMotion();
  // A drag ends with a click on the web: it mustn't open the book.
  const justDragged = useRef(false);

  useEffect(() => {
    order.set(key ? key.split(',') : []);
  }, [key, order]);
  useEffect(() => {
    const s: Record<string, Size> = {};
    for (const id of key ? key.split(',') : []) s[id] = spineSize(id);
    layout.set({ view: view === 'covers' ? 0 : 1, width, perRow, coverW, clothH, rowH, spineRowH, sizes: s });
  }, [view, width, perRow, coverW, clothH, rowH, spineRowH, key, layout]);

  const move = (id: string, to: CoverMove) => {
    const from = ids.indexOf(id);
    const next = to === 'earlier' ? from - 1 : from + 1;
    if (from < 0 || next < 0 || next >= ids.length) return;
    const reordered = moved(ids, from, next);
    order.set(reordered);
    onArrange(reordered);
  };
  const dragStarted = () => {
    justDragged.current = true;
  };
  const dragEnded = (next: string[]) => {
    if (next.join(',') !== key) onArrange(next);
    setTimeout(() => {
      justDragged.current = false;
    }, 300);
  };
  const press = (book: Textbook) => {
    if (!justDragged.current) onOpen(book);
  };

  return (
    <View>
      <View style={styles.bar}>
        <View style={styles.views} accessibilityRole="radiogroup" accessibilityLabel="Show books as">
          {(['covers', 'spines'] as const).map((v) => (
            <Pressable key={v} accessibilityRole="radio" accessibilityState={{ checked: view === v }} aria-checked={view === v}
              accessibilityLabel={v === 'covers' ? 'Covers' : 'Spines'} onPress={() => onView(v)}
              style={({ pressed }) => [styles.viewOption, pressed && styles.pressed]}>
              <Text style={[styles.viewText, view === v && styles.viewTextOn]}>{v === 'covers' ? 'Covers' : 'Spines'}</Text>
              <View style={[styles.viewMark, view === v && styles.viewMarkOn]} />
            </Pressable>
          ))}
        </View>
      </View>
      <View style={styles.barGap} />

      <View style={{ height: view === 'covers' ? rows * rowH - ROW_GAP : rows * spineRowH - SPINE_ROW_GAP }}>
        {view === 'spines' ? Array.from({ length: rows }, (_, r) => <Ledge key={r} top={r * spineRowH + SPINE_MAX_H} />) : null}
        {books.map((book) => (
          <ShelfBook key={book.id} book={book} home={place(book.id, ids, now)} view={view} coverW={coverW} clothH={clothH}
            size={sizes[book.id]} shared={shared} reduced={reduced} reading={reading === book.id}
            onPress={() => press(book)} onMove={(to) => move(book.id, to)} onDragStart={dragStarted} onDragEnd={dragEnded} />
        ))}
      </View>
    </View>
  );
}

/** The fine ledge a row of spines stands on: a hairline with a soft shadow under it. It never moves. */
function Ledge({ top }: { top: number }) {
  return <View style={[styles.ledge, { top }]} accessible={false} />;
}

function ShelfBook({
  book,
  home,
  view,
  coverW,
  clothH,
  size,
  shared,
  reduced,
  reading,
  onPress,
  onMove,
  onDragStart,
  onDragEnd,
}: {
  book: Textbook;
  home: { x: number; y: number };
  view: ShelfView;
  coverW: number;
  clothH: number;
  size: Size;
  shared: Shared;
  reduced: boolean;
  reading: boolean;
  onPress: () => void;
  onMove: (to: CoverMove) => void;
  onDragStart: () => void;
  onDragEnd: (ids: string[]) => void;
}) {
  const { order, layout, active, drop } = shared;
  const id = book.id;
  const spines = view === 'spines';
  const x = useSharedValue(home.x);
  const y = useSharedValue(home.y);
  const startX = useSharedValue(0);
  const startY = useSharedValue(0);
  const lift = useSharedValue(0);
  const tilt = useSharedValue(0);
  const nudge = useSharedValue(0);
  const hover = useSharedValue(0);
  const press = useSharedValue(0);
  /** Counts down for a moment after a drop. On the web, hover ends and starts again when the button comes up; that
   * restart mustn't pull the book back out of the place it just landed in. */
  const landing = useSharedValue(0);

  // Glide to wherever this book's place is now, unless it's the one in hand. Only when the place changes: the saved
  // order coming back after a drop mustn't restart the landing.
  useAnimatedReaction(
    () => place(id, order.get(), layout.get()),
    (to, prev) => {
      if (active.get() === id || (prev && to.x === prev.x && to.y === prev.y)) return;
      x.set(withSpring(to.x, GLIDE));
      y.set(withSpring(to.y, GLIDE));
    },
  );

  // A drop knocks the books beside it, a quick tap that goes straight back: covers, the next one along; spines, the
  // next three, each a little less and a beat later.
  useAnimatedReaction(
    () => drop.get(),
    (d, prev) => {
      if (reduced || !prev || d.n === prev.n || d.id === id) return;
      const l = layout.get();
      const o = order.get();
      if (place(id, o, l).row !== place(d.id, o, l).row) return;
      const steps = o.indexOf(id) - o.indexOf(d.id);
      if (Math.abs(steps) > (l.view === 0 ? 1 : 3)) return;
      const away = Math.sign(steps) * (l.view === 0 ? 1.5 : 2.5 / Math.abs(steps));
      nudge.set(withDelay((Math.abs(steps) - 1) * 30, withSequence(withTiming(away, { duration: 50 }), withSpring(0, SETTLE))));
    },
  );

  const pan = usePanGesture({
    minDistance: 4,
    activateAfterLongPress: Platform.OS === 'web' ? 0 : 220,
    // A touch presses the book in a little; picking it up lifts it out of that.
    onBegin: () => {
      press.set(withTiming(1, { duration: 70 }));
    },
    onActivate: () => {
      active.set(id);
      startX.set(x.get());
      startY.set(y.get());
      lift.set(withSpring(1, LIFT));
      scheduleOnRN(onDragStart);
    },
    onUpdate: (e) => {
      x.set(startX.get() + e.translationX);
      y.set(startY.get() + e.translationY);
      // Held from above, a book leans a little behind the hand, more the faster it moves, and stands straight again
      // the moment the hand stops.
      if (!reduced) {
        const lean = Math.max(-6, Math.min(6, e.velocityX / 100));
        tilt.set(withSequence(withTiming(lean, { duration: 60 }), withSpring(0, SETTLE)));
      }
      const l = layout.get();
      const o = order.get();
      const from = o.indexOf(id);
      const s = l.sizes[id];
      const cx = x.get() + (l.view === 0 ? l.coverW : s.w) / 2;
      const cy = y.get() + (l.view === 0 ? l.clothH : s.h) / 2;
      const to = target(id, cx, cy, o, l);
      if (to !== from) order.set(moved(o, from, to));
    },
    onFinalize: () => {
      press.set(withSpring(0, LIFT));
      if (active.get() !== id) return;
      const l = layout.get();
      const o = order.get();
      const to = place(id, o, l);
      x.set(withSpring(to.x, SETTLE));
      y.set(withSpring(to.y, SETTLE));
      lift.set(withSpring(0, SETTLE));
      // It stays where it lands: no hover pull-out under the pointer that dropped it.
      hover.set(0);
      landing.set(withSequence(withTiming(1, { duration: 0 }), withTiming(0, { duration: 250 })));
      tilt.set(reduced ? 0 : withSpring(0, SETTLE));
      active.set(null);
      drop.set({ n: drop.get().n + 1, id });
      scheduleOnRN(onDragEnd, o);
    },
  });
  const hoverGesture = useHoverGesture({
    onActivate: () => {
      if (!reduced && landing.get() === 0) hover.set(withSpring(1, LIFT));
    },
    onFinalize: () => {
      hover.set(withSpring(0, LIFT));
    },
  });
  const gesture = useSimultaneousGestures(pan, hoverGesture);

  const style = useAnimatedStyle(() => {
    const held = active.get() === id;
    const up = spines ? 14 : 10;
    const pulled = held ? 0 : hover.get() * (spines ? 8 : 5);
    return {
      zIndex: held ? 100 : 1,
      transform: [
        { translateX: x.get() },
        { translateY: y.get() - lift.get() * up - pulled },
        { scale: 1 + lift.get() * (spines ? 0.04 : 0.06) - press.get() * 0.025 * (1 - lift.get()) },
        { rotateZ: `${tilt.get()}deg` },
      ],
    };
  });
  // Knocks pivot on the shelf, at the book's foot.
  const knock = useAnimatedStyle(() => ({ transform: [{ rotateZ: `${nudge.get()}deg` }] }));
  const shadow = useAnimatedStyle(() => ({ opacity: Math.max(lift.get(), hover.get() * 0.4) }));

  return (
    <Animated.View style={[styles.book, { width: spines ? size.w : coverW, transformOrigin: spines ? '50% 0%' : '50% 50%' }, style]}>
      <GestureDetector gesture={gesture} userSelect="none" touchAction="pan-y">
        <Animated.View style={[{ transformOrigin: '50% 100%' }, knock]}>
          <Animated.View pointerEvents="none"
            style={[styles.liftShadow, { height: spines ? size.h : clothH, borderRadius: spines ? 2 : 3 }, shadow]} />
          {spines ? (
            <Spine book={book} size={size} reading={reading} onPress={onPress} onMove={onMove} />
          ) : (
            <Cover book={book} width={coverW} onPress={onPress} onMove={onMove} reading={reading} dimOnPress={false} />
          )}
        </Animated.View>
      </GestureDetector>
    </Animated.View>
  );
}

/**
 * Type for a title down a spine, as a binder would set it: as large as fits on one line (15 down to 13), else two
 * lines across the spine, else two smaller lines that end in "…". Width is estimated from the serif's average glyph.
 */
function spineType(title: string, length: number) {
  const glyph = 0.6;
  for (let size = 15; size >= 13; size--) if (title.length * size * glyph <= length) return { fontSize: size, lineHeight: size + 3, lines: 1 };
  if (title.length * 13 * glyph <= length * 1.8) return { fontSize: 13, lineHeight: 15, lines: 2 };
  return { fontSize: 12, lineHeight: 14, lines: 2 };
}

/**
 * A book standing in the library, spine out: its cloth in leather or linen, rounded edges, bands, the title down the
 * spine, the volume, and the reading ribbon if it's the book being read.
 */
function Spine({ book, size, reading, onPress, onMove }: {
  book: Textbook; size: Size; reading: boolean; onPress: () => void; onMove: (to: CoverMove) => void;
}) {
  const { label, hint } = bookLabel(book, reading);
  const titleLength = size.h - 58;
  const set = spineType(book.title, titleLength);
  const ink = titleColorOn(book.coverColor);
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={label} accessibilityHint={hint}
      accessibilityActions={MOVE_ACTIONS}
      onAccessibilityAction={(e: AccessibilityActionEvent) => onMove(e.nativeEvent.actionName === 'moveEarlier' ? 'earlier' : 'later')}
      style={{ width: size.w, height: size.h }}>
      <View style={[styles.spine, { backgroundColor: book.coverColor }]}>
        <MaterialGrain cloth={book.coverColor} width={size.w} height={size.h} cut={book.title} radius={2} />
        {reading ? <View style={styles.ribbon} /> : null}
        <View style={styles.edgeLight} />
        <View style={styles.edgeShade} />
        <View style={[styles.band, { top: 12 }]} />
        <View style={[styles.band, { top: 16 }]} />
        <View style={[styles.spineTitleBox, { width: titleLength, height: size.w, left: (size.w - titleLength) / 2, top: (size.h - size.w) / 2 - 10 }]}>
          <Text style={[styles.spineTitle, { color: ink, fontSize: set.fontSize, lineHeight: set.lineHeight }]} numberOfLines={set.lines}>
            {book.title}
          </Text>
        </View>
        <View style={[styles.band, { bottom: 30 }]} />
        <Text style={[styles.spineVolume, { color: ink }]}>{volumeNumeral(book.volume)}</Text>
      </View>
    </Pressable>
  );
}

const styles = themed(() => StyleSheet.create({
  bar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  views: { flexDirection: 'row', gap: 4 },
  viewOption: { minHeight: minTouch, minWidth: minTouch, paddingHorizontal: 10, justifyContent: 'center', alignItems: 'center' },
  viewText: { ...type.meta, fontFamily: fonts.sansMedium, fontSize: 14 },
  viewTextOn: { color: colors.ink, fontFamily: fonts.sansSemiBold },
  // The current view is underlined with a short green line, the way a contents page marks where you are.
  viewMark: { height: 2, width: 18, marginTop: 4, borderRadius: 1, backgroundColor: 'transparent' },
  viewMarkOn: { backgroundColor: colors.accent },
  barGap: { height: 20 },
  ledge: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 1,
    backgroundColor: colors.control,
    boxShadow: '0px 3px 6px rgba(41, 40, 35, 0.18)',
  },
  book: { position: 'absolute', left: 0, top: 0 },
  liftShadow: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    boxShadow: '0px 18px 28px rgba(41, 40, 35, 0.3)',
  },
  pressed: { opacity: 0.75 },
  spine: { flex: 1, borderRadius: 2, boxShadow: '1px 2px 4px rgba(41, 40, 35, 0.22)' },
  // A little light on one edge and shade on the other, so the spine reads as rounded.
  edgeLight: { position: 'absolute', left: 0, top: 0, bottom: 0, width: 4, backgroundColor: 'rgba(255,255,255,0.12)' },
  edgeShade: { position: 'absolute', right: 0, top: 0, bottom: 0, width: 5, backgroundColor: 'rgba(0,0,0,0.18)' },
  band: { position: 'absolute', left: 4, right: 5, height: 1, backgroundColor: 'rgba(247,244,236,0.5)' },
  spineTitleBox: { position: 'absolute', justifyContent: 'center', transform: [{ rotate: '90deg' }] },
  spineTitle: { fontFamily: fonts.serifSemiBold, textAlign: 'center' },
  spineVolume: {
    position: 'absolute',
    bottom: 9,
    left: 0,
    right: 0,
    textAlign: 'center',
    fontFamily: fonts.sansSemiBold,
    fontSize: 10,
    letterSpacing: 1,
  },
  // The reading ribbon: a bookmark poking out of the top of the book being read.
  ribbon: { position: 'absolute', top: -10, right: 12, width: 7, height: 22, backgroundColor: colors.ribbon, borderRadius: 1 },
}));
