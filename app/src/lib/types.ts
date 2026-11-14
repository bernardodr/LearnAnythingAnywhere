// Shapes returned by the LAA server (server/src/main/java/com/laa/...). Keep in step with the records there.

export type Role = 'LEARNER' | 'PARENT' | 'OWNER';

export type User = { id: string; email: string; displayName: string | null; role: Role };

export type DevLoginResponse = { token: string; tokenType: string; expiresIn: number; user: User };

export type TextbookStatus = 'INTERVIEW' | 'ACTIVE' | 'ARCHIVED';

export type Textbook = {
  id: string;
  title: string;
  topic: string;
  status: TextbookStatus;
  coverColor: string;
  volume: number;
  missionHeadline: string | null;
  /** The topic gate's note when the topic was reframed. */
  gateNote: string | null;
  createdAt: string;
};

export type CreateTextbookResponse = {
  verdict: 'ALLOW' | 'REFRAME' | 'DECLINE';
  message: string;
  textbook: Textbook | null;
};

export type MissionDraft = {
  headline: string;
  why: string;
  successLooksLike: string[];
  constraints: string[];
  outOfScope: string[];
};

export type ChatTurn = { id: string; role: 'USER' | 'ASSISTANT'; content: string };

export type MissionState = {
  status: TextbookStatus;
  title: string;
  gateNote: string | null;
  messages: ChatTurn[];
  answers: number;
  maxAnswers: number;
  draft: MissionDraft | null;
};

export type ConfirmResponse = { path: string; version: number; content: string };

// ---- Lessons (server: com.laa.lesson.LessonController) ----

export type Source = { id: string; title: string; url: string | null; note: string };

export type LessonSummary = { id: string; seq: number; title: string; subtitle: string; read: boolean };

export type Contents = {
  id: string;
  title: string;
  status: TextbookStatus;
  missionHeadline: string | null;
  missionWhy: string | null;
  lessons: LessonSummary[];
  /** The next lesson: written after lesson `writtenAfter` is finished; `jobId` while it's being written. */
  next: { seq: number; writtenAfter: number; canWrite: boolean; jobId: string | null };
  back: {
    referenceSheets: { seq: number; lessonTitle: string; title: string; md: string }[];
    glossary: string | null;
    learned: { path: string; title: string; body: string }[];
    /** With links off, sources come without their addresses. */
    sources: Source[];
    /** The reader settings turn links off: sources can't be added. */
    linksOff: boolean;
  };
};

export type Block =
  | { id: string; type: 'heading'; text: string }
  | { id: string; type: 'text'; md: string; citations: string[] }
  | { id: string; type: 'key_idea'; text: string }
  | { id: string; type: 'callout'; kind: 'win' | 'note' | 'watch_out'; text: string }
  | { id: string; type: 'quiz'; variant: 'warmup' | 'check'; question: string; options: string[]; answer: number; explanation: string }
  | { id: string; type: 'steps'; title: string; items: string[] }
  | { id: string; type: 'free_response'; prompt: string; hintCount: number }
  | { id: string; type: 'primary_source'; resource: string; note: string }
  | { id: string; type: 'ask_tutor'; examples: string[] }
  | { id: string; type: 'diagram'; source: string };

// ---- Tutor (server: com.laa.tutor) ----

export type Verdict = 'MET' | 'PARTIAL' | 'NOT_YET';

/** The tutor's note on one attempt (BRIEF screen 6). `covered` are points the learner made; `missing` is only a count. */
export type TutorNote = {
  attemptNo: number;
  verdict: Verdict;
  covered: string[];
  missing: number;
  coachingMove: 'CONFIRM' | 'ASK' | 'HINT';
  message: string;
  hintNo?: number | null;
  evidencePending?: boolean;
};

export type HintView = { hintNo: number; total: number; text: string | null; answer: string | null; exhausted: boolean };

/** A practice block's saved progress, so the lesson page can restore it. */
export type PracticeState = {
  hintsShown: string[];
  hintCount: number;
  answer: string | null;
  met: boolean;
  evidence: string | null;
  attempts: number;
  /** The reader settings' "reveal answer after n tries"; null = never. */
  revealAfter: number | null;
  lastAnswer?: string;
  note?: TutorNote;
};

export type Lesson = {
  id: string;
  seq: number;
  kicker: string;
  title: string;
  subtitle: string;
  win: string;
  missionLink: string;
  blocks: Block[];
  referenceSheet: { title: string; md: string } | null;
  sources: Source[];
  practice: Record<string, PracticeState>;
  readAt: string | null;
  prev: number | null;
  next: number | null;
  total: number;
};

// ---- Owner console (server: com.laa.owner) ----

export type ProviderId = 'OPENAI' | 'GOOGLE' | 'ANTHROPIC' | 'OLLAMA' | 'FAKE' | 'FAKE_B';
export type TaskId =
  | 'MISSION_INTERVIEW'
  | 'RESEARCH'
  | 'LESSON_PLAN'
  | 'LESSON_WRITE'
  | 'TUTOR'
  | 'GRADE'
  | 'RECORD_UPDATE'
  | 'SAFETY_CHECK';
export type Tier = 'LARGE' | 'SMALL';

export type ModelView = {
  id: string;
  label: string;
  tier: Tier;
  inputPerMTok: number | null;
  outputPerMTok: number | null;
};

export type ProviderView = {
  id: ProviderId;
  label: string;
  configured: boolean;
  enabled: boolean;
  isDefault: boolean;
  usable: boolean;
  unavailableReason: string | null;
  tasksServed: number;
  models: ModelView[];
};

export type RouteView = {
  task: TaskId;
  providerChoice: 'DEFAULT' | ProviderId;
  resolvedProvider: ProviderId | null;
  modelTier: Tier;
  modelId: string | null;
  resolvedModel: string | null;
  params: Record<string, unknown>;
  promptVersion: string;
  fallbacks: ProviderId[];
  trafficSplit: Record<string, unknown> | null;
  problems: string[];
};

export type RunView = {
  id: string;
  task: TaskId;
  provider: ProviderId;
  model: string;
  origin: string;
  attempt: number;
  tokensIn: number | null;
  tokensOut: number | null;
  costEstimate: number | null;
  latencyMs: number;
  status: 'OK' | 'FALLBACK' | 'ERROR' | 'BLOCKED';
  errorKind: string | null;
  errorMessage: string | null;
  createdAt: string;
};

export type CompareSide = {
  provider: ProviderId;
  model: string;
  ok: boolean;
  output: string | null;
  parsed: unknown;
  errorKind: string | null;
  error: string | null;
  latencyMs: number;
  tokensIn: number | null;
  tokensOut: number | null;
  costEstimate: number | null;
  checks: {
    schemaValid: boolean | null;
    schemaErrors: string[];
    quizRules: string[] | null;
    readingAge: number | null;
    /** `passed` null: no output to check, or the check couldn't run. */
    moderation: { passed: boolean | null; flags: string[] };
  };
};

export type CompareResult = { task: TaskId; mode: string; a: CompareSide; b: CompareSide };

// ---- Reader settings (server: com.laa.profile.Profiles) ----

export type LinksPolicy = 'OFF' | 'ALLOWLIST' | 'OPEN';

export type CategoryOption = { key: string; label: string };

export type Profile = {
  id: string;
  userId: string;
  /** null = adult. */
  age: number | null;
  readingLevel: string;
  childSafe: boolean;
  /** Category keys left out of lessons. */
  categories: string[];
  /** Extra topics to leave out, in the reader's words. */
  extras: string[];
  linksPolicy: LinksPolicy;
  /** null = never reveal the answer. */
  revealAfterAttempts: number | null;
  managedByParent: boolean;
  canEdit: boolean;
  allCategories: CategoryOption[];
};

export type ProfileSettings = Pick<Profile, 'age' | 'childSafe' | 'categories' | 'extras' | 'linksPolicy' | 'revealAfterAttempts'>;

export type Child = { userId: string; email: string; displayName: string | null; profile: Profile | null };

export type ParentState = { pinSet: boolean; children: Child[] };
