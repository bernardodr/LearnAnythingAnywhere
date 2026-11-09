import { router } from 'expo-router';
import { type ReactNode, useRef } from 'react';
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

import { colors, fonts, minTouch, type } from '@/theme';

/**
 * A page of the book: paper background, one readable column. `stickToBottom` keeps the newest content in view
 * as it grows (a streaming conversation).
 */
export function Page({
  children,
  center = false,
  stickToBottom = false,
}: {
  children: ReactNode;
  center?: boolean;
  stickToBottom?: boolean;
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
          <View style={styles.column}>{children}</View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

/** Back, or to the shelf when there's no history (a reloaded page on web, a deep link). */
export function goBack() {
  if (router.canGoBack()) router.back();
  else router.replace('/library');
}

export function Ornament({ width = 200 }: { width?: number }) {
  return (
    <View style={[styles.ornament, { width }]} accessible={false} importantForAccessibility="no-hide-descendants">
      <View style={styles.rule} />
      <Text style={styles.fleuron}>❦</Text>
      <View style={styles.rule} />
    </View>
  );
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
        <Text style={[styles.buttonText, variant !== 'primary' && styles.buttonTextAlt]}>{label}</Text>
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

/** A quiet boxed message: errors, notes from the topic gate. */
export function Note({ children, tone = 'note' }: { children: ReactNode; tone?: 'note' | 'error' }) {
  return (
    <View style={[styles.note, tone === 'error' && styles.noteError]} accessibilityRole={tone === 'error' ? 'alert' : undefined}>
      <Text style={styles.noteText}>{children}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.paper },
  page: { flexGrow: 1, alignItems: 'center', paddingHorizontal: 24, paddingVertical: 32 },
  pageCenter: { justifyContent: 'center' },
  column: { maxWidth: 560, width: '100%' },
  ornament: { flexDirection: 'row', alignItems: 'center', alignSelf: 'center', marginVertical: 24 },
  rule: { flex: 1, height: StyleSheet.hairlineWidth, backgroundColor: colors.rule },
  fleuron: { fontFamily: fonts.serif, fontSize: 18, color: colors.accent, marginHorizontal: 12 },
  button: {
    minHeight: minTouch,
    minWidth: minTouch,
    paddingHorizontal: 22,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 4,
  },
  primary: { backgroundColor: colors.accent },
  secondary: { borderWidth: 1, borderColor: colors.accent, backgroundColor: colors.paper },
  quiet: { backgroundColor: 'transparent', paddingHorizontal: 8 },
  inactive: { opacity: 0.55 },
  pressed: { opacity: 0.8 },
  buttonText: { fontFamily: fonts.sansSemiBold, fontSize: 15, color: colors.paper, letterSpacing: 0.4 },
  buttonTextAlt: { color: colors.accent },
  field: { marginBottom: 18 },
  label: { ...type.kicker, marginBottom: 8 },
  input: {
    minHeight: minTouch,
    borderWidth: 1,
    borderColor: colors.rule,
    borderRadius: 4,
    paddingHorizontal: 14,
    paddingVertical: 10,
    backgroundColor: '#FFFFFF',
    fontFamily: fonts.serif,
    fontSize: 17,
    color: colors.ink,
  },
  inputMulti: { minHeight: 96, textAlignVertical: 'top' },
  hint: { fontFamily: fonts.sans, fontSize: 13, color: colors.muted, marginTop: 6 },
  note: { backgroundColor: colors.noteFill, borderRadius: 4, padding: 16, marginVertical: 12 },
  noteError: { borderLeftWidth: 3, borderLeftColor: colors.accent },
  noteText: { ...type.body, fontSize: 16, lineHeight: 16 * 1.5 },
});
