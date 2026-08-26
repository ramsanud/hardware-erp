import type { PaymentMethod } from '@/modules/invoice/types';

export type WorkerStatus = 'ACTIVE' | 'INACTIVE';
export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'HALF_DAY';
export type WorkerPaymentStatus = 'ACTIVE' | 'CANCELLED';

export interface WorkerRequest {
  name: string;
  mobileNo?: string | null;
  roleTitle?: string | null;
  dailyRatePaise: number;
}

export interface WorkerResponse {
  id: number;
  name: string;
  mobileNo?: string | null;
  roleTitle?: string | null;
  dailyRatePaise: number;
  dailyRateDisplay: string;
  status: WorkerStatus;
  createdAt: string;
}

export interface AttendanceEntryRequest {
  workerId: number;
  status: AttendanceStatus;
  projectId?: number | null;
  notes?: string | null;
}

export interface AttendanceMarkRequest {
  attendanceDate: string;
  entries: AttendanceEntryRequest[];
}

export interface WorkerAttendanceResponse {
  id: number;
  workerId: number;
  workerName: string;
  attendanceDate: string;
  status: AttendanceStatus;
  projectId?: number | null;
  projectName?: string | null;
  notes?: string | null;
  wagePaise: number;
  wageDisplay: string;
}

export interface WorkerPaymentRequest {
  workerId: number;
  amountPaise: number;
  paymentDate: string;
  paymentMethod: PaymentMethod;
  notes?: string | null;
}

export interface WorkerPaymentResponse {
  id: number;
  workerId: number;
  workerName: string;
  amountPaise: number;
  amountDisplay: string;
  paymentDate: string;
  paymentMethod: PaymentMethod;
  notes?: string | null;
  status: WorkerPaymentStatus;
  createdAt: string;
}

export interface WorkerWageSummaryResponse {
  workerId: number;
  workerName: string;
  fromDate?: string | null;
  toDate?: string | null;
  wageEarnedPaise: number;
  wageEarnedDisplay: string;
  paidPaise: number;
  paidDisplay: string;
  balancePaise: number;
  balanceDisplay: string;
}

export interface WorkerSearchParams {
  search?: string;
  status?: WorkerStatus;
  page?: number;
  size?: number;
}
