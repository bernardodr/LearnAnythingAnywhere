import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { MissionCard } from '@/components/MissionCard';
import { Button, Field, Kicker, MarginNote, Note, Ornament, Page, TopBar } from '@/components/ui';
import { ApiError, apiGet, apiPost } from '@/lib/api';
import { useSession } from '@/lib/session';
import { postSse } from '@/lib/sse';
import type { ChatTurn, ConfirmResponse, MissionDraft, MissionState } from '@/lib/types';
import { colors, type } from '@/theme';

/**
 * Screen 3: the preface (`docs/DESIGN.md`), set like the opening of a book: the book's title in the bar, then
 * "Preface", "Before we begin", an ornament and a line on what happens here. Below it, a streamed interview about why
 * the learner wants this topic: the tutor's questions as reading text, the learner's answers as notes in the margin. It
 * ends in the mission card, which they can edit and confirm. The one dominant action is pinned at the bottom: Send
 * while they answer, then "Write my textbook" (which saves the mission as MISSION.md and leads to the book's page,
 * where lesson 1 is written).
 *
 * Loads `/textbooks/{id}/mission` (the learner may be coming back to an unfinished preface); streams each turn from
 * `POST /textbooks/{id}/mission/messages` (the first one with no answer, for the opening question); retries a failed
 * draft at `POST /mission/draft`; confirms at `POST /mission/confirm`. A book whose preface is done goes to its page.
 */
export default function Preface() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { ready, user, token } = useSession();
  const queryClient = useQueryClient();
  // The query cache holds the transcript and draft: loaded from the server (the learner may be coming back
  // to an unfinished preface), then updated in place as each turn is saved.
  const mission = useQuery({
    queryKey: ['mission', id],
    queryFn: () => apiGet<MissionState>(`/textbooks/${id}/mission`, token),
    enabled: !!token && !!id,
    refetchOnWindowFocus: false, // a refetch racing a streaming turn could drop the turn just saved
  });
  const update = useCallback(
    (change: (s: MissionState) => MissionState) =>
      queryClient.setQueryData<MissionState>(['mission', id], (s) => (s ? change(s) : s)),
    [queryClient, id],
  );
  const messages = mission.data?.messages ?? [];
  const draft = mission.data?.draft ?? null;
  const answers = mission.data?.answers ?? 0;

  const [streaming, setStreaming] = useState<string | null>(null); // the reply as it arrives
  const [pendingAnswer, setPendingAnswer] = useState<string | null>(null);
  const [answer, setAnswer] = useState('');
  const [draftError, setDraftError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  // The learner's edit of the draft, if they kept one, and whether the edit form is open. The card shows `edited` over
  // the draft, and "Write my textbook" confirms whichever is shown.
  const [edited, setEdited] = useState<MissionDraft | null>(null);
  const [editing, setEditing] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const started = useRef(false);
  const abort = useRef<AbortController | null>(null);

  // Leaving the screen stops the stream; the server then saves nothing for that turn. Resetting `started`
  // lets a remount (React's dev-mode double mount included) open the interview again.
  useEffect(
    () => () => {
      abort.current?.abort();
      started.current = false;
    },
    [],
  );

  const sendTurn = useCallback(
    async (content: string | null) => {
      if (!token) return;
      setError(null);
      setPendingAnswer(content);
      setStreaming('');
      let reply = '';
      let saved = false;
      const controller = new AbortController();
      abort.current = controller;
      try {
        await postSse(`/textbooks/${id}/mission/messages`, {
          token,
          body: content ? { content } : {},
          signal: controller.signal,
          onEvent: ({ event, data }) => {
            const d = data as Record<string, unknown>;
            if (event === 'token') {
              reply += String(d.text ?? '');
              setStreaming(reply);
            } else if (event === 'draft') {
              update((s) => ({ ...s, draft: data as MissionDraft }));
            } else if (event === 'draft_failed') {
              setDraftError(String(d.message ?? 'The mission draft couldn’t be written.'));
            } else if (event === 'done') {
              saved = true;
              const turn: ChatTurn[] = [];
              if (content) turn.push({ id: `you-${Date.now()}`, role: 'USER', content });
              turn.push({ id: String(d.messageId), role: 'ASSISTANT', content: reply });
              update((s) => ({ ...s, messages: [...s.messages, ...turn], answers: s.answers + (content ? 1 : 0) }));
              setAnswer('');
            } else if (event === 'error') {
              setError(String(d.message ?? 'Something went wrong.'));
            }
          },
        });
        if (controller.signal.aborted) return; // this screen (or this request) is gone; touch nothing
        if (!saved) setError((prev) => prev ?? 'The reply didn’t finish. Your answer wasn’t lost: send it again.');
      } catch (e) {
        if (controller.signal.aborted) return;
        setError(e instanceof ApiError ? e.message : 'Something went wrong. Please try again.');
      } finally {
        if (!controller.signal.aborted) {
          setStreaming(null);
          setPendingAnswer(null);
        }
      }
    },
    [id, token, update],
  );

  // A new preface opens with the interviewer's first question.
  useEffect(() => {
    if (mission.data && mission.data.status === 'INTERVIEW' && mission.data.messages.length === 0 && !started.current) {
      started.current = true;
      void sendTurn(null);
    }
  }, [mission.data, sendTurn]);

  if (!ready) return null;
  if (!user) return <Redirect href="/" />;
  if (mission.data?.status === 'ACTIVE') return <Redirect href={{ pathname: '/textbooks/[id]', params: { id } }} />;

  const busy = streaming !== null;
  const canAnswer = !draft && !busy && messages.length > 0 && answer.trim() !== '';

  async function retryDraft() {
    setRetrying(true);
    setDraftError(null);
    try {
      const fresh = await apiPost<MissionDraft>(`/textbooks/${id}/mission/draft`, undefined, token);
      setEdited(null);
      update((s) => ({ ...s, draft: fresh }));
    } catch (e) {
      setDraftError(e instanceof ApiError ? e.message : 'The mission draft couldn’t be written.');
    } finally {
      setRetrying(false);
    }
  }

  async function confirm(confirmed: MissionDraft) {
    setConfirming(true);
    setError(null);
    try {
      await apiPost<ConfirmResponse>(`/textbooks/${id}/mission/confirm`, { draft: confirmed }, token);
      await queryClient.invalidateQueries({ queryKey: ['textbooks'] });
      router.replace({ pathname: '/textbooks/[id]', params: { id } });
    } catch (e) {
      const detail = e instanceof ApiError && e.errors.length > 0 ? ` (${e.errors.join('; ')})` : '';
      setError((e instanceof ApiError ? e.message : 'Couldn’t save your mission.') + detail);
    } finally {
      setConfirming(false);
    }
  }

  const shown = edited ?? draft;
  // The one dominant action at the bottom: whatever moves the preface on from where it is now.
  const footer = draft ? (
    editing ? undefined : <Button label="Write my textbook" busy={confirming} onPress={() => shown && void confirm(shown)} />
  ) : draftError ? (
    <Button label="Write the mission draft" onPress={retryDraft} busy={retrying} />
  ) : messages.length === 0 && !busy && error ? (
    <Button label="Try again" onPress={() => void sendTurn(null)} />
  ) : messages.length > 0 || busy ? (
    <Button label="Send" onPress={() => void sendTurn(answer.trim())} disabled={!canAnswer} busy={busy}
      accessibilityLabel="Send your answer" />
  ) : undefined;

  return (
    <Page stickToBottom footer={footer}>
      <TopBar backLabel="Back to your library" kicker={mission.data?.title} />
      <View style={styles.opening}>
        <Kicker>Preface</Kicker>
        <Text style={styles.title} accessibilityRole="header">
          Before we begin
        </Text>
        <Ornament />
        <Text style={styles.intro}>
          Before a single lesson is written: why do you want to learn this? A few questions, then your mission.
        </Text>
      </View>
      {mission.data?.gateNote ? <Note>{mission.data.gateNote}</Note> : null}
      {mission.isError ? <Note tone="error">{mission.error.message}</Note> : null}

      {messages.map((m) => (
        <Turn key={m.id} role={m.role} text={m.content} />
      ))}
      {pendingAnswer ? <Turn role="USER" text={pendingAnswer} /> : null}
      {streaming !== null ? (
        <View accessibilityLiveRegion="polite">
          <Turn role="ASSISTANT" text={streaming || 'Your tutor is writing…'} />
        </View>
      ) : null}

      {error ? <Note tone="error">{error}</Note> : null}

      {draft && shown ? (
        // Keyed by content: a regenerated draft remounts the card, discarding any half-done edit of the old one.
        <MissionCard key={JSON.stringify(draft)} mission={shown} editing={editing} busy={confirming}
          onEdit={() => setEditing(true)} onCancel={() => setEditing(false)}
          onDone={(m) => {
            setEdited(m);
            setEditing(false);
          }} />
      ) : draftError ? (
        <Note tone="error">{draftError}</Note>
      ) : messages.length > 0 && !busy && !mission.isPending ? (
        <View style={styles.answer}>
          <Field
            label="Your answer"
            value={answer}
            onChangeText={setAnswer}
            multiline
            maxLength={2000}
            hint={mission.data ? `Question ${Math.min(answers + 1, mission.data.maxAnswers)} of up to ${mission.data.maxAnswers}` : undefined}
          />
        </View>
      ) : null}
    </Page>
  );
}

/** One turn of the interview: the tutor's words as reading text, the learner's as a note in the margin. */
function Turn({ role, text }: { role: 'USER' | 'ASSISTANT'; text: string }) {
  if (role === 'USER') return <MarginNote kicker="You" style={styles.turn}>{text}</MarginNote>;
  return (
    <View style={styles.turn}>
      <Kicker style={styles.speaker}>Your tutor</Kicker>
      <Text style={styles.said}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  // An opening (DESIGN.md "Screens"): kicker, a large centred serif title, the ornament, then the text.
  opening: { alignItems: 'center', marginTop: 12, marginBottom: 12 },
  title: { ...type.h1, fontSize: 36, lineHeight: 42, textAlign: 'center', marginTop: 10 },
  intro: { ...type.small, fontSize: 17, lineHeight: 27, color: colors.muted, textAlign: 'center' },
  turn: { marginTop: 24 },
  speaker: { marginBottom: 6 },
  said: { ...type.body },
  answer: { marginTop: 28 },
});
