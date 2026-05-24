export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  role: string;
}

export interface ExamResponse {
  id: string;
  createdBy: string;
  title: string;
  description?: string;
  durationMins: number;
  createdAt: string;
}

export interface SessionResponse {
  id: string;
  examId: string;
  supervisorId: string;
  code: string;
  status: 'PENDING' | 'ACTIVE' | 'ENDED';
  startedAt?: string;
  endedAt?: string;
}

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

export interface ParticipantStatusUpdate {
  participantId: string;
  connectionStatus: string;
}
