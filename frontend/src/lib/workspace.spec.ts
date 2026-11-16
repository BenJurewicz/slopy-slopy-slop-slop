import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, type Competition } from './api';
import { Workspace } from './workspace.svelte';

const competition: Competition = {
    id: 'competition',
    revision: 1,
    name: 'CloudMigration',
    description: 'Test competition',
    eligibilityRules: 'Test rules',
    currency: 'PLN',
    budget: '250000.00',
    maxGrant: '150000.00',
    threshold: 60,
    stage: 'OPEN',
    criteria: [],
};

beforeEach(() => {
    vi.spyOn(api, 'getCompetition').mockResolvedValue(competition);
    vi.spyOn(api, 'listApplications').mockResolvedValue([]);
    vi.spyOn(api, 'listEvaluations').mockResolvedValue([]);
    vi.spyOn(api, 'listDecisions').mockResolvedValue([]);
    vi.spyOn(api, 'listQueries').mockResolvedValue([]);
});

afterEach(() => vi.restoreAllMocks());

describe('workspace transitions', () => {
    it('resets transient views without clearing search and report parameters', () => {
        const workspace = new Workspace();
        workspace.editor = { kind: 'application', value: {} };
        workspace.fundingPreview = { available: '0', rows: [] };
        workspace.report = { total: 0, rows: [] };
        workspace.error = 'Old error';
        workspace.message = 'Old success';
        workspace.pageIndex = 3;
        workspace.searchText = 'Existing search';
        workspace.statusFilter = 'DRAFT';
        workspace.reportThreshold = '50000';

        workspace.navigate('reports');

        expect(workspace.view).toBe('reports');
        expect(workspace.editor).toBeNull();
        expect(workspace.fundingPreview).toBeNull();
        expect(workspace.report).toBeNull();
        expect(workspace.error).toBe('');
        expect(workspace.message).toBe('');
        expect(workspace.pageIndex).toBe(0);
        expect(workspace.searchText).toBe('Existing search');
        expect(workspace.statusFilter).toBe('DRAFT');
        expect(workspace.reportThreshold).toBe('50000');
    });

    it('does not display success when the refresh after a write fails', async () => {
        const workspace = new Workspace();
        vi.spyOn(api, 'saveDocument').mockResolvedValue(competition);
        vi.mocked(api.getCompetition).mockRejectedValue(
            new Error('Reload failed'),
        );

        await workspace.saveSettings({});

        expect(workspace.error).toBe('Reload failed');
        expect(workspace.message).toBe('');
        expect(workspace.busy).toBe(false);
        expect(workspace.loading).toBe(false);
        expect(workspace.competition).toBeNull();
    });

    it('does not refresh or display success after a failed action', async () => {
        const workspace = new Workspace();
        const refresh = vi.spyOn(workspace, 'refresh');

        await workspace.runAction(
            () => Promise.reject(new Error('Write failed')),
            'Saved',
        );

        expect(refresh).not.toHaveBeenCalled();
        expect(workspace.error).toBe('Write failed');
        expect(workspace.message).toBe('');
        expect(workspace.busy).toBe(false);
    });

    it('ignores a save when there is no open document editor', async () => {
        const workspace = new Workspace();
        const save = vi.spyOn(api, 'saveDocument');

        await workspace.saveDocument({});

        expect(save).not.toHaveBeenCalled();
        expect(workspace.busy).toBe(false);
    });
});
