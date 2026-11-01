// Design tokens from docs/BRIEF.md section 9. Change values here, not in screens.
export const colors = {
  paper: '#FFFDF7',
  ink: '#1F1B16',
  muted: '#5E574D',
  rule: '#E6DFD0',
  accent: '#9B2C1F',
  noteFill: '#F2EEE4',
  ribbon: '#C9962B',
  covers: {
    rust: '#9B2C1F',
    slate: '#3E4C5E',
    olive: '#5A6B4A',
    terracotta: '#B4552F',
    navy: '#23466B',
  },
} as const;

export const fonts = {
  serif: 'Newsreader_400Regular',
  serifItalic: 'Newsreader_400Regular_Italic',
  serifSemiBold: 'Newsreader_600SemiBold',
  sans: 'Inter_400Regular',
  sansSemiBold: 'Inter_600SemiBold',
} as const;

export const type = {
  body: { fontFamily: fonts.serif, fontSize: 17, lineHeight: 17 * 1.6, color: colors.ink },
  h1: { fontFamily: fonts.serifSemiBold, fontSize: 34, lineHeight: 34 * 1.2, color: colors.ink },
  h2: { fontFamily: fonts.serifSemiBold, fontSize: 23, lineHeight: 23 * 1.3, color: colors.ink },
  kicker: {
    fontFamily: fonts.sansSemiBold,
    fontSize: 12,
    letterSpacing: 1.3,
    textTransform: 'uppercase' as const,
    color: colors.muted,
  },
} as const;

/** Minimum touch target (BRIEF section 9). */
export const minTouch = 44;
