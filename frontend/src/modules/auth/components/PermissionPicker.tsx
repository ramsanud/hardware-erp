import { useMemo } from 'react';
import { Checkbox } from '@/shared/components/ui/checkbox';
import { Label } from '@/shared/components/ui/label';
import { Separator } from '@/shared/components/ui/separator';
import { Button } from '@/shared/components/ui/button';
import type { PermissionGroupResponse } from '../types';

interface PermissionPickerProps {
  groups: PermissionGroupResponse[];
  selected: string[];
  onChange: (permissions: string[]) => void;
  disabled?: boolean;
}

/**
 * Grouped by module, driven entirely by GET /v1/permissions/grouped. When a
 * future module's migration inserts new permissions they appear here with no
 * frontend change.
 */
export function PermissionPicker({ groups, selected, onChange, disabled }: PermissionPickerProps) {
  const selectedSet = useMemo(() => new Set(selected), [selected]);

  const toggle = (code: string) => {
    const next = new Set(selectedSet);
    if (next.has(code)) next.delete(code);
    else next.add(code);
    onChange([...next]);
  };

  const toggleGroup = (group: PermissionGroupResponse) => {
    const codes = group.permissions.map((p) => p.code);
    const allSelected = codes.every((code) => selectedSet.has(code));
    const next = new Set(selectedSet);
    codes.forEach((code) => (allSelected ? next.delete(code) : next.add(code)));
    onChange([...next]);
  };

  return (
    <div className="space-y-4">
      {groups.map((group, index) => {
        const codes = group.permissions.map((p) => p.code);
        const allSelected = codes.every((code) => selectedSet.has(code));

        return (
          <div key={group.moduleCode} className="space-y-2">
            {index > 0 ? <Separator /> : null}
            <div className="flex items-center justify-between pt-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {group.moduleCode}
              </p>
              <Button
                type="button" variant="ghost" size="sm" disabled={disabled}
                onClick={() => toggleGroup(group)}
              >
                {allSelected ? 'Clear' : 'Select all'}
              </Button>
            </div>

            <div className="grid gap-2 sm:grid-cols-2">
              {group.permissions.map((permission) => (
                <label
                  key={permission.code}
                  htmlFor={`perm-${permission.code}`}
                  className="flex cursor-pointer items-start gap-2.5 rounded-md border p-2.5 transition-colors hover:bg-accent/50"
                >
                  <Checkbox
                    id={`perm-${permission.code}`}
                    checked={selectedSet.has(permission.code)}
                    onCheckedChange={() => toggle(permission.code)}
                    disabled={disabled}
                    className="mt-0.5"
                  />
                  <span className="min-w-0">
                    <Label htmlFor={`perm-${permission.code}`} className="cursor-pointer">
                      {permission.name}
                    </Label>
                    {permission.description ? (
                      <span className="mt-0.5 block text-xs text-muted-foreground">
                        {permission.description}
                      </span>
                    ) : null}
                  </span>
                </label>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
}
