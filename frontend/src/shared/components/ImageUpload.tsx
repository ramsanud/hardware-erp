import { useRef, useState } from 'react';
import { Loader2, Trash2, Upload } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { useToast } from '@/modules/auth/hooks/useToast';

const MAX_BYTES = 2 * 1024 * 1024;
const ACCEPTED = ['image/png', 'image/jpeg', 'image/webp'];

interface ImageUploadProps {
  /** Current image URL (an authenticated endpoint), or null if none is set. */
  src: string | null;
  alt: string;
  /** Rendered when src is null. */
  fallback: React.ReactNode;
  shape?: 'circle' | 'square';
  onUpload: (file: File) => Promise<void>;
  onRemove: () => Promise<void>;
}

/**
 * Shared by profile photo and shop logo (CR-023) - preview-before-save,
 * replace, remove, format/size validation and a loading state, all in one
 * place so the two features never drift.
 */
export function ImageUpload({ src, alt, fallback, shape = 'circle', onUpload, onRemove }: ImageUploadProps) {
  const toast = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const shapeClass = shape === 'circle' ? 'rounded-full' : 'rounded-md';

  const handleFile = async (file: File) => {
    if (!ACCEPTED.includes(file.type)) {
      toast.error(new Error('Unsupported format'), 'Use a PNG, JPEG or WebP image.');
      return;
    }
    if (file.size > MAX_BYTES) {
      toast.error(new Error('Too large'), 'Image must be 2MB or smaller.');
      return;
    }

    const objectUrl = URL.createObjectURL(file);
    setPreview(objectUrl);
    setBusy(true);
    try {
      await onUpload(file);
      toast.success('Image saved.');
    } catch (caught) {
      toast.error(caught, 'Could not upload the image.');
      setPreview(null);
    } finally {
      setBusy(false);
      URL.revokeObjectURL(objectUrl);
    }
  };

  const handleRemove = async () => {
    setBusy(true);
    try {
      await onRemove();
      setPreview(null);
      toast.success('Image removed.');
    } catch (caught) {
      toast.error(caught, 'Could not remove the image.');
    } finally {
      setBusy(false);
    }
  };

  const shown = preview ?? src;

  return (
    <div className="flex items-center gap-4">
      <div className={`relative flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden border bg-muted ${shapeClass}`}>
        {shown ? (
          // eslint-disable-next-line jsx-a11y/img-redundant-alt
          <img src={shown} alt={alt} className="h-full w-full object-cover" />
        ) : fallback}
        {busy ? (
          <div className="absolute inset-0 flex items-center justify-center bg-background/70">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          </div>
        ) : null}
      </div>
      <div className="flex flex-col gap-2">
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPTED.join(',')}
          className="hidden"
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) void handleFile(file);
            event.target.value = '';
          }}
        />
        <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => inputRef.current?.click()}>
          <Upload className="h-4 w-4" />
          {shown ? 'Replace' : 'Upload'}
        </Button>
        {shown ? (
          <Button type="button" variant="outline" size="sm" disabled={busy}
                  className="text-destructive hover:text-destructive" onClick={handleRemove}>
            <Trash2 className="h-4 w-4" />
            Remove
          </Button>
        ) : null}
        <p className="text-xs text-muted-foreground">PNG, JPEG or WebP, up to 2MB.</p>
      </div>
    </div>
  );
}
