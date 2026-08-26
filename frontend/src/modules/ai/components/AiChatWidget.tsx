import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Loader2, MessageCircle, Send, Sparkles, X,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card, CardContent, CardHeader } from '@/shared/components/ui/card';
import { Input } from '@/shared/components/ui/input';
import { useAppChrome } from '@/layouts/AppChromeProvider';
import { ApiError } from '@/shared/types/api';
import { cn } from '@/shared/lib/utils';
import { aiChatService } from '../services/aiChatService';
import type { AiChatMessage } from '../types';

/**
 * Floating, non-modal so the rest of the app stays usable behind it - a
 * shop owner asking "what's Ram's balance" while looking at the invoice
 * list is the whole point. Gated to the Max subscription tier server-side
 * (AiChatService.reply -> SubscriptionService.requireTier); shown to every
 * tier so the feature is discoverable, with an upgrade prompt in place of
 * the chat input below Max.
 */
export function AiChatWidget() {
  const { subscriptionTier } = useAppChrome();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<AiChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, sending]);

  // Tier hasn't loaded yet - avoid a flash of the wrong state.
  if (subscriptionTier === null) return null;

  const isMax = subscriptionTier === 'MAX';

  const handleSend = async () => {
    const text = input.trim();
    if (!text || sending) return;
    const priorHistory = messages;
    setInput('');
    setError(null);
    setMessages((current) => [...current, { role: 'user', content: text }]);
    setSending(true);
    try {
      const { reply } = await aiChatService.chat({ message: text, history: priorHistory });
      setMessages((current) => [...current, { role: 'assistant', content: reply }]);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Something went wrong. Please try again.');
    } finally {
      setSending(false);
    }
  };

  return (
    <>
      <Button
        size="icon"
        onClick={() => setOpen((v) => !v)}
        className="fixed bottom-5 right-5 z-50 h-12 w-12 rounded-full shadow-lg"
        aria-label={open ? 'Close AI assistant' : 'Open AI assistant'}
      >
        {open ? <X className="h-5 w-5" /> : <MessageCircle className="h-5 w-5" />}
      </Button>

      {open ? (
        <Card className="fixed bottom-20 right-5 z-50 flex h-[28rem] w-[22rem] flex-col shadow-xl">
          <CardHeader className="flex-row items-center gap-2 space-y-0 border-b py-3">
            <Sparkles className="h-4 w-4 text-primary" aria-hidden />
            <p className="text-sm font-semibold">AI assistant</p>
          </CardHeader>

          {!isMax ? (
            <CardContent className="flex flex-1 flex-col items-center justify-center gap-3 text-center text-sm">
              <Sparkles className="h-8 w-8 text-muted-foreground" aria-hidden />
              <p className="font-medium">AI assistant is a Max plan feature.</p>
              <p className="text-muted-foreground">
                It answers questions about your own shop data - customer balances, low stock, sales.
              </p>
              <Button asChild size="sm" variant="outline">
                <Link to="/settings/shop" onClick={() => setOpen(false)}>Change plan in Shop Settings</Link>
              </Button>
            </CardContent>
          ) : (
            <>
              <CardContent ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto py-3 text-sm">
                {messages.length === 0 ? (
                  <p className="text-muted-foreground">
                    Ask me things like "What's Ram Sangar's outstanding balance?" or
                    "Which products are low on stock?"
                  </p>
                ) : null}
                {messages.map((turn, index) => (
                  <div
                    // eslint-disable-next-line react/no-array-index-key -- messages are only ever appended, never reordered/removed
                    key={index}
                    className={cn(
                      'max-w-[85%] rounded-lg px-3 py-2',
                      turn.role === 'user' ? 'ml-auto bg-primary text-primary-foreground' : 'bg-muted',
                    )}
                  >
                    {turn.content}
                  </div>
                ))}
                {sending ? (
                  <div className="flex items-center gap-2 text-muted-foreground">
                    <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden />
                    Thinking…
                  </div>
                ) : null}
                {error ? <p className="text-destructive">{error}</p> : null}
              </CardContent>
              <div className="flex items-center gap-2 border-t p-3">
                <Input
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void handleSend(); } }}
                  placeholder="Ask about your shop…"
                  disabled={sending}
                  autoFocus
                />
                <Button size="icon" onClick={() => void handleSend()} disabled={sending || !input.trim()} aria-label="Send">
                  <Send className="h-4 w-4" />
                </Button>
              </div>
            </>
          )}
        </Card>
      ) : null}
    </>
  );
}
