import { useEffect, useState } from 'react';
import { useNavigate, useParams, Navigate } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { ProjectForm } from '../forms/ProjectForm';
import { projectService } from '../services/projectService';
import { PROJECT_ROUTES } from '../constants';
import type { ProjectRequest, ProjectResponse } from '../types';

export function ProjectEditPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const navigate = useNavigate();
  const toast = useToast();

  const [project, setProject] = useState<ProjectResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    if (!id || Number.isNaN(id)) return;
    setLoading(true);
    projectService.get(id)
      .then(setProject)
      .catch((caught) => setError(caught instanceof ApiError ? caught : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 })))
      .finally(() => setLoading(false));
  }, [id]);

  if (!id || Number.isNaN(id)) return <Navigate to={PROJECT_ROUTES.list} replace />;
  if (loading) {
    return <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" /></div>;
  }
  if (error || !project) {
    return <ErrorState error={error ?? new ApiError({ message: 'Project not found', code: 'NOT_FOUND', status: 404 })} onRetry={() => window.location.reload()} />;
  }

  const handleSubmit = async (request: ProjectRequest) => {
    await projectService.update(id, request);
    toast.success('Project updated.');
    navigate(PROJECT_ROUTES.detail(id), { replace: true });
  };

  return (
    <>
      <PageHeader title="Edit project" description={project.projectNumber} />
      <ProjectForm project={project} onSubmit={handleSubmit} onCancel={() => navigate(PROJECT_ROUTES.detail(id))} />
    </>
  );
}
