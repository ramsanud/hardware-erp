/** Mirrors com.hardware.erp.supportticket.dto.* */

export type TicketCategory =
  | 'LOGIN' | 'INVOICE' | 'PAYMENT' | 'PURCHASE' | 'INVENTORY'
  | 'WHATSAPP' | 'SUBSCRIPTION' | 'TECHNICAL' | 'OTHER';

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'WAITING_FOR_USER' | 'RESOLVED' | 'CLOSED';

export interface CreateTicketRequest {
  subject: string;
  description: string;
  category: TicketCategory;
}

export interface TicketMessageRequest {
  message: string;
  internal: boolean;
}

export type MessageAuthorType = 'TENANT_USER' | 'PLATFORM_ADMIN';

export interface TicketMessageResponse {
  id: number;
  authorType: MessageAuthorType;
  authorName: string;
  message: string;
  internal: boolean;
  createdAt: string;
}

export interface SupportTicketSummaryResponse {
  id: number;
  tenantName: string | null;
  subject: string;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  assignedAdminId: number | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface SupportTicketDetailResponse {
  id: number;
  tenantName: string;
  raisedByName: string;
  subject: string;
  description: string;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  assignedAdminId: number | null;
  createdAt: string;
  updatedAt: string | null;
  resolvedAt: string | null;
  messages: TicketMessageResponse[];
}
