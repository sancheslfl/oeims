export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  role: string;
}

export type CreateExamRequest = {
  title: string;
  description?: string | null;
  durationMins: number;
};

export type ExamResponse = {
  id: string;
  createdBy: string;
  title: string;
  description: string | null;
  durationMins: number;
  createdAt: string;
};

export type SessionStatus = "PENDING" | "ACTIVE" | "ENDED";

export type SessionResponse = {
  id: string;
  examId: string;
  supervisorId: string;
  code: string;
  status: SessionStatus;
  startedAt: string | null;
  endedAt: string | null;
};

export interface ParticipantResponse {
  id: string;
  sessionId: string;
  userId: string;
  email: string;
  connectionStatus: 'CONNECTED' | 'DISCONNECTED' | 'TIMED_OUT';
  lastHeartbeat?: string;
  joinedAt: string;
}

export interface EventResponse {
  id: string;
  participantId: string;
  monitorName: string;
  message: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  occurredAt: string;
}

export type ParticipantStatusResponse = {
  participantId: string;
  connectionStatus: "CONNECTED" | "DISCONNECTED" | "TIMED_OUT";
};

export type OpenedSession = {
  exam: ExamResponse;
  session: SessionResponse;
};

export const USER_ROLES = {
  Professor: "PROFESSOR",
} as const;

export type UserRole = typeof USER_ROLES[keyof typeof USER_ROLES];
