import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { Button, Kicker, Note, type Option, Select } from '@/components/ui';
import { ApiError, apiGet, apiPatch, apiPost } from '@/lib/api';
import type { CallView, CompareResult, CompareSide, ProviderId, ProviderView, RouteView, RunView, TaskId, Tier } from '@/lib/types';
import { colors, fonts, minTouch, type } from '@/theme';

/** Plain names for what each task does, as the owner thinks of it. */
export const TASK_LABEL: Record<TaskId, string> = {
  MISSION_INTERVIEW: 'Mission interview',
  LESSON_PLAN: 'Lesson planning',
  LESSON_WRITE: 'Lesson writing',
  DIAGRAM: 'Diagrams',
  TUTOR: 'Tutor chat',
  GRADE: 'Grading practice',
  RECORD_UPDATE: 'Learning records',
  SAFETY_CHECK: 'Safety checks',
  RESEARCH: 'Research (not used yet)',
};
const TASKS = Object.keys(TASK_LABEL) as TaskId[];

const STATUS: Record<string, { fg: string; bg: string }> = {
  OK: { fg: '#2F6B3A', bg: '#E5F1E6' },
  FALLBACK: { fg: '#7A5A12', bg: '#F6EBD3' },
  ERROR: { fg: colors.accent, bg: '#F8E5E1' },
  BLOCKED: { fg: colors.paper, bg: colors.ink },
};

type Changed = () => void;

/** The registered calls (server: llm/Calls), for Compare and the run log. */
function useCalls(token: string | null) {
  return useQuery({ queryKey: ['owner', 'calls'], queryFn: () => apiGet<CallView[]>('/owner/calls', token), enabled: !!token });
}

/** A request to start from, per call, so a comparison runs on something that call would really see. */
const SAMPLE: Record<string, string> = {
  LESSON_WRITE: 'Write lesson 2 from this plan: {"title": "Placing your feet on purpose", "skill": "Choose and place each foot deliberately"}',
  DIAGRAM: 'Draw one interactive diagram for lesson 2, "Placing your feet on purpose".\n\nWhat it shows, and how the learner explores it:\nA climbing shoe with four parts to tap: the toe, the inside edge, the outside edge and the heel. Tapping a part says when you use it on the wall.\n\nThe caption under it: Tap each part of the shoe to see when you use it.',
  MISSION_INTERVIEW: 'I want to climb a V3 with my friends by spring.',
  'MISSION_INTERVIEW.draft': 'Interview transcript:\nLEARNER: I want to climb a V3 with my friends by spring.\nLEARNER: Twice a week at my local gym.',
  GRADE: "Attempt 1. The learner's answer:\nI'd practise one small step, like a single move, ten times.",
  TUTOR: 'Why do small steps work better than big ones?',
  'SAFETY_CHECK.topic': 'Topic: Bouldering',
  'SAFETY_CHECK.content': '<item id="a">\nVolcanoes erupt when pressure builds up under the ground.\n</item>',
  'SAFETY_CHECK.leak': 'Tutor message to check:\nGood start. What happens straight after you try it once?',
};

function useOwnerData(token: string | null) {
  const providers = useQuery({ queryKey: ['owner', 'providers'], queryFn: () => apiGet<ProviderView[]>('/owner/providers', token), enabled: !!token });
  const routes = useQuery({ queryKey: ['owner', 'routes'], queryFn: () => apiGet<RouteView[]>('/owner/routes', token), enabled: !!token });
  return { providers, routes };
}

export function useOwner(token: string | null) {
  const queryClient = useQueryClient();
  const data = useOwnerData(token);
  const refresh: Changed = () => void queryClient.invalidateQueries({ queryKey: ['owner'] });
  return { ...data, refresh };
}

function errorText(e: unknown): string {
  if (e instanceof ApiError) return e.errors.length > 0 ? `${e.message}: ${e.errors.join('; ')}` : e.message;
  return 'That change couldn’t be made.';
}

// ------------------------------------------------------------------ providers

export function ProvidersPanel({ providers, token, onChanged }: { providers: ProviderView[]; token: string | null; onChanged: Changed }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const def = providers.find((p) => p.isDefault);

  async function patch(id: ProviderId, body: Record<string, boolean>) {
    setBusy(id);
    setError(null);
    try {
      await apiPatch(`/owner/providers/${id}`, body, token);
      onChanged();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(null);
    }
  }

  const usable: Option<ProviderId>[] = providers.filter((p) => p.usable).map((p) => ({ value: p.id, label: p.label }));
  return (
    <View>
      <Text style={styles.h2} accessibilityRole="header">
        AI services
      </Text>
      <Text style={styles.intro}>
        Every task follows the default service unless you route it elsewhere below. Changes apply to the very next request.
      </Text>
      {def ? (
        <View style={styles.defaultRow}>
          <Select label="Default service" value={def.id} options={usable} onChange={(id) => void patch(id, { isDefault: true })}
            disabled={busy !== null} />
        </View>
      ) : null}
      {error ? <Note tone="error">{error}</Note> : null}
      <View style={styles.cards}>
        {providers.map((p) => {
          const status = p.isDefault ? 'Default' : !p.configured ? 'Not set up' : !p.enabled ? 'Turned off' : 'Ready';
          return (
            <View key={p.id} style={[styles.card, p.isDefault && styles.cardDefault]} accessibilityLabel={`${p.label}: ${status}`}>
              <View style={styles.cardHead}>
                <Text style={styles.cardTitle}>{p.label}</Text>
                <Text style={[styles.badge, p.isDefault ? styles.badgeOn : p.usable ? styles.badgeReady : styles.badgeOff]}>{status}</Text>
              </View>
              <Text style={styles.small}>Handles {p.tasksServed === 1 ? '1 task' : `${p.tasksServed} tasks`}</Text>
              {!p.configured && p.unavailableReason ? <Text style={styles.muted}>{p.unavailableReason}</Text> : null}
              <View style={styles.models}>
                {p.models.map((m) => (
                  <Text key={m.id} style={styles.model}>
                    {m.label} <Text style={styles.muted}>· {m.tier === 'LARGE' ? 'Large' : 'Small'}</Text>
                  </Text>
                ))}
              </View>
              <View style={styles.cardActions}>
                {!p.isDefault && p.usable ? (
                  <Button label="Make default" variant="secondary" onPress={() => void patch(p.id, { isDefault: true })}
                    busy={busy === p.id} disabled={busy !== null} accessibilityLabel={`Make ${p.label} the default`} />
                ) : null}
                {p.configured && !p.isDefault ? (
                  <Button label={p.enabled ? 'Turn off' : 'Turn on'} variant="quiet" onPress={() => void patch(p.id, { enabled: !p.enabled })}
                    disabled={busy !== null} accessibilityLabel={`${p.enabled ? 'Turn off' : 'Turn on'} ${p.label}`} />
                ) : null}
              </View>
            </View>
          );
        })}
      </View>
    </View>
  );
}

// ------------------------------------------------------------------ routing

export function RoutingTable({ routes, providers, token, onChanged }: { routes: RouteView[]; providers: ProviderView[]; token: string | null; onChanged: Changed }) {
  const [errors, setErrors] = useState<Partial<Record<TaskId, string>>>({});
  const label = (id: ProviderId | null) => providers.find((p) => p.id === id)?.label ?? id ?? 'none';
  const def = providers.find((p) => p.isDefault);

  async function patch(task: TaskId, body: Record<string, unknown>) {
    setErrors((e) => ({ ...e, [task]: undefined }));
    try {
      await apiPatch(`/owner/routes/${task}`, body, token);
      onChanged();
    } catch (e) {
      setErrors((x) => ({ ...x, [task]: errorText(e) }));
    }
  }

  return (
    <View>
      <Text style={styles.h2} accessibilityRole="header">
        Routing
      </Text>
      <Text style={styles.intro}>
        Choose who handles each task, which size of model it uses, and who takes over if it fails. A choice a task can’t use
        (for example, a model without a capability it needs) is refused, with the reason.
      </Text>
      <View style={styles.tableHead}>
        <Text style={[styles.th, styles.colTask]}>Task</Text>
        <Text style={[styles.th, styles.colPick]}>Service</Text>
        <Text style={[styles.th, styles.colPick]}>Model</Text>
        <Text style={[styles.th, styles.colFallback]}>If it fails, try</Text>
        <Text style={[styles.th, styles.colTraffic]}>Traffic</Text>
      </View>
      {TASKS.map((task) => {
        const r = routes.find((x) => x.task === task);
        if (!r) return null;
        const resolved = providers.find((p) => p.id === r.resolvedProvider);
        const serviceOptions: Option<string>[] = [
          { value: 'DEFAULT', label: `Default (${def?.label ?? 'none set'})` },
          ...providers.filter((p) => p.usable).map((p) => ({ value: p.id, label: p.label })),
        ];
        const modelOptions: Option<string>[] = [
          { value: 'TIER:LARGE', label: 'Large (best quality)' },
          { value: 'TIER:SMALL', label: 'Small (faster, cheaper)' },
          ...(resolved?.models ?? []).map((m) => ({ value: `ID:${m.id}`, label: m.label })),
        ];
        const modelValue = r.modelId ? `ID:${r.modelId}` : `TIER:${r.modelTier}`;
        return (
          <View key={task} style={styles.row}>
            <View style={styles.colTask}>
              <Text style={styles.taskName}>{TASK_LABEL[task]}</Text>
              <Text style={styles.muted}>{label(r.resolvedProvider)} · {r.resolvedModel ?? 'no model'}</Text>
            </View>
            <View style={styles.colPick}>
              <Select compact label={`Service for ${TASK_LABEL[task]}`} value={r.providerChoice} options={serviceOptions}
                onChange={(v) => void patch(task, { providerChoice: v })} />
            </View>
            <View style={styles.colPick}>
              <Select compact label={`Model for ${TASK_LABEL[task]}`} value={modelValue} options={modelOptions}
                onChange={(v) => void patch(task, v.startsWith('TIER:') ? { modelTier: v.slice(5), modelId: null } : { modelId: v.slice(3) })} />
            </View>
            <View style={[styles.colFallback, styles.chips]}>
              {providers.filter((p) => p.usable && p.id !== r.resolvedProvider).map((p) => {
                const on = r.fallbacks.includes(p.id);
                return (
                  <Pressable key={p.id} accessibilityRole="checkbox" accessibilityState={{ checked: on }} aria-checked={on}
                    accessibilityLabel={`${TASK_LABEL[task]}: fall back to ${p.label}`}
                    onPress={() => void patch(task, { fallbacks: on ? r.fallbacks.filter((f) => f !== p.id) : [...r.fallbacks, p.id] })}
                    style={[styles.chip, on && styles.chipOn]}>
                    <Text style={[styles.chipText, on && styles.chipTextOn]}>{on ? `${r.fallbacks.indexOf(p.id) + 1}. ` : ''}{p.label}</Text>
                  </Pressable>
                );
              })}
            </View>
            <Text style={[styles.small, styles.colTraffic]}>{r.trafficSplit ? JSON.stringify(r.trafficSplit) : 'All to its service'}</Text>
            {errors[task] ? <View style={styles.rowError}><Note tone="error">{errors[task]}</Note></View> : null}
            {r.problems.length > 0 ? <View style={styles.rowError}><Note tone="error">{r.problems.join(' ')}</Note></View> : null}
          </View>
        );
      })}
    </View>
  );
}

// ------------------------------------------------------------------ run log

export function RunLog({ token, providers }: { token: string | null; providers: ProviderView[] }) {
  const [task, setTask] = useState<string>('ALL');
  const [provider, setProvider] = useState<string>('ALL');
  const query = new URLSearchParams({ limit: '50' });
  if (task !== 'ALL') query.set('task', task);
  if (provider !== 'ALL') query.set('provider', provider);
  const runs = useQuery({
    queryKey: ['owner', 'runs', task, provider],
    queryFn: () => apiGet<RunView[]>(`/owner/runs?${query.toString()}`, token),
    enabled: !!token,
  });
  const calls = useCalls(token);
  const total = (runs.data ?? []).reduce((sum, r) => sum + (r.costEstimate ?? 0), 0);
  const label = (id: string) => providers.find((p) => p.id === id)?.label ?? id;
  /** The call's own name where the task has several (the three safety checks); otherwise the task's. */
  const what = (r: RunView) => {
    const call = r.call ? calls.data?.find((c) => c.id === r.call) : undefined;
    return call && (calls.data ?? []).filter((c) => c.task === r.task).length > 1 ? call.label : (TASK_LABEL[r.task] ?? r.task);
  };

  return (
    <View>
      <Text style={styles.h2} accessibilityRole="header">
        Run log
      </Text>
      <Text style={styles.intro}>Every request to an AI service, newest first: who handled it, how long it took, and roughly what it cost.</Text>
      <View style={styles.filters}>
        <View style={styles.filter}>
          <Select label="Task" value={task} onChange={setTask}
            options={[{ value: 'ALL', label: 'All tasks' }, ...TASKS.map((t) => ({ value: t, label: TASK_LABEL[t] }))]} />
        </View>
        <View style={styles.filter}>
          <Select label="Service" value={provider} onChange={setProvider}
            options={[{ value: 'ALL', label: 'All services' }, ...providers.map((p) => ({ value: p.id, label: p.label }))]} />
        </View>
        <Button label="Refresh" variant="quiet" onPress={() => void runs.refetch()} style={styles.refresh} />
      </View>
      <Text style={styles.small}>
        {runs.data?.length ?? 0} requests shown · about ${total.toFixed(4)} in total
      </Text>
      {(runs.data ?? []).map((r) => (
        <View key={r.id} style={styles.run}>
          <Text style={[styles.badge, { color: STATUS[r.status].fg, backgroundColor: STATUS[r.status].bg }]}>{r.status}</Text>
          <View style={styles.runMain}>
            <Text style={styles.runTitle}>
              {what(r)} <Text style={styles.muted}>· {label(r.provider)} · {r.model}</Text>
            </Text>
            <Text style={styles.muted}>
              {new Date(r.createdAt).toLocaleString()} · {(r.latencyMs / 1000).toFixed(1)}s · {r.tokensIn ?? '?'} in / {r.tokensOut ?? '?'} out
              {r.costEstimate != null ? ` · $${r.costEstimate.toFixed(4)}` : ''}
              {r.origin !== 'APP' ? ` · ${r.origin.toLowerCase()}` : ''}
            </Text>
            {r.errorMessage ? <Text style={styles.runError} numberOfLines={2}>{r.errorKind}: {r.errorMessage}</Text> : null}
          </View>
        </View>
      ))}
    </View>
  );
}

// ------------------------------------------------------------------ compare

export function ComparePanel({ token, providers, routes, onChanged }: { token: string | null; providers: ProviderView[]; routes: RouteView[]; onChanged: Changed }) {
  const usable = providers.filter((p) => p.usable);
  const calls = useCalls(token);
  const [callId, setCallId] = useState('LESSON_WRITE');
  const call = calls.data?.find((c) => c.id === callId);
  const task: TaskId = call?.task ?? 'LESSON_WRITE';
  const siblings = (calls.data ?? []).filter((c) => c.task === task);
  const [message, setMessage] = useState(SAMPLE.LESSON_WRITE);
  // A: the default service (what most tasks use now); B: another one to weigh against it.
  const current = usable.find((p) => p.isDefault) ?? usable[0];
  const other = usable.find((p) => p.id !== current?.id) ?? current;
  const [a, setA] = useState<string>(current?.id ?? 'FAKE');
  const [b, setB] = useState<string>(other?.id ?? 'FAKE');
  const [tierA, setTierA] = useState<Tier>('SMALL');
  const [tierB, setTierB] = useState<Tier>('SMALL');
  const [result, setResult] = useState<CompareResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [applied, setApplied] = useState<string | null>(null);
  const providerOptions: Option<string>[] = usable.map((p) => ({ value: p.id, label: p.label }));
  const tierOptions: Option<Tier>[] = [{ value: 'LARGE', label: 'Large' }, { value: 'SMALL', label: 'Small' }];

  async function run() {
    setBusy(true);
    setError(null);
    setApplied(null);
    try {
      setResult(await apiPost<CompareResult>('/owner/compare', {
        call: callId, input: { message, variables: {} },
        a: { provider: a, modelTier: tierA }, b: { provider: b, modelTier: tierB },
      }, token));
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  async function use(side: CompareSide) {
    setError(null);
    try {
      // A service can't be its own fallback, so switching to it also takes it off the task's fallback list.
      const fallbacks = (routes.find((r) => r.task === task)?.fallbacks ?? []).filter((f) => f !== side.provider);
      await apiPatch(`/owner/routes/${task}`, { providerChoice: side.provider, modelId: side.model, fallbacks }, token);
      setApplied(`${TASK_LABEL[task]} now uses ${side.model}.`);
      onChanged();
    } catch (e) {
      setError(errorText(e));
    }
  }

  return (
    <View>
      <Text style={styles.h2} accessibilityRole="header">
        Compare before switching
      </Text>
      <Text style={styles.intro}>
        Run the same request through two services side by side, see the results and their automatic checks, then switch the
        task to the one you prefer.
      </Text>
      <View style={styles.filters}>
        <View style={styles.filter}>
          <Select label="Call" value={callId}
            onChange={(id) => {
              setCallId(id);
              setResult(null);
              if (SAMPLE[id]) setMessage(SAMPLE[id]);
            }}
            options={(calls.data ?? []).filter((c) => c.task !== 'RESEARCH').map((c) => ({ value: c.id, label: c.label }))} />
        </View>
      </View>
      {siblings.length > 1 ? (
        <Text style={styles.small}>
          Switching moves the whole task, {TASK_LABEL[task]}, so all its calls change together: {siblings.map((c) => c.label).join(', ')}.
        </Text>
      ) : null}
      <Text style={styles.label} nativeID="compare-input">Request to send</Text>
      <TextInput accessibilityLabel="Request to send" aria-labelledby="compare-input" value={message} onChangeText={setMessage} multiline
        style={styles.input} />
      <View style={styles.filters}>
        <View style={styles.filter}><Select label="A: service" value={a} options={providerOptions} onChange={setA} /></View>
        <View style={styles.filter}><Select label="A: size" value={tierA} options={tierOptions} onChange={setTierA} /></View>
        <View style={styles.filter}><Select label="B: service" value={b} options={providerOptions} onChange={setB} /></View>
        <View style={styles.filter}><Select label="B: size" value={tierB} options={tierOptions} onChange={setTierB} /></View>
      </View>
      <Button label="Run comparison" onPress={() => void run()} busy={busy} disabled={!message.trim()} style={styles.runButton} />
      {error ? <Note tone="error">{error}</Note> : null}
      {applied ? <Note>{applied}</Note> : null}
      {result ? (
        <View style={styles.sides}>
          {(['a', 'b'] as const).map((key) => (
            <CompareColumn key={key} name={key.toUpperCase()} side={result[key]} label={providers.find((p) => p.id === result[key].provider)?.label ?? result[key].provider}
              onUse={() => void use(result[key])} />
          ))}
        </View>
      ) : null}
    </View>
  );
}

function CompareColumn({ name, side, label, onUse }: { name: string; side: CompareSide; label: string; onUse: () => void }) {
  const c = side.checks;
  const pretty = side.parsed ? JSON.stringify(side.parsed, null, 2) : (side.output ?? '');
  return (
    <View style={styles.side} accessibilityLabel={`Result ${name}: ${label}`}>
      <Kicker>{name} · {label}</Kicker>
      <Text style={styles.runTitle}>{side.model}</Text>
      <Text style={styles.muted}>
        {side.ok ? 'Answered' : `Failed: ${side.errorKind ?? ''} ${side.error ?? ''}`} · {(side.latencyMs / 1000).toFixed(1)}s
        {side.costEstimate != null ? ` · $${side.costEstimate.toFixed(4)}` : ''}
      </Text>
      <View style={styles.checks}>
        {c.schemaValid != null ? <Check ok={c.schemaValid} text={c.schemaValid ? 'Right shape' : `Wrong shape: ${c.schemaErrors.join('; ')}`} /> : null}
        {c.quizRules != null ? <Check ok={c.quizRules.length === 0} text={c.quizRules.length === 0 ? 'Passes the lesson rules' : `Breaks the lesson rules: ${c.quizRules.join(' ')}`} /> : null}
        {c.readingAge != null ? <Check ok text={`Reads like it’s for age ${Math.round(c.readingAge)}`} /> : null}
        {c.moderation.passed != null ? (
          <Check ok={c.moderation.passed} text={c.moderation.passed ? 'Passes the safety check' : `Blocked by the safety check: ${c.moderation.flags.join('; ')}`} />
        ) : c.moderation.flags.length > 0 ? (
          <Check ok={false} text={c.moderation.flags.join(' ')} />
        ) : null}
      </View>
      <Text style={styles.output} selectable numberOfLines={24}>{pretty}</Text>
      {side.ok ? <Button label={`Use ${name} for this task`} variant="secondary" onPress={onUse} style={styles.useButton} /> : null}
    </View>
  );
}

function Check({ ok, text }: { ok: boolean; text: string }) {
  return (
    <Text style={styles.small}>
      <Text style={{ color: ok ? '#2F6B3A' : colors.accent }}>{ok ? '✓  ' : '✗  '}</Text>
      {text}
    </Text>
  );
}

const styles = StyleSheet.create({
  h2: { ...type.h2, marginTop: 36, marginBottom: 6 },
  intro: { ...type.body, fontSize: 15, lineHeight: 22, color: colors.muted, marginBottom: 14 },
  small: { fontFamily: fonts.sans, fontSize: 13, lineHeight: 19, color: colors.ink },
  muted: { fontFamily: fonts.sans, fontSize: 12, lineHeight: 18, color: colors.muted },
  label: { ...type.kicker, marginBottom: 8 },
  defaultRow: { maxWidth: 320 },
  cards: { flexDirection: 'row', flexWrap: 'wrap', gap: 14 },
  card: { width: 250, borderWidth: 1, borderColor: colors.rule, borderRadius: 6, padding: 14, backgroundColor: colors.field, gap: 6 },
  cardDefault: { borderColor: colors.accent },
  cardHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  cardTitle: { fontFamily: fonts.sansSemiBold, fontSize: 15, color: colors.ink, flexShrink: 1 },
  badge: { fontFamily: fonts.sansSemiBold, fontSize: 11, letterSpacing: 0.5, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10, overflow: 'hidden' },
  badgeOn: { color: colors.paper, backgroundColor: colors.accent },
  badgeReady: { color: '#2F6B3A', backgroundColor: '#E5F1E6' },
  badgeOff: { color: colors.muted, backgroundColor: colors.noteFill },
  models: { marginTop: 4 },
  model: { fontFamily: fonts.sans, fontSize: 13, lineHeight: 20, color: colors.ink },
  cardActions: { flexDirection: 'row', gap: 6, flexWrap: 'wrap', marginTop: 4 },
  tableHead: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: colors.rule, paddingBottom: 6, gap: 12 },
  th: { ...type.kicker, fontSize: 11 },
  row: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'flex-start', gap: 12, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: colors.rule },
  rowError: { flexBasis: '100%' },
  colTask: { width: 190 },
  colPick: { width: 200 },
  colFallback: { flex: 1, minWidth: 180 },
  colTraffic: { width: 110 },
  taskName: { fontFamily: fonts.sansSemiBold, fontSize: 14, color: colors.ink },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  chip: { minHeight: 32, justifyContent: 'center', paddingHorizontal: 10, borderRadius: 16, borderWidth: 1, borderColor: colors.rule, backgroundColor: colors.field },
  chipOn: { borderColor: colors.accent, backgroundColor: colors.noteFill },
  chipText: { fontFamily: fonts.sans, fontSize: 12, color: colors.ink },
  chipTextOn: { color: colors.accent, fontFamily: fonts.sansSemiBold },
  filters: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, alignItems: 'flex-end' },
  filter: { width: 220 },
  refresh: { marginBottom: 18 },
  run: { flexDirection: 'row', gap: 10, alignItems: 'flex-start', paddingVertical: 8, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.rule },
  runMain: { flex: 1 },
  runTitle: { fontFamily: fonts.sansSemiBold, fontSize: 13, color: colors.ink },
  runError: { fontFamily: fonts.sans, fontSize: 12, color: colors.accent, marginTop: 2 },
  input: {
    minHeight: 80, borderWidth: 1, borderColor: colors.rule, borderRadius: 4, padding: 12, backgroundColor: colors.field,
    fontFamily: fonts.sans, fontSize: 14, color: colors.ink, textAlignVertical: 'top', marginBottom: 14,
  },
  runButton: { alignSelf: 'flex-start', marginTop: 4, minHeight: minTouch },
  sides: { flexDirection: 'row', flexWrap: 'wrap', gap: 16, marginTop: 16 },
  side: { flex: 1, minWidth: 300, borderWidth: 1, borderColor: colors.rule, borderRadius: 6, padding: 14, backgroundColor: colors.field, gap: 4 },
  checks: { marginVertical: 8, gap: 2 },
  output: { fontFamily: 'ui-monospace, Menlo, monospace', fontSize: 12, lineHeight: 17, color: colors.ink, backgroundColor: colors.noteFill, padding: 10, borderRadius: 4 },
  useButton: { alignSelf: 'flex-start', marginTop: 10 },
});
