import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, type TextInputInstance, View } from 'react-native';

import { Markdown } from '@/components/Markdown';
import { Button, Field, Kicker, Note } from '@/components/ui';
import { ApiError, apiGet, apiPost } from '@/lib/api';
import { postSse } from '@/lib/sse';
import type { ChatTurn, HintView, PracticeState, TutorNote, Verdict } from '@/lib/types';
import { colors, fonts, minTouch, themed, type } from '@/theme';

export type LessonContext = { textbookId: string; lessonId: string; seq: number; token: string | null };

const VERDICT: Record<Verdict, { label: string; fg: string; bg: string }> = {
  MET: { label: 'Met', fg: '#2F6B3A', bg: '#E5F1E6' },
  PARTIAL: { label: 'Partly there', fg: '#7A5A12', bg: '#F6EBD3' },
  NOT_YET: { label: 'Not yet', fg: colors.accent, bg: '#F8E5E1' },
};

/**
 * Screen 6: practice and the tutor's note. The answer goes to the tutor, which grades it against a hidden rubric
 * and replies with what's covered, how many points are still to find (never what they are), and a coaching
 * message. Hints come one at a time. The answer is handed over only if the reader settings allow it: once every
 * hint is used and after that many tries ("Reveal answer after 3 tries").
 */
export function Practice({ blockId, prompt, initial, ctx }: { blockId: string; prompt: string; initial?: PracticeState; ctx: LessonContext }) {
  const queryClient = useQueryClient();
  const [answer, setAnswer] = useState(initial?.lastAnswer ?? '');
  const [note, setNote] = useState<TutorNote | null>(initial?.note ?? null);
  const [hints, setHints] = useState<string[]>(initial?.hintsShown ?? []);
  const [revealed, setRevealed] = useState<string | null>(initial?.answer ?? null);
  const [exhausted, setExhausted] = useState(false);
  const [busy, setBusy] = useState<'send' | 'hint' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<TextInputInstance>(null);
  const hintCount = initial?.hintCount ?? 3;
  const base = `/lessons/${ctx.lessonId}/blocks/${blockId}`;

  async function send() {
    setBusy('send');
    setError(null);
    try {
      const n = await apiPost<TutorNote>(`${base}/attempts`, { answer: answer.trim() }, ctx.token);
      setNote(n);
      if (n.hintNo) setHints((h) => (h.length < n.hintNo! ? [...h, n.message] : h));
      if (n.verdict === 'MET') void queryClient.invalidateQueries({ queryKey: ['contents', ctx.textbookId] });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Your tutor couldn’t be reached. Nothing was lost: send it again.');
    } finally {
      setBusy(null);
    }
  }

  async function hint() {
    setBusy('hint');
    setError(null);
    try {
      const h = await apiPost<HintView>(`${base}/hint`, undefined, ctx.token);
      if (h.text) setHints((list) => (list.length < h.hintNo ? [...list, h.text!] : list));
      if (h.answer) setRevealed(h.answer);
      if (h.exhausted && !h.text && !h.answer) setExhausted(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Couldn’t get a hint just now.');
    } finally {
      setBusy(null);
    }
  }

  const met = note?.verdict === 'MET';
  const hintsLeft = hints.length < hintCount;
  const attempts = note?.attemptNo ?? initial?.attempts ?? 0;
  const revealAfter = initial?.revealAfter ?? null;
  const outOfHints = !hintsLeft && !met && !revealed;
  return (
    <View style={styles.gap}>
      <Text style={styles.h2} accessibilityRole="header">
        Practice
      </Text>
      <Markdown md={prompt} />
      <View>
        <Text style={styles.label} nativeID={`answer-${blockId}`}>
          Your answer
        </Text>
        <TextInput
          ref={input}
          accessibilityLabel="Your answer"
          aria-labelledby={`answer-${blockId}`}
          value={answer}
          onChangeText={setAnswer}
          multiline
          maxLength={4000}
          placeholderTextColor={colors.muted}
          style={styles.input}
        />
      </View>
      <Button label={note ? 'Send your revised answer' : 'Send to your tutor'} onPress={send} busy={busy === 'send'}
        disabled={!answer.trim() || busy !== null} />
      {error ? <Note tone="error">{error}</Note> : null}

      {note ? (
        <View style={styles.noteBox} accessibilityLiveRegion="polite">
          <View style={styles.noteHead}>
            <Kicker>Tutor’s note</Kicker>
            <View style={[styles.chip, { backgroundColor: VERDICT[note.verdict].bg }]}>
              <Text style={[styles.chipText, { color: VERDICT[note.verdict].fg }]}>{VERDICT[note.verdict].label}</Text>
            </View>
          </View>
          {note.covered.map((point, i) => (
            <Text key={i} style={styles.point}>
              <Text style={{ color: VERDICT.MET.fg }}>✓  </Text>
              {point}
            </Text>
          ))}
          {note.missing > 0 ? (
            <Text style={styles.point}>
              <Text style={{ color: colors.muted }}>○  </Text>
              {note.missing === 1 ? 'One more point to find' : `${note.missing} more points to find`}
            </Text>
          ) : null}
          {note.hintNo ? <Kicker style={styles.hintLabel}>Hint {note.hintNo} of {hintCount}</Kicker> : null}
          <Text style={styles.coaching}>{note.message}</Text>
          {met && initial?.evidence ? (
            <Text style={styles.small}>Added to “What you’ve learned” at the back of the book.</Text>
          ) : met && note.evidencePending ? (
            <Text style={styles.small}>Your tutor is adding this to “What you’ve learned” at the back of the book.</Text>
          ) : null}
          {!met ? (
            <View style={styles.noteActions}>
              <Button label="Revise answer" variant="secondary" onPress={() => input.current?.focus()} />
              {hintsLeft ? (
                <Button label={`Hint ${hints.length + 1} of ${hintCount}`} variant="quiet" onPress={hint} busy={busy === 'hint'} disabled={busy !== null} />
              ) : null}
            </View>
          ) : null}
        </View>
      ) : null}

      {!note && hintsLeft && hints.length > 0 ? (
        <Button label={`Hint ${hints.length + 1} of ${hintCount}`} variant="quiet" onPress={hint} busy={busy === 'hint'} style={styles.left} />
      ) : null}
      {hints.length > 0 && !(note?.hintNo && hints.length === 1 && note.message === hints[0]) ? (
        <View style={styles.hints}>
          {hints.map((h, i) => (
            <Text key={i} style={styles.small}>
              <Text style={styles.hintNo}>Hint {i + 1}  </Text>
              {h}
            </Text>
          ))}
        </View>
      ) : null}
      {outOfHints && revealAfter != null && attempts >= revealAfter ? (
        <Button label="Show a strong answer" variant="secondary" onPress={hint} busy={busy === 'hint'} disabled={busy !== null}
          style={styles.left} />
      ) : outOfHints && revealAfter != null ? (
        <Text style={styles.small}>
          That’s every hint. After {revealAfter} tries you can see a strong answer; you’ve had {attempts}. Have another go, or ask
          your tutor below.
        </Text>
      ) : outOfHints && (exhausted || hints.length >= hintCount) ? (
        <Text style={styles.small}>That’s every hint. Have another go, or ask your tutor below.</Text>
      ) : null}
      {revealed ? (
        <View style={styles.revealed}>
          <Kicker style={styles.hintLabel}>A strong answer</Kicker>
          <Text style={styles.small}>{revealed}</Text>
        </View>
      ) : null}
      <Text style={styles.footnote}>Your tutor won’t hand you the answer, but it will help you find it.</Text>
    </View>
  );
}

/** "Stuck or curious?": tutor chat inside the lesson, streamed. Explains freely; coaches practice, never solves it. */
export function TutorChat({ examples, ctx }: { examples: string[]; ctx: LessonContext }) {
  const queryClient = useQueryClient();
  const key = ['tutor', ctx.textbookId, ctx.seq];
  const [open, setOpen] = useState(false);
  // Loaded with the lesson (not on the first question), so an earlier conversation shows and a slow load can't
  // land after a new reply.
  const thread = useQuery({
    queryKey: key,
    queryFn: () => apiGet<ChatTurn[]>(`/textbooks/${ctx.textbookId}/tutor/messages?lessonSeq=${ctx.seq}`, ctx.token),
    enabled: !!ctx.token,
    refetchOnWindowFocus: false,
  });
  const showThread = open || (thread.data?.length ?? 0) > 0;
  const [question, setQuestion] = useState('');
  const [pending, setPending] = useState<string | null>(null);
  const [streaming, setStreaming] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function ask(text: string) {
    const q = text.trim();
    if (!q || !ctx.token || streaming !== null) return;
    setOpen(true);
    setError(null);
    setPending(q);
    setStreaming('');
    let reply = '';
    let saved = false;
    try {
      await postSse(`/textbooks/${ctx.textbookId}/tutor/messages`, {
        token: ctx.token,
        body: { content: q, lessonSeq: ctx.seq },
        onEvent: ({ event, data }) => {
          const d = data as Record<string, unknown>;
          if (event === 'token') {
            reply += String(d.text ?? '');
            setStreaming(reply);
          } else if (event === 'done') {
            saved = true;
            const turn: ChatTurn[] = [
              { id: `q-${Date.now()}`, role: 'USER', content: q },
              { id: String(d.messageId), role: 'ASSISTANT', content: reply },
            ];
            // A history load still in flight was fetched before this turn was saved; drop it rather than let it
            // overwrite the reply.
            void queryClient.cancelQueries({ queryKey: key }).then(() =>
              queryClient.setQueryData<ChatTurn[]>(key, (old) => [...(old ?? []), ...turn]),
            );
            setQuestion('');
          } else if (event === 'error') {
            setError(String(d.message ?? 'Your tutor couldn’t reply.'));
          }
        },
      });
      if (!saved) setError((e) => e ?? 'The reply didn’t finish. Your question wasn’t lost: send it again.');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Your tutor couldn’t be reached.');
    } finally {
      setPending(null);
      setStreaming(null);
    }
  }

  return (
    <View style={styles.box}>
      <Kicker style={styles.boxLabel}>Stuck or curious?</Kicker>
      <Text style={styles.small}>Ask your tutor anything about this lesson. For example:</Text>
      {examples.map((e, i) => (
        <Pressable key={i} accessibilityRole="button" accessibilityLabel={`Ask: ${e}`} onPress={() => void ask(e)}
          disabled={streaming !== null} style={({ pressed }) => [styles.example, pressed && { opacity: 0.7 }]}>
          <Text style={styles.exampleText}>“{e}”</Text>
        </Pressable>
      ))}

      {showThread ? (
        <View style={styles.thread}>
          {(thread.data ?? []).map((m) => (
            <ChatLine key={m.id} role={m.role} text={m.content} />
          ))}
          {pending ? <ChatLine role="USER" text={pending} /> : null}
          {streaming !== null ? (
            <View accessibilityLiveRegion="polite">
              <ChatLine role="ASSISTANT" text={streaming || 'Your tutor is writing…'} />
            </View>
          ) : null}
        </View>
      ) : null}
      {error ? <Note tone="error">{error}</Note> : null}
      <Field label="Your question" value={question} onChangeText={setQuestion} multiline maxLength={2000} />
      <Button label="Ask your tutor" variant="secondary" onPress={() => void ask(question)} busy={streaming !== null}
        disabled={!question.trim()} style={styles.left} />
    </View>
  );
}

function ChatLine({ role, text }: { role: 'USER' | 'ASSISTANT'; text: string }) {
  const you = role === 'USER';
  return (
    <View style={styles.line}>
      <Text style={[styles.speaker, you && { color: colors.muted }]}>{you ? 'You' : 'Tutor'}</Text>
      {you ? <Text style={[styles.small, styles.italic]}>{text}</Text> : <Markdown md={text} size="small" />}
    </View>
  );
}

const styles = themed(() => StyleSheet.create({
  gap: { marginBottom: 8 },
  h2: { ...type.h2, marginTop: 20, marginBottom: 10 },
  label: { ...type.kicker, marginBottom: 8, marginTop: 4 },
  input: {
    minHeight: 110,
    borderWidth: 1,
    borderColor: colors.rule,
    borderRadius: 4,
    padding: 12,
    backgroundColor: colors.field,
    fontFamily: fonts.serif,
    fontSize: 17,
    color: colors.ink,
    textAlignVertical: 'top',
    marginBottom: 12,
  },
  noteBox: { backgroundColor: colors.noteFill, borderRadius: 4, padding: 16, marginTop: 16 },
  noteHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 },
  chip: { borderRadius: 12, paddingHorizontal: 10, paddingVertical: 4 },
  chipText: { fontFamily: fonts.sansSemiBold, fontSize: 12, letterSpacing: 0.5 },
  point: { ...type.body, fontSize: 15, lineHeight: 15 * 1.5 },
  hintLabel: { marginTop: 10, marginBottom: 2, color: colors.accent },
  coaching: { ...type.body, fontFamily: fonts.serifItalic, marginTop: 8 },
  noteActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 12, alignItems: 'center' },
  hints: { marginTop: 12, gap: 6 },
  hintNo: { fontFamily: fonts.sansSemiBold, fontSize: 13, color: colors.accent },
  revealed: { borderLeftWidth: 3, borderLeftColor: colors.ribbon, paddingLeft: 12, marginTop: 12 },
  small: { ...type.body, fontSize: 15, lineHeight: 15 * 1.5 },
  italic: { fontFamily: fonts.serifItalic },
  footnote: { fontFamily: fonts.sans, fontSize: 13, lineHeight: 19, color: colors.muted, marginTop: 12 },
  left: { alignSelf: 'flex-start' },
  box: { borderWidth: 1, borderColor: colors.rule, borderRadius: 4, padding: 16, marginVertical: 12, backgroundColor: colors.field },
  boxLabel: { marginBottom: 8 },
  example: { minHeight: minTouch, justifyContent: 'center' },
  exampleText: { ...type.body, fontSize: 15, fontFamily: fonts.serifItalic, color: colors.accent },
  thread: { marginTop: 12, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.rule, paddingTop: 12 },
  line: { marginBottom: 12 },
  speaker: { ...type.kicker, fontSize: 11, color: colors.accent, marginBottom: 2 },
}));
