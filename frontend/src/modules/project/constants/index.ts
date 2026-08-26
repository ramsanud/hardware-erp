export const PROJECT_ROUTES = {
  list: '/projects',
  create: '/projects/new',
  detail: (id: number | string) => `/projects/${id}`,
  edit: (id: number | string) => `/projects/${id}/edit`,
};

export const PROJECT_STATUS_OPTIONS: { value: import('../types').ProjectStatus; label: string }[] = [
  { value: 'UPCOMING', label: 'Upcoming' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'ON_HOLD', label: 'On hold' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'COMPLETED', label: 'Completed' },
];

export const PROJECT_EXPENSE_CATEGORY_OPTIONS: { value: import('../types').ProjectExpenseCategory; label: string }[] = [
  { value: 'LABOUR', label: 'Labour' },
  { value: 'EMPLOYEE', label: 'Employee' },
  { value: 'FOOD', label: 'Food' },
  { value: 'STAY', label: 'Stay' },
  { value: 'PETROL', label: 'Petrol / travel' },
  { value: 'OTHER', label: 'Other' },
];
