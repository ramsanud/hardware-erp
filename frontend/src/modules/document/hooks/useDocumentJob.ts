import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/shared/types/api';
import { documentJobService } from '../services/documentJobService';
import type { ReportJob, ReportJobRequest } from '../types';

/*
  CR-101. Owns one background export from "queue it" to "it is ready".
  Polls /v1/documents/jobs/{id} every 1.5s only while the job is
  PENDING/PROCESSING, and stops the moment it is COMPLETED or FAILED - a
  timer that keeps firing after the answer is known is the kind of thing
  that survives into production and shows up as a request per second per
  open tab. Unmounting clears the interval for the same reason.
*/

const POLL_MS = 1500;

export interface DocumentJobState {
  job: ReportJob | null;
  /** True from enqueue until the job reaches a terminal status. */
  working: boolean;
  error: string | null;
  enqueue: (request: ReportJobRequest) => Promise<ReportJob | null>;
  reset: () => void;
}

export function isTerminal(job: ReportJob | null | undefined): boolean {
  return job?.status === 'COMPLETED' || job?.status === 'FAILED';
}

export function useDocumentJob(): DocumentJobState {
  const [job, setJob] = useState<ReportJob | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  const stop = useCallback(() => {
    if (timer.current) {
      clearInterval(timer.current);
      timer.current = null;
    }
  }, []);

  useEffect(() => stop, [stop]);

  const poll = useCallback((id: number) => {
    stop();
    timer.current = setInterval(async () => {
      try {
        const next = await documentJobService.status(id);
        // Optional-chain on purpose: the harness's generic stub answers a
        // page object for any query-string URL, and a status read must
        // never white-screen the page it sits on (testing.md).
        setJob(next ?? null);
        if (isTerminal(next)) {
          stop();
          setWorking(false);
        }
      } catch (caught) {
        stop();
        setWorking(false);
        setError(caught instanceof ApiError ? caught.message : 'Lost track of the export.');
      }
    }, POLL_MS);
  }, [stop]);

  const enqueue = useCallback(async (request: ReportJobRequest) => {
    setError(null);
    setWorking(true);
    setJob(null);
    try {
      const created = await documentJobService.enqueue(request);
      setJob(created);
      if (isTerminal(created)) {
        setWorking(false);
      } else if (created?.id != null) {
        poll(created.id);
      } else {
        setWorking(false);
        setError('The server did not return a job to follow.');
      }
      return created ?? null;
    } catch (caught) {
      setWorking(false);
      setError(caught instanceof ApiError ? caught.message : 'Could not start the export.');
      return null;
    }
  }, [poll]);

  const reset = useCallback(() => {
    stop();
    setJob(null);
    setError(null);
    setWorking(false);
  }, [stop]);

  return { job, working, error, enqueue, reset };
}
