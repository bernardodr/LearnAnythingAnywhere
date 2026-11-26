import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button, Divider, Field, Kicker, Ornament } from '@/components/ui';
import type { MissionDraft } from '@/lib/types';
import { colors, fonts, radius, themed, type } from '@/theme';

/**
 * The mission that ends the preface (BRIEF screen 3, `docs/DESIGN.md`): a quiet panel set like a book's dedication page:
 * "Your mission", the goal in a line, an ornament, the why, then what success looks like, the limits and what's left for
 * later. "Edit your mission" turns it into a form.
 *
 * The preface screen owns the mission being shown (`mission`) and whether it's being edited, because the confirming
 * action ("Write my textbook") is pinned to the screen's bottom, outside this card. The card owns only the form's raw
 * text while editing (lists as one item per line, split only on Done so a new empty line can be typed). A new draft
 * from the server remounts the card (the screen keys it by the draft), so the form starts again from it.
 *
 * @param mission what the card shows: the AI's draft, or the learner's edit of it
 * @param editing show the form instead of the mission
 * @param onEdit the learner tapped "Edit your mission"
 * @param onCancel the learner left the form without keeping changes
 * @param onDone the learner kept their changes: the edited mission (the AI's motif rides along unchanged)
 * @param busy the mission is being saved, so editing waits
 */
export function MissionCard({
  mission,
  editing,
  onEdit,
  onCancel,
  onDone,
  busy,
}: {
  mission: MissionDraft;
  editing: boolean;
  onEdit: () => void;
  onCancel: () => void;
  onDone: (mission: MissionDraft) => void;
  busy: boolean;
}) {
  const [form, setForm] = useState<Form>(() => toForm(mission));
  // The motif isn't on the form: it rides along as the AI picked it (the server keeps its own copy anyway).
  const edited = { ...fromForm(form), motif: mission.motif };
  const valid = edited.headline !== '' && edited.why !== '' && edited.successLooksLike.length > 0;
  const set = (key: keyof Form) => (text: string) => setForm({ ...form, [key]: text });

  if (editing) {
    return (
      <View style={styles.card}>
        <Kicker style={styles.kicker}>Edit your mission</Kicker>
        <Field label="Your goal in a line" value={form.headline} maxLength={90} onChangeText={set('headline')} />
        <Field label="Why" value={form.why} multiline maxLength={600} onChangeText={set('why')} />
        <Field label="Success looks like" hint="One per line" value={form.success} multiline onChangeText={set('success')} />
        <Field label="Limits" hint="Time, budget, equipment… one per line" value={form.limits} multiline
          onChangeText={set('limits')} />
        <Field label="Not now" hint="Things to leave out for now, one per line" value={form.notNow} multiline
          onChangeText={set('notNow')} />
        {!valid ? <Text style={styles.invalid}>A goal, a why and at least one success line are needed.</Text> : null}
        <View style={styles.actions}>
          <Button label="Cancel" variant="quiet" onPress={onCancel} />
          <Button label="Done" variant="secondary" disabled={!valid} onPress={() => onDone(edited)} />
        </View>
      </View>
    );
  }

  return (
    <View style={styles.card} accessibilityLabel="Your mission">
      <Kicker style={styles.kicker}>Your mission</Kicker>
      <Text style={styles.headline} accessibilityRole="header">
        {mission.headline}
      </Text>
      <Ornament />
      <Text style={styles.why}>{mission.why}</Text>
      <Section label="Success looks like" items={mission.successLooksLike} />
      <Section label="Limits" items={mission.constraints} />
      <Section label="Not now" items={mission.outOfScope} />
      <Button
        label="Edit your mission"
        variant="quiet"
        disabled={busy}
        style={styles.edit}
        onPress={() => {
          setForm(toForm(mission));
          onEdit();
        }}
      />
    </View>
  );
}

/** One part of the mission: a fine rule, its kicker, then a line per item with a green bullet (or "None stated yet"). */
function Section({ label, items }: { label: string; items: string[] }) {
  return (
    <View style={styles.section}>
      <Divider style={styles.sectionRule} />
      <Kicker style={styles.sectionLabel}>{label}</Kicker>
      {items.length === 0 ? (
        <Text style={styles.none}>None stated yet</Text>
      ) : (
        items.map((item, i) => (
          <Text key={i} style={styles.item}>
            <Text style={styles.bullet}>•  </Text>
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

const styles = themed(() => StyleSheet.create({
  // A quiet panel (DESIGN.md: noteFill is for notes and the mission), even and flat: texture belongs to books.
  card: { backgroundColor: colors.noteFill, borderRadius: radius, paddingHorizontal: 24, paddingVertical: 28, marginTop: 16 },
  kicker: { textAlign: 'center', color: colors.accent },
  headline: { ...type.h2, textAlign: 'center', marginTop: 12 },
  why: { ...type.small, fontFamily: fonts.serifItalic, fontSize: 17, lineHeight: 27, textAlign: 'center' },
  section: { marginTop: 20 },
  sectionRule: { marginBottom: 16 },
  sectionLabel: { marginBottom: 8 },
  item: { ...type.small, marginBottom: 4 },
  bullet: { color: colors.accent },
  none: { ...type.small, color: colors.muted, fontFamily: fonts.serifItalic },
  invalid: { ...type.meta, color: colors.danger, marginBottom: 4 },
  actions: { flexDirection: 'row', justifyContent: 'flex-end', flexWrap: 'wrap', gap: 12, marginTop: 8 },
  edit: { alignSelf: 'center', marginTop: 20 },
}));
