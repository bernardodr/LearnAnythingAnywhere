import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button, Field, Kicker, Ornament } from '@/components/ui';
import type { MissionDraft } from '@/lib/types';
import { colors, fonts, type } from '@/theme';

/**
 * The mission card that ends the preface (BRIEF screen 3): the why, then success, limits and not-now lines.
 * "Edit" turns it into a form; "Write my textbook" confirms whatever is shown.
 */
export function MissionCard({
  draft,
  busy,
  onConfirm,
}: {
  draft: MissionDraft;
  busy: boolean;
  onConfirm: (draft: MissionDraft) => void;
}) {
  // `current` is what the card shows and confirms; `form` is the raw text while the edit form is open
  // (lists as one item per line, split only on Done so a new empty line can be typed). A new draft from the
  // server remounts the card (the parent keys it by content), so state starts from it.
  const [current, setCurrent] = useState<MissionDraft>(draft);
  const [form, setForm] = useState<Form>(() => toForm(draft));
  const [editing, setEditing] = useState(false);
  // The motif isn't on the form: it rides along as the AI picked it (the server keeps its own copy anyway).
  const edited = { ...fromForm(form), motif: draft.motif };
  const valid = edited.headline !== '' && edited.why !== '' && edited.successLooksLike.length > 0;
  const set = (key: keyof Form) => (text: string) => setForm({ ...form, [key]: text });

  if (editing) {
    return (
      <View style={styles.card}>
        <Kicker style={styles.center}>Edit your mission</Kicker>
        <Field label="Your goal in a line" value={form.headline} maxLength={90} onChangeText={set('headline')} />
        <Field label="Why" value={form.why} multiline maxLength={600} onChangeText={set('why')} />
        <Field label="Success looks like" hint="One per line" value={form.success} multiline onChangeText={set('success')} />
        <Field label="Limits" hint="Time, budget, equipment… one per line" value={form.limits} multiline
          onChangeText={set('limits')} />
        <Field label="Not now" hint="Things to leave out for now, one per line" value={form.notNow} multiline
          onChangeText={set('notNow')} />
        {!valid ? <Text style={styles.invalid}>A goal, a why and at least one success line are needed.</Text> : null}
        <View style={styles.actions}>
          <Button label="Cancel" variant="quiet" onPress={() => setEditing(false)} />
          <Button
            label="Done"
            variant="secondary"
            disabled={!valid}
            onPress={() => {
              setCurrent(edited);
              setEditing(false);
            }}
          />
        </View>
      </View>
    );
  }

  return (
    <View style={styles.card} accessibilityLabel="Your mission">
      <Kicker style={styles.center}>Your mission</Kicker>
      <Text style={styles.headline} accessibilityRole="header">
        {current.headline}
      </Text>
      <Ornament />
      <Text style={styles.why}>{current.why}</Text>
      <Section label="Success looks like" items={current.successLooksLike} />
      <Section label="Limits" items={current.constraints} />
      <Section label="Not now" items={current.outOfScope} />
      <View style={styles.actions}>
        <Button
          label="Edit"
          variant="secondary"
          disabled={busy}
          onPress={() => {
            setForm(toForm(current));
            setEditing(true);
          }}
        />
        <Button label="Write my textbook" busy={busy} onPress={() => onConfirm(current)} />
      </View>
    </View>
  );
}

function Section({ label, items }: { label: string; items: string[] }) {
  return (
    <View style={styles.section}>
      <Kicker style={styles.sectionLabel}>{label}</Kicker>
      {items.length === 0 ? (
        <Text style={styles.none}>None stated yet</Text>
      ) : (
        items.map((item, i) => (
          <Text key={i} style={styles.item}>
            <Text style={styles.bullet}>—  </Text>
            {item}
          </Text>
        ))
      )}
    </View>
  );
}

type Form = { headline: string; why: string; success: string; limits: string; notNow: string };

function toForm(d: MissionDraft): Form {
  return {
    headline: d.headline,
    why: d.why,
    success: d.successLooksLike.join('\n'),
    limits: d.constraints.join('\n'),
    notNow: d.outOfScope.join('\n'),
  };
}

function fromForm(f: Form): MissionDraft {
  return {
    headline: f.headline.trim(),
    why: f.why.trim(),
    successLooksLike: lines(f.success),
    constraints: lines(f.limits),
    outOfScope: lines(f.notNow),
  };
}

function lines(text: string): string[] {
  return text
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l !== '');
}

const styles = StyleSheet.create({
  card: {
    borderWidth: 1,
    borderColor: colors.rule,
    borderRadius: 4,
    backgroundColor: colors.field,
    padding: 24,
    marginTop: 28,
  },
  center: { textAlign: 'center' },
  headline: { ...type.h2, textAlign: 'center', marginTop: 12 },
  why: { ...type.body, fontFamily: fonts.serifItalic, textAlign: 'center', marginBottom: 8 },
  section: { marginTop: 18 },
  sectionLabel: { marginBottom: 6 },
  item: { ...type.body, fontSize: 16, lineHeight: 16 * 1.5 },
  bullet: { color: colors.accent },
  none: { ...type.body, fontSize: 16, color: colors.muted, fontFamily: fonts.serifItalic },
  invalid: { fontFamily: fonts.sans, fontSize: 13, color: colors.accent, marginTop: 4 },
  actions: { flexDirection: 'row', justifyContent: 'flex-end', flexWrap: 'wrap', gap: 12, marginTop: 24 },
});
