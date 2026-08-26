import { toast } from 'sonner';
import { ApiError } from '@/shared/types/api';

/**
 * One place that turns an ApiError into a message, so every page reports
 * failures identically. Validation errors are handled by the form itself and
 * are not toasted twice.
 */
export function useToast() {
  return {
    success: (message: string) => toast.success(message),

    error: (error: unknown, fallback = 'Something went wrong') => {
      if (error instanceof ApiError) {
        if (error.isValidation) return;
        toast.error(error.message, {
          description: error.requestId ? `Reference: ${error.requestId}` : undefined,
        });
        return;
      }
      toast.error(fallback);
    },

    info: (message: string) => toast(message),
  };
}
