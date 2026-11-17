import { useState } from 'react';
import { Pressable, StyleSheet, Switch, Text, View } from 'react-native';

import { Button, Field, Kicker, Note } from '@/components/ui';
import { ApiError } from '@/lib/api';
import type { LinksPolicy, Profile, ProfileSettings } from '@/lib/types';
import { colors, fonts, minTouch, type } from '@/theme';

/** Shown under the age control; the server's wording (Profiles.readingLevel). */
export function readingLevel(age: number | null): string {
  if (age == null) return 'Adult';
  if (age <= 6) return 'Early reader (ages 5–6)';
  if (age <= 8) return 'Young reader (ages 7–8)';
  if (age <= 10) return 'Confident reader (ages 9–10)';
  if (age <= 12) return 'Older child (ages 11–12)';
  if (age <= 15) return 'Young teen (ages 13–15)';
  return 'Older teen (ages 16–18)';
}

const LINKS: { value: LinksPolicy; label: string; hint: string }[] = [
  { value: 'OFF', label: 'Off', hint: 'No links at all' },
  { value: 'ALLOWLIST', label: 'Allowlist', hint: 'Only links to sources you’ve added' },
  { value: 'OPEN', label: 'Open', hint: 'Links are allowed' },
];

/** react-native-web colours a switched-on thumb separately (its own prop, not in React Native's types). */
export const WEB_SWITCH = { activeThumbColor: colors.paper } as object;

/** How many tries before the answer can be revealed (BRIEF screen 7: "Reveal answer after 3 tries"). */
const REVEAL_AFTER = 3;

/**
 * Screen 7's form: child-safe switch, age (5 to Adult) with the reading level, "Leave out of lessons" switches and
 * extra topics, links policy, revealing the answer, and the parent PIN when it locks the settings. Set up for a child
 * without a PIN, it suggests setting one.
 */
export function SettingsForm({
  profile,
  saved,
  onEdit,
  onSave,
}: {
  profile: Profile;
  /**
   * Held by the screen: saving refreshes the profile, which remounts this form (it's keyed on the data), so a
   * "saved" message kept in here would vanish.
   */
  saved: boolean;
  onEdit: () => void;
  onSave: (settings: ProfileSettings, pin: string | null) => Promise<unknown>;
}) {
  const [s, setS] = useState<ProfileSettings>({
    age: profile.age,
    childSafe: profile.childSafe,
    categories: profile.categories,
    extras: profile.extras,
    linksPolicy: profile.linksPolicy,
    revealAfterAttempts: profile.revealAfterAttempts,
  });
  const [extra, setExtra] = useState('');
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const needsPin = profile.pinSet;
  const forAChild = s.childSafe || (s.age != null && s.age < 13);
  const change = (next: Partial<ProfileSettings>) => {
    setS((prev) => ({ ...prev, ...next }));
    setError(null);
    onEdit();
  };

  const addExtra = () => {
    const e = extra.replace(/\s+/g, ' ').trim();
    if (!e || s.extras.some((x) => x.toLowerCase() === e.toLowerCase())) return;
    change({ extras: [...s.extras, e] });
    setExtra('');
  };

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await onSave(s, needsPin ? pin : null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'The settings couldn’t be saved. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <View>
      <ToggleRow label="Child-safe mode" hint="Leaves out everything listed below and shows replies only once they’ve been checked."
        value={s.childSafe} onChange={(v) => change({ childSafe: v })} />

      <AgeStepper age={s.age} min={5} allowAdult onChange={(age) => change({ age })} />
      {forAChild && !needsPin ? (
        <Note>Setting LAA up for a child? Set a parent PIN below, so these settings can only be changed with it.</Note>
      ) : null}

      <Kicker style={styles.section}>Leave out of lessons</Kicker>
      {profile.allCategories.map((c) => (
        <ToggleRow key={c.key} label={capitalise(c.label)} disabled={s.childSafe}
          value={s.childSafe || s.categories.includes(c.key)}
          onChange={(on) => change({ categories: on ? [...s.categories, c.key] : s.categories.filter((k) => k !== c.key) })} />
      ))}
      {s.childSafe ? <Text style={styles.hint}>Child-safe mode leaves all of these out.</Text> : null}

      {s.extras.length > 0 ? (
        <View style={styles.chips}>
          {s.extras.map((e) => (
            <View key={e} style={styles.chip}>
              <Text style={styles.chipText}>{e}</Text>
              <Pressable accessibilityRole="button" accessibilityLabel={`Stop leaving out ${e}`} hitSlop={8}
                onPress={() => change({ extras: s.extras.filter((x) => x !== e) })} style={styles.chipRemove}>
                <Text style={styles.chipRemoveText}>×</Text>
              </Pressable>
            </View>
          ))}
        </View>
      ) : null}
      <View style={styles.addRow}>
        <View style={{ flex: 1 }}>
          <Field label="Also leave out" value={extra} onChangeText={setExtra} maxLength={40} onSubmitEditing={addExtra}
            placeholder="For example: spiders" returnKeyType="done" />
        </View>
        <Button label="Add" variant="secondary" onPress={addExtra} disabled={!extra.trim()} style={styles.addButton}
          accessibilityLabel="Add to the topics to leave out" />
      </View>

      <Text style={styles.label} nativeID="links-label">Links in lessons and replies</Text>
      <View style={styles.radios} accessibilityRole="radiogroup" aria-labelledby="links-label">
        {LINKS.map((o) => (
          <Pressable key={o.value} accessibilityRole="radio" accessibilityState={{ checked: s.linksPolicy === o.value }}
            aria-checked={s.linksPolicy === o.value} accessibilityLabel={`${o.label}: ${o.hint}`}
            onPress={() => change({ linksPolicy: o.value })} style={[styles.radio, s.linksPolicy === o.value && styles.radioOn]}>
            <Text style={[styles.radioText, s.linksPolicy === o.value && styles.radioTextOn]}>{o.label}</Text>
            <Text style={styles.radioHint}>{o.hint}</Text>
          </Pressable>
        ))}
      </View>

      <ToggleRow label={`Reveal answer after ${REVEAL_AFTER} tries`}
        hint="Once every hint has been used, the model answer can be shown. Off: the tutor never gives it away."
        value={s.revealAfterAttempts != null} onChange={(on) => change({ revealAfterAttempts: on ? REVEAL_AFTER : null })} />

      {needsPin ? (
        <Field label="Parent PIN" value={pin} onChangeText={(t) => setPin(t.replace(/\D/g, ''))} secureTextEntry
          inputMode="numeric" keyboardType="number-pad" maxLength={8} hint="Changing these settings needs the parent PIN." />
      ) : null}
      {error ? <Note tone="error">{error}</Note> : null}
      {saved && !error ? <Note>Saved. New lessons and replies follow these settings.</Note> : null}
      <Button label="Save settings" onPress={() => void save()} busy={busy} disabled={needsPin && pin.length < 4}
        style={styles.save} />
    </View>
  );
}

function ToggleRow({ label, hint, value, onChange, disabled }: {
  label: string; hint?: string; value: boolean; onChange: (v: boolean) => void; disabled?: boolean;
}) {
  return (
    <View style={styles.toggle}>
      <View style={styles.toggleText}>
        <Text style={[styles.toggleLabel, disabled && styles.dim]}>{label}</Text>
        {hint ? <Text style={styles.hint}>{hint}</Text> : null}
      </View>
      <Switch value={value} onValueChange={onChange} disabled={disabled} accessibilityLabel={label} accessibilityHint={hint}
        trackColor={{ true: colors.accent, false: colors.control }} thumbColor={colors.paper} {...WEB_SWITCH} />
    </View>
  );
}

/** The age "slider" as an adjustable stepper: − and + buttons, and swipe up/down with a screen reader. */
function AgeStepper({ age, min, allowAdult, onChange }: {
  age: number | null; min: number; allowAdult: boolean; onChange: (age: number | null) => void;
}) {
  const steps: (number | null)[] = [];
  for (let a = min; a <= 18; a++) steps.push(a);
  if (allowAdult) steps.push(null);
  const at = Math.max(0, steps.indexOf(age));
  const label = age == null ? 'Adult' : `${age}`;
  const move = (by: number) => {
    const next = steps[Math.min(steps.length - 1, Math.max(0, at + by))];
    if (next !== age) onChange(next);
  };
  return (
    <View style={styles.ageBlock}>
      <Text style={styles.label} nativeID="age-label">Age</Text>
      <View
        style={styles.stepper}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Age"
        accessibilityValue={{ text: `${label}, ${readingLevel(age)}` }}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(e) => move(e.nativeEvent.actionName === 'increment' ? 1 : -1)}>
        <Pressable accessibilityRole="button" accessibilityLabel="Younger" disabled={at === 0} onPress={() => move(-1)}
          style={[styles.step, at === 0 && styles.dim]}>
          <Text style={styles.stepText}>−</Text>
        </Pressable>
        <View style={styles.track} importantForAccessibility="no-hide-descendants">
          <View style={[styles.fill, { width: `${(at / Math.max(1, steps.length - 1)) * 100}%` }]} />
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Older" disabled={at === steps.length - 1} onPress={() => move(1)}
          style={[styles.step, at === steps.length - 1 && styles.dim]}>
          <Text style={styles.stepText}>+</Text>
        </Pressable>
      </View>
      <Text style={styles.ageValue}>
        {label}
        {age != null ? <Text style={styles.ageLevel}> · {readingLevel(age)}</Text> : null}
      </Text>
    </View>
  );
}

function capitalise(s: string) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

const styles = StyleSheet.create({
  section: { marginTop: 20, marginBottom: 4 },
  label: { ...type.kicker, marginTop: 18, marginBottom: 8 },
  hint: { fontFamily: fonts.sans, fontSize: 13, lineHeight: 18, color: colors.muted, marginTop: 2 },
  dim: { opacity: 0.55 },
  toggle: {
    minHeight: minTouch,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
    paddingVertical: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.rule,
  },
  toggleText: { flex: 1 },
  toggleLabel: { fontFamily: fonts.sans, fontSize: 16, color: colors.ink },
  ageBlock: { marginTop: 4 },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  step: {
    width: minTouch,
    height: minTouch,
    borderRadius: minTouch / 2,
    borderWidth: 1,
    borderColor: colors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepText: { fontFamily: fonts.sansSemiBold, fontSize: 22, lineHeight: 26, color: colors.accent },
  track: { flex: 1, height: 6, borderRadius: 3, backgroundColor: colors.rule, overflow: 'hidden' },
  fill: { height: 6, backgroundColor: colors.accent },
  ageValue: { ...type.body, marginTop: 8 },
  ageLevel: { color: colors.muted },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 12 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 16,
    backgroundColor: colors.noteFill,
    paddingLeft: 12,
  },
  chipText: { fontFamily: fonts.sans, fontSize: 14, color: colors.ink },
  chipRemove: { minWidth: minTouch, minHeight: 36, alignItems: 'center', justifyContent: 'center' },
  chipRemoveText: { fontFamily: fonts.sansSemiBold, fontSize: 18, color: colors.muted },
  addRow: { flexDirection: 'row', alignItems: 'flex-end', gap: 10, marginTop: 14 },
  addButton: { marginBottom: 18 },
  radios: { gap: 8 },
  radio: { minHeight: minTouch, borderWidth: 1, borderColor: colors.rule, borderRadius: 4, paddingHorizontal: 14, paddingVertical: 8, justifyContent: 'center' },
  radioOn: { borderColor: colors.accent, backgroundColor: colors.noteFill },
  radioText: { fontFamily: fonts.sans, fontSize: 15, color: colors.ink },
  radioTextOn: { fontFamily: fonts.sansSemiBold, color: colors.accent },
  radioHint: { fontFamily: fonts.sans, fontSize: 13, color: colors.muted },
  save: { marginTop: 12, alignSelf: 'flex-start' },
});
