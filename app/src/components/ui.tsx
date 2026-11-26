import { router } from 'expo-router';
import { type ReactNode, useRef, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  type ScrollViewInstance,
  StyleSheet,
  Text,
  TextInput,
  type TextInputProps,
  View,
  type ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';

import { colors, fonts, minTouch, radius, themed, type } from '@/theme';

/**
 * A screen (`docs/DESIGN.md`): an even ivory background and one readable column with generous space. `stickToBottom`
 * keeps the newest content in view as it grows (a streaming conversation). `footer` pins the screen's one dominant
 * action to the bottom, above the scrolling page, the way the library's "Create a textbook" sits.
 */
export function Page({
  children,
  center = false,
  stickToBottom = false,
  wide = false,
  footer,
}: {
  children: ReactNode;
  center?: boolean;
  stickToBottom?: boolean;
  /** A wide working page (the owner console) instead of the reading column. */
  wide?: boolean;
  /** The screen's main action, pinned to the bottom (usually one primary `Button`). */
  footer?: ReactNode;
}) {
  const scroll = useRef<ScrollViewInstance>(null);
  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView style={styles.safe} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          ref={scroll}
          onContentSizeChange={() => stickToBottom && scroll.current?.scrollToEnd({ animated: true })}
          contentContainerStyle={[styles.page, center && styles.pageCenter]}
          keyboardShouldPersistTaps="handled">
          <View style={[styles.column, wide && styles.wide]}>{children}</View>
        </ScrollView>
        {footer ? (
          <View style={styles.footer}>
            <View style={[styles.column, wide && styles.wide]}>{footer}</View>
          </View>
        ) : null}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

export type Option<T extends string> = { value: T; label: string };

/**
 * A labelled dropdown: shows the current choice; tapping opens the options inline beneath it. No native picker,
 * so it looks and behaves the same on web, iOS and Android.
 */
export function Select<T extends string>({
  label,
  value,
  options,
  onChange,
  disabled,
  compact,
}: {
  label: string;
  value: T;
  options: Option<T>[];
  onChange: (value: T) => void;
  disabled?: boolean;
  /** Inside a table cell: no visible label (it's still the accessibility label). */
  compact?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const current = options.find((o) => o.value === value)?.label ?? value;
  return (
    <View style={compact ? styles.selectCompact : styles.field}>
      {compact ? null : <Text style={styles.label}>{label}</Text>}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${label}: ${current}`}
        accessibilityState={{ expanded: open, disabled: !!disabled }}
        disabled={disabled}
        onPress={() => setOpen(!open)}
        style={[styles.select, disabled && styles.inactive]}>
        <Text style={styles.selectText} numberOfLines={1}>
          {current}
        </Text>
        <Icon name={open ? 'chevronUp' : 'chevronDown'} size={18} color={colors.muted} />
      </Pressable>
      {open ? (
        <View style={styles.menu} accessibilityRole="menu">
          {options.map((o) => (
            <Pressable
              key={o.value}
              accessibilityRole="menuitem"
              accessibilityLabel={o.label}
              accessibilityState={{ selected: o.value === value }}
              onPress={() => {
                setOpen(false);
                if (o.value !== value) onChange(o.value);
              }}
              style={({ pressed }) => [styles.menuItem, o.value === value && styles.menuItemOn, pressed && { opacity: 0.7 }]}>
              <Text style={[styles.selectText, o.value === value && styles.selectTextOn]}>{o.label}</Text>
            </Pressable>
          ))}
        </View>
      ) : null}
    </View>
  );
}

/** Back, or to the library when there's no history (a reloaded page on web, a deep link). */
export function goBack() {
  if (router.canGoBack()) router.back();
  else router.replace('/library');
}

/**
 * The small ornament under a title on openings (the title page, a chapter opening, the mission card): two dots each
 * side of a four-pointed star, in forest green. Decorative, so screen readers skip it.
 */
export function Ornament() {
  return (
    <View style={styles.ornament} accessible={false} importantForAccessibility="no-hide-descendants">
      <Svg width={96} height={20} viewBox="0 0 96 20" aria-hidden>
        <Circle cx={18} cy={10} r={1.6} fill={colors.accent} />
        <Circle cx={30} cy={10} r={1.6} fill={colors.accent} />
        <Path d="M48 2 Q48 10 56 10 Q48 10 48 18 Q48 10 40 10 Q48 10 48 2 Z" fill={colors.accent} />
        <Circle cx={66} cy={10} r={1.6} fill={colors.accent} />
        <Circle cx={78} cy={10} r={1.6} fill={colors.accent} />
      </Svg>
    </View>
  );
}

/** The line icons the app uses (DESIGN.md: clear icons). */
export type IconName = 'back' | 'settings' | 'more' | 'close' | 'chevronRight' | 'chevronDown' | 'chevronUp' | 'plus' | 'check';

/**
 * A simple line icon in a 24 × 24 box. Decorative on its own: put it in an `IconButton` (or next to text) so it has a
 * label.
 */
export function Icon({ name, size = 24, color = colors.ink }: { name: IconName; size?: number; color?: string }) {
  const line = { stroke: color, strokeWidth: 1.6, fill: 'none', strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  const paths: Record<IconName, ReactNode> = {
    back: <Path d="M20 12 H4 M10 6 L4 12 L10 18" {...line} />,
    settings: (
      <>
        <Path d="M4 7 H20 M4 17 H20" {...line} />
        <Circle cx={9} cy={7} r={2.4} {...line} fill={colors.paper} />
        <Circle cx={15} cy={17} r={2.4} {...line} fill={colors.paper} />
      </>
    ),
    more: (
      <>
        <Circle cx={5} cy={12} r={1.4} fill={color} />
        <Circle cx={12} cy={12} r={1.4} fill={color} />
        <Circle cx={19} cy={12} r={1.4} fill={color} />
      </>
    ),
    close: <Path d="M6 6 L18 18 M18 6 L6 18" {...line} />,
    chevronRight: <Path d="M9 5 L16 12 L9 19" {...line} />,
    chevronDown: <Path d="M6 9 L12 15 L18 9" {...line} />,
    chevronUp: <Path d="M6 15 L12 9 L18 15" {...line} />,
    plus: <Path d="M12 5 V19 M5 12 H19" {...line} />,
    check: <Path d="M5 12.5 L10 17.5 L19 7" {...line} />,
  };
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessible={false} importantForAccessibility="no-hide-descendants" aria-hidden>
      {paths[name]}
    </Svg>
  );
}

/** An icon-only button: 44 × 44 to tap, with the label screen readers say (required, as there's no visible text). */
export function IconButton({ icon, label, onPress, color }: { icon: IconName; label: string; onPress: () => void; color?: string }) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} hitSlop={4}
      style={({ pressed }) => [styles.iconButton, pressed && styles.pressed]}>
      <Icon name={icon} color={color} />
    </Pressable>
  );
}

/**
 * The bar at the top of a screen you reach from somewhere else (DESIGN.md, the book-page mockup): a back arrow on the
 * left, and in the middle an optional kicker saying where you are ("Your library" on a book's page, the book's title
 * in its preface). The kicker only repeats what the back arrow's label says, so screen readers skip it.
 *
 * @param backLabel what the back arrow says to screen readers, e.g. "Back to your library"
 * @param onBack where it goes; by default back, or to the library when there's no history (`goBack`)
 * @param kicker the small centred line; leave it out for a bare back arrow
 * @param right one control at the top right, such as the ••• `IconButton` that opens a book's menu
 */
export function TopBar({ backLabel, onBack = goBack, kicker, right }: {
  backLabel: string;
  onBack?: () => void;
  kicker?: string;
  right?: ReactNode;
}) {
  return (
    <View style={styles.topBar}>
      <IconButton icon="back" label={backLabel} onPress={onBack} />
      <Text style={[type.kicker, styles.topBarKicker]} numberOfLines={1} importantForAccessibility="no" aria-hidden>
        {kicker ?? ''}
      </Text>
      {/* As wide as the arrow, so the kicker sits in the true middle of the screen. */}
      <View style={styles.topBarSide}>{right}</View>
    </View>
  );
}

/**
 * A note in the margin (DESIGN.md "Screens"): a fine green rule down the left, a small green kicker, then serif text,
 * with no box. For reflections and the learner's own words: their answers in the preface, the why of their mission.
 *
 * @param kicker the small capitals above the note, e.g. "You" or "Why you’re learning this"
 * @param children the note: a string is set in the margin's serif, anything else is shown as it is
 */
export function MarginNote({ kicker, children, style }: { kicker: string; children: ReactNode; style?: ViewStyle }) {
  return (
    <View style={[styles.marginNote, style]}>
      <Text style={[type.kicker, styles.marginKicker]}>{kicker}</Text>
      {typeof children === 'string' ? <Text style={styles.marginText}>{children}</Text> : children}
    </View>
  );
}

/** A fine horizontal divider. */
export function Divider({ style }: { style?: ViewStyle }) {
  return <View style={[styles.divider, style]} accessible={false} />;
}

export function Kicker({ children, style }: { children: ReactNode; style?: object }) {
  return <Text style={[type.kicker, style]}>{children}</Text>;
}

type ButtonProps = {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'quiet';
  disabled?: boolean;
  busy?: boolean;
  accessibilityLabel?: string;
  style?: ViewStyle;
};

/**
 * A button (DESIGN.md: modestly rounded, a small number of prominent controls). `primary` is the screen's one dominant
 * action: forest green, full width in the column. `secondary` is outlined and `quiet` is green text, for everything
 * else.
 */
export function Button({ label, onPress, variant = 'primary', disabled, busy, accessibilityLabel, style }: ButtonProps) {
  const inactive = disabled || busy;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ disabled: !!inactive, busy: !!busy }}
      disabled={inactive}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        variant === 'primary' && styles.primary,
        variant === 'secondary' && styles.secondary,
        variant === 'quiet' && styles.quiet,
        inactive && styles.inactive,
        pressed && styles.pressed,
        style,
      ]}>
      {busy ? (
        <ActivityIndicator color={variant === 'primary' ? colors.paper : colors.accent} />
      ) : (
        <Text style={[styles.buttonText, variant === 'secondary' && styles.buttonTextSecondary, variant === 'quiet' && styles.buttonTextQuiet]}>
          {label}
        </Text>
      )}
    </Pressable>
  );
}

/** A labelled text input: the label is visible and is also the input's accessibility label. */
export function Field({ label, hint, ...input }: TextInputProps & { label: string; hint?: string }) {
  return (
    <View style={styles.field}>
      <Text style={styles.label} nativeID={`label-${label}`}>
        {label}
      </Text>
      <TextInput
        accessibilityLabel={label}
        accessibilityHint={hint}
        aria-labelledby={`label-${label}`}
        placeholderTextColor={colors.muted}
        style={[styles.input, input.multiline && styles.inputMulti]}
        {...input}
      />
      {hint ? <Text style={styles.hint}>{hint}</Text> : null}
    </View>
  );
}

/**
 * A labelled password input with a Show / Hide switch. Pasting works, so password managers do too. `isNew` marks a
 * password being chosen (sign-up, a change), so browsers and phones offer to suggest and save one.
 */
export function PasswordField({
  label,
  hint,
  isNew,
  ...input
}: Omit<TextInputProps, 'secureTextEntry'> & { label: string; hint?: string; isNew?: boolean }) {
  const [shown, setShown] = useState(false);
  return (
    <View style={styles.field}>
      <Text style={styles.label} nativeID={`label-${label}`}>
        {label}
      </Text>
      <View style={styles.passwordRow}>
        <TextInput
          accessibilityLabel={label}
          accessibilityHint={hint}
          aria-labelledby={`label-${label}`}
          placeholderTextColor={colors.muted}
          style={[styles.input, styles.passwordInput]}
          secureTextEntry={!shown}
          autoCapitalize="none"
          autoCorrect={false}
          autoComplete={isNew ? 'new-password' : 'current-password'}
          textContentType={isNew ? 'newPassword' : 'password'}
          {...input}
        />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${shown ? 'Hide' : 'Show'} ${label.toLowerCase()}`}
          onPress={() => setShown(!shown)}
          style={({ pressed }) => [styles.reveal, pressed && styles.pressed]}>
          <Text style={styles.revealText}>{shown ? 'Hide' : 'Show'}</Text>
        </Pressable>
      </View>
      {hint ? <Text style={styles.hint}>{hint}</Text> : null}
    </View>
  );
}

/** A password's length as the server counts it: in characters, not UTF-16 units. */
export function passwordLength(password: string) {
  return [...password].length;
}

/** A quiet boxed message: errors, notes from the topic gate. */
export function Note({ children, tone = 'note' }: { children: ReactNode; tone?: 'note' | 'error' }) {
  return (
    <View style={[styles.note, tone === 'error' && styles.noteError]} accessibilityRole={tone === 'error' ? 'alert' : undefined}>
      <Text style={styles.noteText}>{children}</Text>
    </View>
  );
}

const styles = themed(() => StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.paper },
  page: { flexGrow: 1, alignItems: 'center', paddingHorizontal: 24, paddingTop: 20, paddingBottom: 40 },
  pageCenter: { justifyContent: 'center' },
  column: { maxWidth: 560, width: '100%' },
  wide: { maxWidth: 1100 },
  footer: { alignItems: 'center', paddingHorizontal: 24, paddingTop: 12, paddingBottom: 16, backgroundColor: colors.paper },
  selectCompact: { minWidth: 150 },
  select: {
    minHeight: minTouch,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderWidth: 1,
    borderColor: colors.rule,
    borderRadius: radius,
    paddingHorizontal: 14,
    backgroundColor: colors.field,
    gap: 8,
  },
  selectText: { ...type.ui, flexShrink: 1 },
  selectTextOn: { color: colors.accent, fontFamily: fonts.sansSemiBold },
  menu: { borderWidth: 1, borderColor: colors.rule, borderRadius: radius, backgroundColor: colors.field, marginTop: 6, overflow: 'hidden' },
  menuItem: { minHeight: minTouch, justifyContent: 'center', paddingHorizontal: 14 },
  menuItemOn: { backgroundColor: colors.noteFill },
  ornament: { alignSelf: 'center', marginVertical: 20 },
  iconButton: { width: minTouch, height: minTouch, justifyContent: 'center', alignItems: 'center', borderRadius: radius },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: colors.rule, alignSelf: 'stretch' },
  // The arrow's own padding is pulled into the gutter, so the arrow lines up with the text below it.
  topBar: { flexDirection: 'row', alignItems: 'center', marginHorizontal: -10, marginBottom: 12 },
  topBarKicker: { flex: 1, textAlign: 'center', color: colors.ink, letterSpacing: 3 },
  topBarSide: { width: minTouch },
  marginNote: { borderLeftWidth: 2, borderLeftColor: colors.accent, paddingLeft: 18, paddingVertical: 2 },
  marginKicker: { color: colors.accent, marginBottom: 6 },
  marginText: { ...type.small, fontSize: 17, lineHeight: 27 },
  button: {
    minHeight: minTouch,
    minWidth: minTouch,
    paddingHorizontal: 22,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: radius,
  },
  primary: { backgroundColor: colors.accent, minHeight: 52 },
  secondary: { borderWidth: 1, borderColor: colors.rule, backgroundColor: colors.field },
  quiet: { backgroundColor: 'transparent', paddingHorizontal: 10 },
  inactive: { opacity: 0.5 },
  pressed: { opacity: 0.8 },
  buttonText: { fontFamily: fonts.sansSemiBold, fontSize: 16, color: colors.paper, letterSpacing: 0.2 },
  buttonTextSecondary: { color: colors.ink },
  buttonTextQuiet: { color: colors.accent, fontSize: 15 },
  field: { marginBottom: 20 },
  label: { fontFamily: fonts.sansMedium, fontSize: 14, lineHeight: 20, color: colors.ink, marginBottom: 8 },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderColor: colors.rule,
    borderRadius: radius,
    paddingHorizontal: 14,
    paddingVertical: 11,
    backgroundColor: colors.field,
    fontFamily: fonts.serif,
    fontSize: 17,
    color: colors.ink,
  },
  inputMulti: { minHeight: 104, textAlignVertical: 'top' },
  passwordRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  passwordInput: { flex: 1 },
  reveal: { minHeight: minTouch, minWidth: minTouch + 12, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 8 },
  revealText: { fontFamily: fonts.sansSemiBold, fontSize: 14, color: colors.accent },
  hint: { ...type.meta, marginTop: 6 },
  note: { backgroundColor: colors.noteFill, borderRadius: radius, padding: 16, marginVertical: 12 },
  noteError: { backgroundColor: colors.paper, borderWidth: 1, borderColor: colors.rule, borderLeftWidth: 3, borderLeftColor: colors.danger },
  noteText: { ...type.small },
}));
