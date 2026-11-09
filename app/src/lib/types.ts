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
