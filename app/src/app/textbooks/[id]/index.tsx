import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect, router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import { SourceTitle } from '@/components/LessonBlocks';
import { Markdown } from '@/components/Markdown';
import { Button, Field, goBack, Kicker, Note, Ornament, Page } from '@/components/ui';
import { ApiError, apiGet, apiPost } from '@/lib/api';
import { useSession } from '@/lib/session';
import { getSse } from '@/lib/sse';
import type { Contents } from '@/lib/types';
import { colors, fonts, minTouch, type } from '@/theme';

/**
 * Screen 4: the contents page. Title, the mission as an epigraph, the numbered lessons (read / read next), the
 * next lesson ("Written after lesson n", or being written now, with live progress), "Continue reading", and
 * the back of the book: A reference sheets, B glossary, C what you've learned, D sources.
 */
export default function ContentsPage() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { ready, user, token } = useSession();
  const queryClient = useQueryClient();
  const contents = useQuery({
    queryKey: ['contents', id],
    queryFn: () => apiGet<Contents>(`/textbooks/${id}/contents`, token),
    enabled: !!token && !!id,
  });
  // Coming back from a lesson reuses this screen, so refresh on focus: learning records and glossary terms are
  // written in the background after a practice answer is met, and a finished lesson changes "read next".
  const { refetch } = contents;
  useFocusEffect(
    useCallback(() => {
      if (token && id) void refetch();
    }, [token, id, refetch]),
  );
  const [startedJob, setStartedJob] = useState<string | null>(null);
  const [progress, setProgress] = useState<string | null>(null);
  const [writeError, setWriteError] = useState<string | null>(null);
  const autoStarted = useRef(false);
  const data = contents.data;
  // The job to follow: one this screen started, or one already running when the contents loaded (re-attach).
  const jobId = startedJob ?? data?.next.jobId ?? null;

  // Follow the writing job's events (it replays them from the start) until it's done, then refresh the contents.
  useEffect(() => {
    if (!jobId || !token) return;
    const controller = new AbortController();
    getSse(`/jobs/${jobId}/events`, {
      token,
      signal: controller.signal,
      onEvent: ({ event, data: payload }) => {
        const d = payload as Record<string, unknown>;
        if (event === 'progress') setProgress(String(d.message ?? 'Writing…'));
        else if (event === 'error') setWriteError(String(d.message ?? 'The lesson couldn’t be written.'));
      },
    })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setWriteError(e instanceof ApiError ? e.message : 'Lost track of the writing.');
      })
      .finally(() => {
        if (controller.signal.aborted) return;
        setStartedJob(null);
        setProgress(null);
        void queryClient.invalidateQueries({ queryKey: ['contents', id] });
      });
    return () => controller.abort();
  }, [jobId, token, id, queryClient]);

  /** Starts writing the next lesson; the job id arrives in a callback, which the follow effect picks up. */
  const write = useCallback(() => {
    apiPost<{ jobId: string }>(`/textbooks/${id}/lessons/next`, undefined, token)
      .then((res) => setStartedJob(res.jobId))
      .catch((e: unknown) => setWriteError(e instanceof ApiError ? e.message : 'The lesson couldn’t be started.'));
  }, [id, token]);

  // "Write my textbook": lesson 1 is written straight after the mission is confirmed.
  useEffect(() => {
    if (!data || data.status !== 'ACTIVE' || data.next.jobId || autoStarted.current) return;
    if (data.lessons.length === 0 && data.next.canWrite) {
      autoStarted.current = true;
      apiPost<{ jobId: string }>(`/textbooks/${id}/lessons/next`, undefined, token)
        .then((res) => setStartedJob(res.jobId))
        .catch((e: unknown) => setWriteError(e instanceof ApiError ? e.message : 'The lesson couldn’t be started.'));
    }
  }, [data, id, token]);

  if (!ready) return null;
  if (!user) return <Redirect href="/" />;
  if (data?.status === 'INTERVIEW') return <Redirect href={{ pathname: '/textbooks/[id]/preface', params: { id } }} />;

  const openLesson = (seq: number) =>
    router.push({ pathname: '/textbooks/[id]/lessons/[seq]', params: { id, seq: String(seq) } });
  const nextToRead = data?.lessons.find((l) => !l.read) ?? null;
  const writing = jobId !== null;

  return (
    <Page>
      <Button label="‹ Your shelf" variant="quiet" onPress={goBack} style={styles.back} accessibilityLabel="Back to your shelf" />
      {contents.isError ? <Note tone="error">{contents.error.message}</Note> : null}
      {data ? (
        <View>
          <Text style={styles.title} accessibilityRole="header">
            {data.title}
          </Text>
          {data.missionWhy ? (
            <View style={styles.epigraph}>
              <Text style={styles.epigraphText}>{data.missionWhy}</Text>
              {data.missionHeadline ? <Text style={styles.epigraphBy}>— your mission: {data.missionHeadline}</Text> : null}
            </View>
          ) : null}
          <Ornament />

          <Kicker>Contents</Kicker>
          {data.lessons.map((l) => (
            <Pressable
              key={l.id}
              accessibilityRole="button"
              accessibilityLabel={`Lesson ${l.seq}: ${l.title}. ${l.read ? 'Read' : l.id === nextToRead?.id ? 'Read next' : ''}`}
              onPress={() => openLesson(l.seq)}
              style={({ pressed }) => [styles.entry, pressed && { opacity: 0.7 }]}>
              <Text style={styles.entryNo}>{l.seq}</Text>
              <Text style={styles.entryTitle} numberOfLines={2}>
                {l.title}
              </Text>
              <View style={styles.leader} />
              <Text style={[styles.entryStatus, l.id === nextToRead?.id && styles.entryNext]}>
                {l.read ? 'Read' : l.id === nextToRead?.id ? 'Read next' : ''}
              </Text>
            </Pressable>
          ))}

          <View style={[styles.entry, styles.entryPending]}>
            <Text style={[styles.entryNo, styles.muted]}>{data.next.seq}</Text>
            <Text style={[styles.entryTitle, styles.muted]}>
              {writing ? 'Being written now' : data.next.writtenAfter === 0 ? 'Your first lesson' : `Written after lesson ${data.next.writtenAfter}`}
            </Text>
            <View style={styles.leader} />
            {writing ? <ActivityIndicator color={colors.accent} /> : null}
          </View>
          {writing ? (
            <Text style={styles.progress} accessibilityLiveRegion="polite">
              {progress ?? 'Writing…'}
            </Text>
          ) : null}
          {writeError ? <Note tone="error">{writeError}</Note> : null}
          {!writing && data.next.canWrite ? (
            <Button
              label={`Write lesson ${data.next.seq}`}
              variant="secondary"
              onPress={() => {
                setWriteError(null);
                write();
              }}
              style={styles.writeButton}
            />
          ) : null}

          {nextToRead ? (
            <Button label="Continue reading" onPress={() => openLesson(nextToRead.seq)} style={styles.continue} />
          ) : null}

          <Ornament />
          <Kicker>At the back of the book</Kicker>
          <BackSection letter="A" title="Reference sheets" empty="Each lesson adds a cheat sheet here.">
            {data.back.referenceSheets.length > 0
              ? data.back.referenceSheets.map((s) => <Sheet key={s.seq} seq={s.seq} title={s.title} md={s.md} />)
              : null}
          </BackSection>
          <BackSection letter="B" title="Glossary" empty="Terms appear here once you’ve shown you understand them.">
            {data.back.glossary ? <Markdown md={data.back.glossary} size="small" /> : null}
          </BackSection>
          <BackSection letter="C" title="What you’ve learned" empty="Your tutor records what you’ve shown you can do, as you practise.">
            {data.back.learned.length > 0
              ? data.back.learned.map((r) => (
                  <View key={r.path} style={styles.record}>
                    <Text style={styles.recordTitle}>{r.title}</Text>
                    <Text style={styles.small}>{r.body}</Text>
                  </View>
                ))
              : null}
          </BackSection>
          <BackSection letter="D" title="Sources" empty="No sources yet. LAA never makes them up: add ones you trust, and lessons can cite them.">
            {data.back.sources.length > 0
              ? data.back.sources.map((s) => (
                  <View key={s.id} style={styles.record}>
                    <SourceTitle source={s} />
                    {s.note ? <Text style={styles.small}>{s.note}</Text> : null}
                  </View>
                ))
              : null}
          </BackSection>
          <AddSource textbookId={id} token={token} onAdded={() => queryClient.invalidateQueries({ queryKey: ['contents', id] })} />
        </View>
      ) : null}
    </Page>
  );
}

function BackSection({ letter, title, empty, children }: { letter: string; title: string; empty: string; children: ReactNode }) {
  const hasContent = Array.isArray(children) ? children.length > 0 : !!children;
  return (
    <View style={styles.backSection}>
      <Text style={styles.backTitle} accessibilityRole="header">
        <Text style={styles.backLetter}>{letter}  </Text>
        {title}
      </Text>
      {hasContent ? children : <Text style={styles.empty}>{empty}</Text>}
    </View>
  );
}

function Sheet({ seq, title, md }: { seq: number; title: string; md: string }) {
  const [open, setOpen] = useState(false);
  return (
    <View style={styles.record}>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        accessibilityLabel={`Reference sheet from lesson ${seq}: ${title}`}
        onPress={() => setOpen(!open)}
        style={styles.sheetRow}>
        <Text style={styles.recordTitle}>{title}</Text>
        <Text style={styles.sheetMeta}>Lesson {seq} {open ? '▴' : '▾'}</Text>
      </Pressable>
      {open ? <Markdown md={md} size="small" /> : null}
    </View>
  );
}

function AddSource({ textbookId, token, onAdded }: { textbookId: string; token: string | null; onAdded: () => void }) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [url, setUrl] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!open) return <Button label="Add a source" variant="quiet" onPress={() => setOpen(true)} style={styles.addToggle} />;

  async function add() {
    setBusy(true);
    setError(null);
    try {
      await apiPost(`/textbooks/${textbookId}/resources`, { title: title.trim(), url: url.trim(), note: note.trim() }, token);
      setTitle('');
      setUrl('');
      setNote('');
      setOpen(false);
      onAdded();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Couldn’t add that source.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.addForm}>
      <Field label="Title" value={title} onChangeText={setTitle} maxLength={160} hint="e.g. Book: The Bouldering Bible, by …" />
      <Field label="Web address" value={url} onChangeText={setUrl} maxLength={500} autoCapitalize="none" autoCorrect={false}
        inputMode="url" keyboardType="url" />
      <Field label="What it’s good for" value={note} onChangeText={setNote} maxLength={300} hint="Optional: one line, e.g. “Use for: footwork drills”." />
      {error ? <Note tone="error">{error}</Note> : null}
      <View style={styles.addActions}>
        <Button label="Cancel" variant="quiet" onPress={() => setOpen(false)} />
        <Button label="Add source" variant="secondary" onPress={add} busy={busy} disabled={!title.trim() || !url.trim()} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  back: { alignSelf: 'flex-start', marginLeft: -8, marginBottom: 12 },
  title: { ...type.h1, textAlign: 'center', marginTop: 8 },
  epigraph: { marginTop: 20, paddingHorizontal: 12 },
  epigraphText: { ...type.body, fontFamily: fonts.serifItalic, textAlign: 'center' },
  epigraphBy: { fontFamily: fonts.sans, fontSize: 13, color: colors.muted, textAlign: 'center', marginTop: 8 },
  entry: { flexDirection: 'row', alignItems: 'flex-end', minHeight: minTouch, paddingVertical: 6 },
  entryPending: { opacity: 1 },
  entryNo: { ...type.body, width: 28, color: colors.accent },
  entryTitle: { ...type.body, flexShrink: 1 },
  leader: { flex: 1, minWidth: 16, borderBottomWidth: 1, borderStyle: 'dotted', borderColor: colors.muted, marginHorizontal: 8, marginBottom: 7 },
  entryStatus: { ...type.kicker, fontSize: 11, marginBottom: 4 },
  entryNext: { color: colors.accent },
  muted: { color: colors.muted },
  progress: { fontFamily: fonts.sans, fontSize: 14, color: colors.muted, marginLeft: 28, marginTop: 2 },
  writeButton: { alignSelf: 'flex-start', marginTop: 12 },
  continue: { marginTop: 24 },
  backSection: { marginTop: 18 },
  backTitle: { ...type.h2, fontSize: 20, marginBottom: 8 },
  backLetter: { color: colors.accent },
  empty: { fontFamily: fonts.sans, fontSize: 14, lineHeight: 20, color: colors.muted },
  record: { marginBottom: 12 },
  recordTitle: { ...type.body, fontFamily: fonts.serifSemiBold },
  small: { ...type.body, fontSize: 15, lineHeight: 15 * 1.5 },
  sheetRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', minHeight: minTouch },
  sheetMeta: { fontFamily: fonts.sans, fontSize: 13, color: colors.muted },
  addToggle: { alignSelf: 'flex-start', marginLeft: -8, marginTop: 4 },
  addForm: { marginTop: 12, borderTopWidth: 1, borderTopColor: colors.rule, paddingTop: 16 },
  addActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 12 },
});
