import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect, router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';

import { BookFace, volumeNumeral } from '@/components/Cover';
import { SourceTitle } from '@/components/LessonBlocks';
import { Markdown } from '@/components/Markdown';
import { Button, Divider, Field, Icon, IconButton, Kicker, MarginNote, Note, Page, TopBar } from '@/components/ui';
import { ApiError, apiGet, apiPost } from '@/lib/api';
import { useArrange } from '@/lib/arrange';
import { useSession } from '@/lib/session';
import { getSse } from '@/lib/sse';
import type { Contents, Textbook } from '@/lib/types';
import { colors, fonts, minTouch, radius, type } from '@/theme';

/** "01" for lesson 1: the contents page's numbering. */
const two = (n: number) => String(n).padStart(2, '0');

/**
 * Screen 4: a book's page (`docs/DESIGN.md`, the book-page mockup), read like the front of a printed book. At the top,
 * the drawn cover, the title, the mission's one-line goal and a line of facts (volume, lessons, how many read). Then
 * "Inside this book": the lessons as a contents page (numbered entries, fine dividers, the one up next marked quietly),
 * ending with the next lesson ("Written after lesson n", or being written now, with live progress). Then the mission's
 * why as a note in the margin, and the back of the book (A reference sheets, B glossary, C what you've learned,
 * D sources), each folded until opened. The one dominant action is pinned at the bottom: Start reading, Continue
 * reading, or Write lesson n once every lesson is read.
 *
 * The ••• at the top right opens the book's menu: "Move earlier / later in your library", the way to arrange the
 * library without dragging (CLAUDE.md: anything draggable has a non-drag way too).
 *
 * Loads `/textbooks/{id}/contents` (refreshed whenever the screen comes back into view) and `/textbooks` (the library's
 * order, for the menu), follows a lesson being written at `/jobs/{jobId}/events`, and starts lesson 1 itself straight
 * after the preface. Leads to a lesson, and back to the library. A book still in its preface goes to the preface
 * instead.
 */
export default function BookPage() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { ready, user, token } = useSession();
  const { width } = useWindowDimensions();
  const queryClient = useQueryClient();
  const contents = useQuery({
    queryKey: ['contents', id],
    queryFn: () => apiGet<Contents>(`/textbooks/${id}/contents`, token),
    enabled: !!token && !!id,
  });
  // Coming back from a lesson reuses this screen, so refresh on focus: learning records and glossary terms are
  // written in the background after a practice answer is met, and a finished lesson changes what's up next.
  const { refetch } = contents;
  useFocusEffect(
    useCallback(() => {
      if (token && id) void refetch();
    }, [token, id, refetch]),
  );
  const library = useQuery({
    queryKey: ['textbooks'],
    queryFn: () => apiGet<Textbook[]>('/textbooks', token),
    enabled: !!token,
  });
  const { move, failed: moveFailed } = useArrange();
  const [menuOpen, setMenuOpen] = useState(false);
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
    setWriteError(null);
    apiPost<{ jobId: string }>(`/textbooks/${id}/lessons/next`, undefined, token)
      .then((res) => setStartedJob(res.jobId))
      .catch((e: unknown) => setWriteError(e instanceof ApiError ? e.message : 'The lesson couldn’t be started.'));
  }, [id, token]);

  // "Write my textbook": lesson 1 is written straight after the mission is confirmed, without another tap.
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
  const readCount = data?.lessons.filter((l) => l.read).length ?? 0;
  const writing = jobId !== null;
  // About 45% of the reading column, as in the mockup, and never so big it pushes the contents off a phone's screen.
  const coverWidth = Math.min(168, Math.round(Math.min(560, width - 48) * 0.45));

  // The one dominant action (DESIGN.md): read on if there's something to read, else write the next lesson.
  const footer = !data ? undefined : nextToRead ? (
    <Button label={readCount > 0 ? 'Continue reading' : 'Start reading'} onPress={() => openLesson(nextToRead.seq)} />
  ) : !writing && data.next.canWrite ? (
    <Button label={`Write lesson ${data.next.seq}`} onPress={write} />
  ) : undefined;

  return (
    <Page footer={footer}>
      <View style={styles.top}>
        <TopBar backLabel="Back to your library" kicker="Your library"
          right={<IconButton icon="more" label={menuOpen ? 'Close this book’s menu' : 'This book’s menu'} onPress={() => setMenuOpen(!menuOpen)} />} />
        {menuOpen ? <BookMenu id={id} books={library.data ?? []} onMove={(to) => move(id, to)} /> : null}
      </View>
      {moveFailed ? <Note tone="error">Your library’s new order couldn’t be saved, so it’s back as it was. Please try again.</Note> : null}
      {contents.isError ? <Note tone="error">{contents.error.message}</Note> : null}
      {data ? (
        <View>
          <View style={styles.front}>
            <View style={styles.cover}>
              <BookFace book={data} width={coverWidth} />
            </View>
            <Text style={styles.title} accessibilityRole="header">
              {data.title}
            </Text>
            {data.missionHeadline ? <Text style={styles.headline}>{data.missionHeadline}</Text> : null}
            <Text style={styles.facts}>{facts(data.volume, data.lessons.length, readCount)}</Text>
          </View>

          <Divider style={styles.rule} />
          <Kicker style={styles.sectionKicker}>Inside this book</Kicker>
          {data.lessons.map((l) => {
            const upNext = l.id === nextToRead?.id;
            return (
              <Pressable
                key={l.id}
                accessibilityRole="button"
                accessibilityLabel={`Lesson ${l.seq}: ${l.title}. ${l.read ? 'Read' : upNext ? 'Up next' : ''}`}
                onPress={() => openLesson(l.seq)}
                style={({ pressed }) => [styles.entry, pressed && styles.pressed]}>
                <Text style={[styles.entryNo, upNext && styles.entryNoNext]}>{two(l.seq)}</Text>
                <Text style={styles.entryTitle} numberOfLines={2}>
                  {l.title}
                </Text>
                {l.read ? <Icon name="check" size={18} color={colors.muted} /> : upNext ? <Text style={styles.upNext}>Up next</Text> : null}
              </Pressable>
            );
          })}
          {/* The next lesson, not written yet: LAA writes each one after the last is finished. */}
          <View style={styles.entry}>
            <Text style={styles.entryNo}>{two(data.next.seq)}</Text>
            <Text style={styles.entryPending}>
              {writing ? 'Being written now' : data.next.writtenAfter === 0 ? 'Your first lesson' : `Written after lesson ${data.next.writtenAfter}`}
            </Text>
            {writing ? <ActivityIndicator color={colors.accent} /> : null}
          </View>
          {writing ? (
            <Text style={styles.progress} accessibilityLiveRegion="polite">
              {progress ?? 'Writing…'}
            </Text>
          ) : null}
          {writeError ? <Note tone="error">{writeError}</Note> : null}

          {data.missionWhy ? (
            <MarginNote kicker="Why you’re learning this" style={styles.why}>
              {data.missionWhy}
            </MarginNote>
          ) : null}

          <Kicker style={styles.sectionKicker}>At the back of the book</Kicker>
          <BackEntry letter="A" title="Reference sheets" count={data.back.referenceSheets.length} empty="Each lesson adds its cheat sheet here.">
            {data.back.referenceSheets.map((s) => <Sheet key={s.seq} seq={s.seq} title={s.title} md={s.md} />)}
          </BackEntry>
          <BackEntry letter="B" title="Glossary" count={termCount(data.back.glossary)} empty="Terms appear here once you’ve shown you understand them.">
            {data.back.glossary ? <Markdown md={data.back.glossary} size="small" /> : null}
          </BackEntry>
          <BackEntry letter="C" title="What you’ve learned" count={data.back.learned.length}
            empty="Your tutor records what you’ve shown you can do, as you practise.">
            {data.back.learned.map((r) => (
              <View key={r.path} style={styles.record}>
                <Text style={styles.recordTitle}>{r.title}</Text>
                <Text style={styles.small}>{r.body}</Text>
              </View>
            ))}
          </BackEntry>
          <BackEntry letter="D" title="Sources" count={data.back.sources.length} alwaysOpenable
            empty="No sources yet. LAA never makes them up: add ones you trust, and lessons can cite them.">
            {data.back.sources.map((s) => (
              <View key={s.id} style={styles.record}>
                <SourceTitle source={s} />
                {s.note ? <Text style={styles.small}>{s.note}</Text> : null}
              </View>
            ))}
            {data.back.linksOff ? (
              <Text style={styles.note}>Links are turned off in your reader settings, so sources show without their web addresses.</Text>
            ) : (
              <AddSource textbookId={id} token={token} onAdded={() => queryClient.invalidateQueries({ queryKey: ['contents', id] })} />
            )}
          </BackEntry>
        </View>
      ) : null}
    </Page>
  );
}

/**
 * The book's menu, opened by the ••• at the top right: moving the book a place earlier or later in the library (the
 * library's order, as dragging would change it), and where it stands now. It stays open, so a book can be moved
 * several places in a row.
 *
 * @param id this book's id; @param books the library in its current order
 * @param onMove moves this book one place (saved straight away, see `useArrange`)
 */
function BookMenu({ id, books, onMove }: { id: string; books: Textbook[]; onMove: (to: 'earlier' | 'later') => void }) {
  const at = books.findIndex((b) => b.id === id);
  const item = (label: string, to: 'earlier' | 'later', disabled: boolean) => (
    <Pressable accessibilityRole="menuitem" accessibilityLabel={label} accessibilityState={{ disabled }} disabled={disabled}
      onPress={() => onMove(to)} style={({ pressed }) => [styles.menuItem, disabled && styles.menuItemOff, pressed && styles.pressed]}>
      <Text style={styles.menuText}>{label}</Text>
    </Pressable>
  );
  return (
    <View style={styles.menu} accessibilityRole="menu">
      {item('Move earlier in your library', 'earlier', at <= 0)}
      {item('Move later in your library', 'later', at < 0 || at >= books.length - 1)}
      <Text style={styles.menuNote} accessibilityLiveRegion="polite">
        {at >= 0 ? `Book ${at + 1} of ${books.length} in your library` : ' '}
      </Text>
    </View>
  );
}

/** "Volume III · 2 lessons · 1 read": the line of facts under the title. */
function facts(volume: number, lessons: number, read: number) {
  const parts = [`Volume ${volumeNumeral(volume)}`, lessons === 1 ? '1 lesson' : `${lessons} lessons`];
  if (lessons > 0) parts.push(read === lessons ? 'all read' : `${read} read`);
  return parts.join(' · ');
}

/** How many terms the book's glossary holds: one `**Term**:` line each (the /teach glossary format). */
function termCount(glossary: string | null) {
  return glossary ? (glossary.match(/^\*\*.+?\*\*:/gm) ?? []).length : 0;
}

/**
 * One section at the back of the book, as a contents entry that folds open: its letter, its title, and on the right how
 * many things it holds (like a page number), or "None yet". Opening an empty one says what will fill it.
 *
 * @param count how many items the section holds; 0 shows `empty` when opened
 * @param alwaysOpenable show `children` even when `count` is 0 (Sources: the form to add the first one lives inside)
 */
function BackEntry({ letter, title, count, empty, alwaysOpenable, children }: {
  letter: string;
  title: string;
  count: number;
  empty: string;
  alwaysOpenable?: boolean;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const summary = count === 0 ? 'None yet' : String(count);
  return (
    <View>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        accessibilityLabel={`${letter}, ${title}: ${count === 0 ? 'none yet' : count}`}
        onPress={() => setOpen(!open)}
        style={({ pressed }) => [styles.entry, pressed && styles.pressed]}>
        <Text style={[styles.entryNo, styles.letter]}>{letter}</Text>
        <Text style={styles.entryTitle}>{title}</Text>
        <Text style={styles.count}>{summary}</Text>
        <Icon name={open ? 'chevronUp' : 'chevronDown'} size={18} color={colors.muted} />
      </Pressable>
      {open ? (
        <View style={styles.backBody}>
          {count === 0 ? <Text style={styles.note}>{empty}</Text> : null}
          {count > 0 || alwaysOpenable ? children : null}
        </View>
      ) : null}
    </View>
  );
}

/** A lesson's cheat sheet at the back of the book: its title, folded until tapped. */
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
        <Text style={styles.sheetMeta}>Lesson {seq}</Text>
      </Pressable>
      {open ? <Markdown md={md} size="small" /> : null}
    </View>
  );
}

/**
 * "Add a source": a quiet button that opens a short form (title, web address, what it's good for). Adding one writes
 * it to the book's sources (`POST /textbooks/{id}/resources`), then `onAdded` refreshes the page.
 */
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
  // Above the page, so the open menu lies over the cover rather than pushing it down.
  top: { zIndex: 10 },
  menu: {
    position: 'absolute',
    top: 48,
    right: -4,
    width: 280,
    paddingVertical: 6,
    borderRadius: radius,
    borderWidth: 1,
    borderColor: colors.rule,
    backgroundColor: colors.field,
    boxShadow: '0px 10px 24px rgba(41, 40, 35, 0.16)',
  },
  menuItem: { minHeight: minTouch, justifyContent: 'center', paddingHorizontal: 16 },
  menuItemOff: { opacity: 0.4 },
  menuText: { ...type.ui, color: colors.accent, fontFamily: fonts.sansSemiBold },
  menuNote: { ...type.meta, paddingHorizontal: 16, paddingTop: 6, paddingBottom: 8, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.rule, marginTop: 4 },
  front: { alignItems: 'center' },
  cover: { marginTop: 4, marginBottom: 28 },
  title: { ...type.h1, fontSize: 34, lineHeight: 40, textAlign: 'center' },
  headline: { ...type.small, fontSize: 18, lineHeight: 26, textAlign: 'center', marginTop: 8, paddingHorizontal: 8 },
  facts: { ...type.meta, textAlign: 'center', marginTop: 10 },
  rule: { marginTop: 24 },
  sectionKicker: { color: colors.ink, marginTop: 24, marginBottom: 4 },
  // A contents entry: number, title, and a quiet mark on the right, over a fine divider.
  entry: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    minHeight: 54,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.rule,
  },
  pressed: { opacity: 0.7 },
  entryNo: { fontFamily: fonts.serif, fontSize: 16, color: colors.muted, width: 26, fontVariant: ['tabular-nums'] },
  entryNoNext: { color: colors.accent, fontFamily: fonts.serifSemiBold },
  letter: { fontFamily: fonts.serifSemiBold, color: colors.accent },
  entryTitle: { fontFamily: fonts.serif, fontSize: 18, lineHeight: 25, color: colors.ink, flex: 1 },
  entryPending: { fontFamily: fonts.serifItalic, fontSize: 18, lineHeight: 25, color: colors.muted, flex: 1 },
  upNext: { fontFamily: fonts.sansSemiBold, fontSize: 13, color: colors.accent },
  count: { fontFamily: fonts.sans, fontSize: 13, color: colors.muted },
  progress: { ...type.meta, marginLeft: 40, marginTop: 8 },
  why: { marginTop: 36 },
  backBody: { paddingLeft: 40, paddingTop: 12, paddingBottom: 8 },
  note: { ...type.meta, fontSize: 14, lineHeight: 20, marginBottom: 8 },
  record: { marginBottom: 12 },
  recordTitle: { ...type.small, fontFamily: fonts.serifSemiBold, flexShrink: 1 },
  small: { ...type.small, fontSize: 15, lineHeight: 22 },
  sheetRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12, minHeight: minTouch },
  sheetMeta: { ...type.meta },
  addToggle: { alignSelf: 'flex-start', marginLeft: -10 },
  addForm: { marginTop: 8 },
  addActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 12 },
});
