export const LABOUR_ROUTES = {
  workers: '/labour/workers',
  attendance: '/labour/attendance',
  workerDetail: (id: number | string) => `/labour/workers/${id}`,
};

export const WORKER_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
] as const;

export const ATTENDANCE_STATUS_OPTIONS = [
  { value: 'PRESENT', label: 'Present' },
  { value: 'HALF_DAY', label: 'Half day' },
  { value: 'ABSENT', label: 'Absent' },
] as const;
