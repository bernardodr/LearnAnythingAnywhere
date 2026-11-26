/**
 * Design tokens: the colours, fonts and type scale of Warm Editorial Minimalism (`docs/DESIGN.md`, the owner's brief),
 * in two palettes: light (the brief's ivory page) and dark (issue #85: a warm near-black page with ivory ink). Screens
 * use these and never raw values, so a change here changes the whole app. Contrast ratios are in DESIGN.md.
 *
 * How the two palettes reach the screens without rewriting every one:
 * - `colors` and `type` are read when they're used, from whichever palette is current (`setScheme`, called by the root
 *   layout from the device's setting or the learner's choice).
 * - Each file's styles are made once per palette with `themed(() => StyleSheet.create({...}))`, and `styles.x` gives
 *   the current palette's. The root layout redraws the screens when the palette changes, so everything picks it up.
 */

/** The light palette: the brief's. */
const LIGHT = {
  /** Warm ivory: every screen's background. */
  paper: '#F7F4EC',
  /** Dark ink: text. */
  ink: '#292823',
  /** Metadata, captions and hints (5.6:1 on paper). */
  muted: '#66615A',
  /** Fine dividers and field borders. */
  rule: '#E3DDCF',
  /** Controls drawn on paper (a switch's track when off): at least 3:1 against paper. */
  control: '#8A8476',
  /** Forest green: the one dominant action on a screen, links, and the current choice. */
  accent: '#384E41',
  /** Quiet panels: notes in the margin, the mission card. */
  noteFill: '#EFEADF',
  /** A field's fill: a touch lighter than the page, so it reads as a place to type. */
  field: '#FBF9F4',
  /** Errors (6.3:1 on paper). */
  danger: '#9A3B26',
  /** The reading ribbon: the bookmark on the book read last. */
  ribbon: '#A9533A',
  /** A shadow's colour, as "r, g, b" for rgba(): warm ink on ivory. */
  shadow: '41, 40, 35',
  /**
   * Cover cloths (DESIGN.md "Books"), the same in both palettes: books are things, not chrome. `ochre` takes an ink
   * title; the rest take ivory. Keep this order: books get them in turn by volume, as the server's
   * `Textbook.COVER_COLORS` does (`CoverColorsTest` checks the two lists match).
   */
  covers: {
    forest: '#2F4A3B',
    ochre: '#D4A548',
    blue: '#46668A',
    terracotta: '#A9533A',
  },
};

export type Palette = typeof LIGHT;

/**
 * The dark palette (issue #85): the same warmth at night. A warm near-black page, ivory ink, and a lighter, sage forest
 * for the one thing to do (dark text on it). Every text pair is 4.5:1 or more (DESIGN.md has the ratios).
 */
const DARK: Palette = {
  paper: '#1B1A17',
  ink: '#EEEAE0',
  muted: '#ADA79B',
  rule: '#3A372F',
  control: '#7E786C',
  accent: '#93B79E',
  noteFill: '#2A2823',
  field: '#23211D',
  danger: '#E79A80',
  ribbon: '#C25F43',
  shadow: '0, 0, 0',
  covers: LIGHT.covers,
};

/** Light or dark. */
export type Scheme = 'light' | 'dark';

let current: Scheme = 'light';

/** The palette in use. */
export function scheme(): Scheme {
  return current;
}

/** Switches the palette for everything read from now on (the root layout calls it, then redraws the screens). */
export function setScheme(next: Scheme) {
  current = next;
}

/** An object whose properties are read from one of two versions, whichever matches the current palette. */
function live<T extends object>(light: T, dark: T): T {
  return new Proxy(light, { get: (_, key) => Reflect.get(current === 'dark' ? dark : light, key) });
}

/**
 * Makes something once per palette and hands back the current one's, property by property: wrap each file's styles,
 * `themed(() => StyleSheet.create({...}))`, and `styles.title` follows the palette.
 */
export function themed<T extends object>(make: () => T): T {
  const saved = current;
  current = 'light';
  const light = make();
  current = 'dark';
  const dark = make();
  current = saved;
  return live(light, dark);
}

/** The current palette's colours. */
export const colors: Palette = live(LIGHT, DARK);

/** Cover titles never change with the palette: ink on ochre, ivory on the darker cloths, as the cloth needs. */
export const coverInk = { ink: LIGHT.ink, ivory: LIGHT.paper } as const;

export const fonts = {
  serif: 'SourceSerif4_400Regular',
  serifItalic: 'SourceSerif4_400Regular_Italic',
  serifSemiBold: 'SourceSerif4_600SemiBold',
  serifSemiBoldItalic: 'SourceSerif4_600SemiBold_Italic',
  sans: 'Inter_400Regular',
  sansMedium: 'Inter_500Medium',
  sansSemiBold: 'Inter_600SemiBold',
} as const;

/** The type scale (DESIGN.md "Type"). Reading text is serif; controls and metadata are Inter. */
export const type = themed(() => ({
  /** A screen's title: "Your library". */
  display: { fontFamily: fonts.serifSemiBold, fontSize: 40, lineHeight: 44, letterSpacing: -0.4, color: colors.ink },
  h1: { fontFamily: fonts.serifSemiBold, fontSize: 32, lineHeight: 38, letterSpacing: -0.2, color: colors.ink },
  h2: { fontFamily: fonts.serifSemiBold, fontSize: 24, lineHeight: 31, color: colors.ink },
  h3: { fontFamily: fonts.serifSemiBold, fontSize: 19, lineHeight: 26, color: colors.ink },
  /** Reading text. */
  body: { fontFamily: fonts.serif, fontSize: 18, lineHeight: 30, color: colors.ink },
  /** Shorter serif text: captions, notes, the mission. */
  small: { fontFamily: fonts.serif, fontSize: 16, lineHeight: 24, color: colors.ink },
  /** Labels, buttons and metadata. */
  ui: { fontFamily: fonts.sans, fontSize: 15, lineHeight: 21, color: colors.ink },
  meta: { fontFamily: fonts.sans, fontSize: 13, lineHeight: 18, color: colors.muted },
  /** The small, widely spaced capitals above a title: "LEARN ANYTHING ANYWHERE", "CHAPTER ONE". */
  kicker: {
    fontFamily: fonts.sansSemiBold,
    fontSize: 11,
    lineHeight: 16,
    letterSpacing: 2.4,
    textTransform: 'uppercase' as const,
    color: colors.muted,
  },
}));

/** Minimum touch target. */
export const minTouch = 44;

/** Corner radius for buttons, fields and panels: modestly rounded. */
export const radius = 10;
