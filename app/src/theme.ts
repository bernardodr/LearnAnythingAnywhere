/**
 * Design tokens: the colours, fonts and type scale of Warm Editorial Minimalism (`docs/DESIGN.md`, the owner's brief).
 * Screens use these and never raw values, so a change here changes the whole app. Contrast ratios are in DESIGN.md.
 */
export const colors = {
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
  /** The ribbon bookmark on a book whose preface is still in progress. */
  ribbon: '#A9533A',
  /**
   * Cover cloths (DESIGN.md "Books"). `ochre` takes an ink title; the rest take ivory. Keep this order: books get them in
   * turn by volume, as the server's `Textbook.COVER_COLORS` does (`CoverColorsTest` checks the two lists match).
   */
  covers: {
    forest: '#2F4A3B',
    ochre: '#D4A548',
    blue: '#46668A',
    terracotta: '#A9533A',
  },
} as const;

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
export const type = {
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
} as const;

/** Minimum touch target. */
export const minTouch = 44;

/** Corner radius for buttons, fields and panels: modestly rounded. */
export const radius = 10;
