import { apiGet, apiPost } from '@/services/apiClient';
import type { AttendanceMarkRequest, WorkerAttendanceResponse } from '../types';

/** Backend: labour/controller/AttendanceController.java */
export const attendanceService = {
  mark: (body: AttendanceMarkRequest) =>
    apiPost<WorkerAttendanceResponse[]>('/v1/attendance', body),

  forDate: (date: string) =>
    apiGet<WorkerAttendanceResponse[]>('/v1/attendance', { params: { date } }),

  historyForWorker: (workerId: number, fromDate?: string, toDate?: string) =>
    apiGet<WorkerAttendanceResponse[]>(`/v1/attendance/worker/${workerId}`, { params: { fromDate, toDate } }),
};
