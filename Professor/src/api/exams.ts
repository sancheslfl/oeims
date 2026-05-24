import { apiFetch } from './utils';
import type { ExamResponse } from '../types';

export function getExams(token: string): Promise<ExamResponse[]> {
  return apiFetch<ExamResponse[]>('/exams', {}, token);
}

export function createExam(
  token: string,
  payload: { title: string; description?: string; durationMins: number } // TODO: Create type
): Promise<ExamResponse> {
  return apiFetch<ExamResponse>('/exams', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, token);
}
