import { Pencil, Trash2, User } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import { EmptyState } from '@/shared/components/EmptyState';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import type { SupplierContactResponse } from '../types';

interface ContactListProps {
  contacts: SupplierContactResponse[];
  onEdit: (contact: SupplierContactResponse) => void;
  onDelete: (contact: SupplierContactResponse) => void;
}

export function ContactList({ contacts, onEdit, onDelete }: ContactListProps) {
  if (contacts.length === 0) {
    return (
      <EmptyState
        icon={User}
        title="No contacts recorded"
        description="Add the people at this supplier who actually answer the phone."
      />
    );
  }

  return (
    <ul className="divide-y">
      {contacts.map((contact) => (
        <li key={contact.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="min-w-0">
            <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
              {contact.contactName}
              {contact.primary ? <Badge variant="success">Primary</Badge> : null}
            </p>
            <p className="tabular mt-0.5 text-xs text-muted-foreground">
              {contact.designation ? `${contact.designation} · ` : ''}{contact.mobileNo}
              {contact.email ? ` · ${contact.email}` : ''}
            </p>
          </div>

          <PermissionGate permission={PERMISSIONS.SUPPLIER_MANAGE}>
            <div className="flex items-center gap-1 self-start sm:self-auto">
              <Button
                variant="ghost" size="icon" className="h-8 w-8"
                onClick={() => onEdit(contact)}
                aria-label={`Edit ${contact.contactName}`}
              >
                <Pencil className="h-4 w-4" />
              </Button>
              <Button
                variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive"
                onClick={() => onDelete(contact)}
                aria-label={`Remove ${contact.contactName}`}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          </PermissionGate>
        </li>
      ))}
    </ul>
  );
}
