import { Link } from 'react-router-dom';
import { FileQuestion } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { EmptyState } from './EmptyState';

export function NotFoundPage() {
  return (
    <div className="flex min-h-dvh items-center justify-center px-4">
      <EmptyState
        icon={FileQuestion}
        title="Page not found"
        description="The link may be out of date, or you may not have access to this part of the system."
        action={<Button asChild><Link to="/">Go back</Link></Button>}
      />
    </div>
  );
}
