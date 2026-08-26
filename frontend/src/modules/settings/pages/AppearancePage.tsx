import { PageHeader } from '@/shared/components/PageHeader';
import { AppearanceSettings } from '../components/AppearanceSettings';

/** CR-034: the dedicated "Theme & Appearance" page - also embedded as a Profile tab; see AppearanceSettings.tsx for why it's one implementation, two entry points. */
export function AppearancePage() {
  return (
    <>
      <PageHeader
        title="Theme & Appearance"
        description="Personalise how the app looks. Applies immediately and remembers your choice on this device."
      />
      <div className="max-w-4xl">
        <AppearanceSettings />
      </div>
    </>
  );
}
