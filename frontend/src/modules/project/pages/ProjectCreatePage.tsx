import { useNavigate } from 'react-router-dom';
import { PageHeader } from '@/shared/components/PageHeader';
import { useToast } from '@/modules/auth/hooks/useToast';
import { ProjectForm } from '../forms/ProjectForm';
import { projectService } from '../services/projectService';
import { PROJECT_ROUTES } from '../constants';
import type { ProjectRequest } from '../types';

export function ProjectCreatePage() {
  const navigate = useNavigate();
  const toast = useToast();

  const handleSubmit = async (request: ProjectRequest) => {
    const project = await projectService.create(request);
    toast.success(`Project ${project.projectNumber} created.`);
    navigate(PROJECT_ROUTES.detail(project.id), { replace: true });
  };

  return (
    <>
      <PageHeader title="New project" description="Set up the job - materials, expenses and payments are added once it's created." />
      <ProjectForm onSubmit={handleSubmit} onCancel={() => navigate(PROJECT_ROUTES.list)} />
    </>
  );
}
