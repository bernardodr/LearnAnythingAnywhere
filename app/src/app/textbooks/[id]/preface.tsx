import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { MissionCard } from '@/components/MissionCard';
import { Button, Field, goBack, Kicker, Note, Ornament, Page } from '@/components/ui';
import { ApiError, apiGet, apiPost } from '@/lib/api';
import { useSession } from '@/lib/session';
import { postSse } from '@/lib/sse';
import type { ChatTurn, ConfirmResponse, MissionDraft, MissionState } from '@/lib/types';
import { colors, fonts, type } from '@/theme';

/**
 * Screen 3: the preface. A streamed interview about why the learner wants this topic (TUTOR / YOU transcript),
 * ending in a mission card they can edit and confirm ("Write my textbook" writes MISSION.md).
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

  return (
    <Page stickToBottom>
      <Button label="‹ Your shelf" variant="quiet" onPress={goBack} style={styles.back}
        accessibilityLabel="Back to your shelf" />
      <Kicker>Preface</Kicker>
      <Text style={styles.title} accessibilityRole="header">
        {mission.data?.title ?? ' '}
      </Text>
      <Text style={styles.intro}>
        Before a single lesson is written: why do you want to learn this? A few questions, then your mission.
      </Text>
      {mission.data?.gateNote ? <Note>{mission.data.gateNote}</Note> : null}
      {mission.isError ? <Note tone="error">{mission.error.message}</Note> : null}
      <Ornament />

      {messages.map((m) => (
        <Turn key={m.id} role={m.role} text={m.content} />
      ))}
      {pendingAnswer ? <Turn role="USER" text={pendingAnswer} /> : null}
      {streaming !== null ? (
        <View accessibilityLiveRegion="polite">
          <Turn role="ASSISTANT" text={streaming || '…'} />
        </View>
      ) : null}

      {error ? <Note tone="error">{error}</Note> : null}

      {draft ? (
        // Keyed by content: a regenerated draft remounts the card, discarding any half-done edit of the old one.
        <MissionCard key={JSON.stringify(draft)} draft={draft} busy={confirming} onConfirm={confirm} />
      ) : draftError ? (
        <View>
          <Note tone="error">{draftError}</Note>
          <Button label="Write the mission draft" onPress={retryDraft} busy={retrying} />
        </View>
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
          <Button label="Send" onPress={() => void sendTurn(answer.trim())} disabled={!canAnswer}
            accessibilityLabel="Send your answer" />
        </View>
      ) : null}
      {messages.length === 0 && !busy && error ? (
        <Button label="Try again" onPress={() => void sendTurn(null)} variant="secondary" />
      ) : null}
    </Page>
  );
}

function Turn({ role, text }: { role: 'USER' | 'ASSISTANT'; text: string }) {
  const you = role === 'USER';
  return (
    <View style={styles.turn}>
      <Text style={[styles.speaker, you && styles.speakerYou]}>{you ? 'You' : 'Tutor'}</Text>
      <Text style={[styles.said, you && styles.saidYou]}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  back: { alignSelf: 'flex-start', marginLeft: -8, marginBottom: 12 },
  title: { ...type.h1, marginTop: 8 },
  intro: { ...type.body, color: colors.muted, marginTop: 8 },
  turn: { marginBottom: 20 },
  speaker: { ...type.kicker, fontSize: 11, letterSpacing: 1.4, color: colors.accent, marginBottom: 4 },
  speakerYou: { color: colors.muted },
  said: { ...type.body },
  saidYou: { fontFamily: fonts.serifItalic },
  answer: { marginTop: 8 },
});
