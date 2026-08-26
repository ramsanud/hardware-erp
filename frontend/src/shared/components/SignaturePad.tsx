import { useRef, useState } from 'react';
import SignatureCanvas from 'react-signature-canvas';
import { Eraser, Loader2, Save, Trash2, Upload } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/components/ui/tabs';
import { useToast } from '@/modules/auth/hooks/useToast';

const MAX_BYTES = 2 * 1024 * 1024;

interface SignaturePadProps {
  src: string | null;
  onUpload: (file: File) => Promise<void>;
  onRemove: () => Promise<void>;
}

/**
 * Draw (react-signature-canvas, transparent-background PNG export) or
 * upload (PNG only - see ImageValidation.SIGNATURE_TYPES on the backend,
 * which rejects anything else) - CR-023. Both paths end up calling the
 * same onUpload(file), so the backend sees one consistent shape either way.
 */
export function SignaturePad({ src, onUpload, onRemove }: SignaturePadProps) {
  const toast = useToast();
  const canvasRef = useRef<SignatureCanvas>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  const clear = () => canvasRef.current?.clear();

  const saveDrawn = async () => {
    if (!canvasRef.current || canvasRef.current.isEmpty()) {
      toast.error(new Error('Empty'), 'Draw a signature first.');
      return;
    }
    setBusy(true);
    try {
      const dataUrl = canvasRef.current.getTrimmedCanvas().toDataURL('image/png');
      const blob = await (await fetch(dataUrl)).blob();
      await onUpload(new File([blob], 'signature.png', { type: 'image/png' }));
      toast.success('Signature saved.');
      clear();
    } catch (caught) {
      toast.error(caught, 'Could not save the signature.');
    } finally {
      setBusy(false);
    }
  };

  const uploadFile = async (file: File) => {
    if (file.type !== 'image/png') {
      toast.error(new Error('Unsupported'), 'Signature images must be PNG (ideally transparent background).');
      return;
    }
    if (file.size > MAX_BYTES) {
      toast.error(new Error('Too large'), 'Image must be 2MB or smaller.');
      return;
    }
    setBusy(true);
    try {
      await onUpload(file);
      toast.success('Signature saved.');
    } catch (caught) {
      toast.error(caught, 'Could not upload the signature.');
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setBusy(true);
    try {
      await onRemove();
      toast.success('Signature removed.');
    } catch (caught) {
      toast.error(caught, 'Could not remove the signature.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      {src ? (
        <div className="flex items-center gap-4">
          <div className="flex h-20 w-40 items-center justify-center rounded-md border bg-[repeating-conic-gradient(#00000008_0%_25%,transparent_0%_50%)] bg-[length:16px_16px] p-2">
            <img src={src} alt="Current signature" className="max-h-full max-w-full object-contain" />
          </div>
          <Button type="button" variant="outline" size="sm" disabled={busy}
                  className="text-destructive hover:text-destructive" onClick={remove}>
            <Trash2 className="h-4 w-4" />
            Remove
          </Button>
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">No signature saved yet.</p>
      )}

      <Tabs defaultValue="draw">
        <TabsList>
          <TabsTrigger value="draw">Draw</TabsTrigger>
          <TabsTrigger value="upload">Upload image</TabsTrigger>
        </TabsList>
        <TabsContent value="draw" className="space-y-3">
          <div className="w-fit rounded-md border bg-white">
            <SignatureCanvas
              ref={canvasRef}
              penColor="black"
              canvasProps={{ width: 360, height: 140, className: 'touch-none' }}
            />
          </div>
          <div className="flex gap-2">
            <Button type="button" variant="outline" size="sm" onClick={clear} disabled={busy}>
              <Eraser className="h-4 w-4" />
              Clear
            </Button>
            <Button type="button" size="sm" onClick={saveDrawn} disabled={busy}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              Save signature
            </Button>
          </div>
        </TabsContent>
        <TabsContent value="upload" className="space-y-3">
          <input
            ref={inputRef}
            type="file"
            accept="image/png"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) void uploadFile(file);
              event.target.value = '';
            }}
          />
          <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => inputRef.current?.click()}>
            <Upload className="h-4 w-4" />
            Choose PNG file
          </Button>
          <p className="text-xs text-muted-foreground">PNG only, ideally with a transparent background, up to 2MB.</p>
        </TabsContent>
      </Tabs>
    </div>
  );
}
