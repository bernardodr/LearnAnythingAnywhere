import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { LessonBlock, LessonSources } from '@/components/LessonBlocks';
import { Markdown } from '@/components/Markdown';
import { Button, Kicker, Note, Ornament, Page } from '@/components/ui';
import { ApiError, apiGet, apiPost } from '@/lib/api';
import { useSession } from '@/lib/session';
import type { Block, Lesson, PracticeState } from '@/lib/types';
import { colors, fonts, type } from '@/theme';

/**
 * Screen 5: a lesson, as one long page. Kicker, title, subtitle and "Your win today", then the blocks in
 * order (warm-up, sections, check yourself, steps, practice, primary source, "stuck or curious?"), the cheat
 * sheet, and finishing the lesson, which unlocks the next one.
 */
export default function LessonPage() {
  const { id, seq } = useLocalSearchParams<{ id: string; seq: string }>();
  const { ready, user, token } = useSession();
  const queryClient = useQueryClient();
  const lesson = useQuery({
    queryKey: ['lesson', id, seq],
    queryFn: () => apiGet<Lesson>(`/textbooks/${id}/lessons/${seq}`, token),
    enabled: !!token && !!id && !!seq,
  });
  const [sheetOpen, setSheetOpen] = useState(false);
  const [finishing, setFinishing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!ready) return null;
  if (!user) return <Redirect href="/" />;
  const l = lesson.data;
  // Back to the contents page already in the stack (or open it, after a deep link), never a second copy on top.
  const toContents = () => router.dismissTo({ pathname: '/textbooks/[id]', params: { id } });

  async function finish() {
    if (!l) return;
    setFinishing(true);
    setError(null);
    try {
      await apiPost(`/lessons/${l.id}/read`, undefined, token);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['lesson', id, seq] }),
        queryClient.invalidateQueries({ queryKey: ['contents', id] }),
      ]);
      if (l.next) router.replace({ pathname: '/textbooks/[id]/lessons/[seq]', params: { id, seq: String(l.next) } });
      else toContents();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Couldn’t save your progress.');
    } finally {
      setFinishing(false);
    }
  }

  return (
    <Page>
      <Button label="‹ Contents" variant="quiet" onPress={toContents} style={styles.back} accessibilityLabel="Back to the contents" />
      {lesson.isError ? <Note tone="error">{lesson.error.message}</Note> : null}
      {l ? (
        <View>
          <Kicker>{l.kicker}</Kicker>
          <Text style={styles.title} accessibilityRole="header">
            {l.title}
          </Text>
          <Text style={styles.subtitle}>{l.subtitle}</Text>
          <View style={styles.win}>
            <Kicker style={styles.winLabel}>Your win today</Kicker>
            <Text style={styles.winText}>{l.win}</Text>
            <Text style={styles.mission}>For your mission: {l.missionLink}</Text>
          </View>

          {l.blocks.map((block, i) => (
            <LessonBlock key={blockKey(block.id, l.practice?.[block.id])} block={block} sources={l.sources}
              heading={quizHeading(l.blocks, i)} ctx={{ textbookId: id, lessonId: l.id, seq: l.seq, token }}
              practice={l.practice?.[block.id]} />
          ))}

          {l.referenceSheet ? (
            <View style={styles.sheet}>
              <Button
                label={sheetOpen ? 'Hide the cheat sheet' : `Cheat sheet: ${l.referenceSheet.title}`}
                variant="quiet"
                onPress={() => setSheetOpen(!sheetOpen)}
                style={styles.sheetToggle}
              />
              {sheetOpen ? (
                <View style={styles.sheetBody}>
                  <Kicker style={styles.sheetLabel}>{l.referenceSheet.title}</Kicker>
                  <Markdown md={l.referenceSheet.md} size="small" />
                </View>
              ) : null}

          <LessonSources sources={l.sources} />
            </View>
          ) : null}

          <Ornament />
          {error ? <Note tone="error">{error}</Note> : null}
          {l.readAt ? (
            <Text style={styles.done}>You’ve finished this lesson.</Text>
          ) : (
            <Button label="I’ve finished this lesson" onPress={finish} busy={finishing} />
          )}
          <View style={styles.nav}>
            {l.prev ? (
              <Button label={`‹ Lesson ${l.prev}`} variant="quiet"
                onPress={() => router.replace({ pathname: '/textbooks/[id]/lessons/[seq]', params: { id, seq: String(l.prev) } })} />
            ) : (
              <View />
            )}
            {l.next ? (
              <Button label={`Lesson ${l.next} ›`} variant="quiet"
                onPress={() => router.replace({ pathname: '/textbooks/[id]/lessons/[seq]', params: { id, seq: String(l.next) } })} />
            ) : (
              <Button label={l.readAt ? `Write lesson ${l.seq + 1} ›` : `Lesson ${l.seq + 1} is written after this one`}
                variant="quiet" disabled={!l.readAt} onPress={toContents} />
            )}
          </View>
        </View>
      ) : null}
    </Page>
  );
}

/**
 * A practice block starts from the server's record of it. Revisiting a lesson first shows the cached copy, then
 * the fresh one; keying by the saved progress remounts the block when that progress changed, so it's restored.
 */
function blockKey(id: string, p?: PracticeState): string {
  return p ? `${id}:${p.attempts}:${p.hintsShown.length}:${p.answer ? 1 : 0}:${p.evidence ?? ''}` : id;
}

/** "Warm-up" before the first of a run of warm-ups, "Check yourself" before the first of a run of checks. */
function quizHeading(blocks: Block[], i: number): string | undefined {
  const b = blocks[i];
  if (b.type !== 'quiz') return undefined;
  const prev = blocks[i - 1];
  if (prev && prev.type === 'quiz' && prev.variant === b.variant) return undefined;
  return b.variant === 'warmup' ? 'Warm-up' : 'Check yourself';
}

const styles = StyleSheet.create({
  back: { alignSelf: 'flex-start', marginLeft: -8, marginBottom: 12 },
  title: { ...type.h1, marginTop: 8 },
  subtitle: { ...type.body, fontFamily: fonts.serifItalic, color: colors.muted, marginTop: 6 },
  win: { backgroundColor: colors.noteFill, borderRadius: 4, padding: 16, marginVertical: 20 },
  winLabel: { marginBottom: 6, color: colors.accent },
  winText: { ...type.body },
  mission: { fontFamily: fonts.sans, fontSize: 13, color: colors.muted, marginTop: 8 },
  sheet: { marginTop: 16 },
  sheetToggle: { alignSelf: 'flex-start', marginLeft: -8 },
  sheetBody: { borderWidth: 1, borderColor: colors.rule, borderRadius: 4, padding: 16, backgroundColor: colors.field },
  sheetLabel: { marginBottom: 8 },
  done: { fontFamily: fonts.sans, fontSize: 14, color: colors.muted, textAlign: 'center' },
  nav: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 24 },
});
