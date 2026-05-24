import { apiFetch } from './utils';
import type { SessionResponse, ParticipantResponse, EventResponse } from '../types';

export const createSession = (token: string, examId: string): Promise<SessionResponse> =>
  apiFetch<SessionResponse>('/sessions', { method: 'POST', body: JSON.stringify({ examId }) }, token);

export const getSession = (token: string, id: string): Promise<SessionResponse> =>
  apiFetch<SessionResponse>(`/sessions/${id}`, {}, token);

export const startSession = (token: string, id: string): Promise<SessionResponse> =>
  apiFetch<SessionResponse>(`/sessions/${id}/start`, { method: 'POST' }, token);

export const endSession = (token: string, id: string): Promise<SessionResponse> =>
  apiFetch<SessionResponse>(`/sessions/${id}/end`, { method: 'POST' }, token);

export const getParticipants = (token: string, id: string): Promise<ParticipantResponse[]> =>
  apiFetch<ParticipantResponse[]>(`/sessions/${id}/participants`, {}, token);

export const getEvents = (token: string, id: string): Promise<EventResponse[]> =>
  apiFetch<EventResponse[]>(`/sessions/${id}/events`, {}, token);
