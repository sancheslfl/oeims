import { apiFetch } from "./utils";
import type { CreateExamRequest, ExamResponse } from "../types";

export function createExam(
    request: CreateExamRequest,
    token: string,
): Promise<ExamResponse> {
  return apiFetch<ExamResponse>("/exams", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(request),
  });
}

export function getExams(token: string, title?: string): Promise<ExamResponse[]> {
  const query = title ? `?title=${encodeURIComponent(title)}` : "";

  return apiFetch<ExamResponse[]>(`/exams${query}`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function getExam(id: string, token: string): Promise<ExamResponse> {
  return apiFetch<ExamResponse>(`/exams/${id}`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}
