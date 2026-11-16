import { SvelteSet } from 'svelte/reactivity';

import {
    api,
    status,
    type Competition,
    type Application,
    type Evaluation,
    type Decision,
    type Kind,
    type Query,
    type Preview,
} from './api';
import { newEvaluation, type Data } from './forms';
import { navigation, type View } from './navigation';

export class Workspace {
    view = $state<View>('overview');
    loading = $state(true);
    busy = $state(false);
    error = $state('');
    message = $state('');

    competition = $state<Competition | null>(null);
    applications = $state<Application[]>([]);
    evaluations = $state<Evaluation[]>([]);
    decisions = $state<Decision[]>([]);
    queries = $state<Query[]>([]);

    searchText = $state('');
    statusFilter = $state('');
    pageIndex = $state(0);

    editor = $state<{
        kind: Kind;
        value: Data;
    } | null>(null);
    fundingPreview = $state<Preview | null>(null);
    financialSummary = $state<Record<string, unknown>>({});
    selectedQueryId = $state('summary');
    reportThreshold = $state('0');
    report = $state<{
        total: number;
        rows: Record<string, unknown>[];
    } | null>(null);
    private applicationIds = $derived(
        new SvelteSet(this.applications.map((application) => application.id)),
    );
    linkedEvaluations = $derived(
        this.evaluations.filter((evaluation) =>
            this.applicationIds.has(evaluation.applicationId),
        ),
    );
    linkedDecisions = $derived(
        this.decisions.filter((decision) =>
            this.applicationIds.has(decision.applicationId),
        ),
    );
    filteredApplications = $derived(
        this.applications.filter(
            (application) =>
                (!this.statusFilter ||
                    application.state === this.statusFilter) &&
                `${application.title} ${application.company.name}`
                    .toLocaleLowerCase('pl')
                    .includes(this.searchText.toLocaleLowerCase('pl')),
        ),
    );
    awardedDisplay = $derived(String(this.financialSummary.awarded ?? '0'));
    remainingDisplay = $derived(
        String(
            this.financialSummary.remaining ?? this.competition?.budget ?? '0',
        ),
    );
    selectedQuery = $derived(
        this.queries.find((query) => query.id === this.selectedQueryId),
    );
    selectedApplication = $derived(
        this.editor?.kind === 'application'
            ? this.applications.find(
                  (application) => application.id === this.editor?.value.id,
              )
            : undefined,
    );
    initialize = () => {
        const route = window.location.pathname.slice(1);
        if (navigation.some((item) => item.id === route))
            this.view = route as View;
        void this.refresh();
    };

    refresh = async () => {
        this.loading = true;
        this.error = '';
        try {
            const [competition, applications, evaluations, decisions, queries] =
                await Promise.all([
                    api.getCompetition(),
                    api.listApplications(),
                    api.listEvaluations(),
                    api.listDecisions(),
                    api.listQueries(),
                ]);
            this.competition = competition;
            this.applications = applications;
            this.evaluations = evaluations;
            this.decisions = decisions;
            this.queries = queries;
            this.financialSummary =
                (
                    await api.executeQuery('summary', {
                        threshold: '0',
                        offset: 0,
                        limit: 1,
                    })
                ).rows[0] ?? {};
        } catch (cause) {
            this.error = (cause as Error).message;
        } finally {
            this.loading = false;
        }
    };

    navigate = (next: View) => {
        this.view = next;
        this.editor = null;
        this.error = '';
        this.message = '';
        this.fundingPreview = null;
        this.report = null;
        this.pageIndex = 0;
    };

    runAction = async (action: () => Promise<unknown>, success: string) => {
        this.busy = true;
        this.error = '';
        this.message = '';
        try {
            await action();
            await this.refresh();
            this.message = success;
        } catch (cause) {
            this.error = (cause as Error).message;
        } finally {
            this.busy = false;
        }
    };

    loadDemo = async () => {
        if (
            !window.confirm(
                'Czy napewno chcesz usunąć obecne dane i wczytać dane demonstracyjne?',
            )
        )
            return;
        await this.runAction(
            () => api.loadDemo(),
            'Wczytano dane demonstracyjne.',
        );
    };

    resetData = async () => {
        if (
            !window.confirm(
                'Usunąć wszystkie wnioski, oceny i decyzje? Tej operacji nie można cofnąć. Ustawienia CloudMigration zostaną zachowane.',
            )
        )
            return;
        await this.runAction(
            () => api.resetData(),
            'Usunięto wszystkie wnioski, oceny i decyzje.',
        );
    };

    openEditor = (kind: Kind, value: object) => {
        this.editor = {
            kind,
            value: structuredClone($state.snapshot(value)) as Data,
        };
        this.message = '';
        this.error = '';
        window.scrollTo({ top: 0, behavior: 'smooth' });
    };

    saveDocument = async (value: Data) => {
        if (!this.editor) return;
        this.busy = true;
        this.message = '';
        try {
            await api.saveDocument(this.editor.kind, value);
            this.editor = null;
            await this.refresh();
            this.message = 'Dokument zapisany.';
        } finally {
            this.busy = false;
        }
    };

    saveSettings = async (value: Data) => {
        this.busy = true;
        this.message = '';
        try {
            await api.saveDocument('competition', value);
            await this.refresh();
            this.message = 'Ustawienia zapisane.';
        } finally {
            this.busy = false;
        }
    };

    remove = async (
        kind: Exclude<Kind, 'competition'>,
        value: {
            id: string;
            revision: number;
        },
    ) => {
        if (!window.confirm('Usunąć szkic? Tej operacji nie można cofnąć.'))
            return;
        await this.runAction(
            () => api.removeDocument(kind, value.id, value.revision),
            'Szkic usunięty.',
        );
    };

    submitApplication = async (application: Application) => {
        if (
            !window.confirm(
                'Złożyć wniosek? Po złożeniu nie będzie można zmienić danych.',
            )
        )
            return;
        await this.runAction(
            () => api.submitApplication(application.id, application.revision),
            'Wniosek złożony.',
        );
        this.editor = null;
    };

    finalizeEvaluation = async (evaluation: Evaluation) => {
        if (
            !window.confirm(
                'Zatwierdzić ocenę? Punktacja zostanie zablokowana.',
            )
        )
            return;
        await this.runAction(
            () => api.finalizeEvaluation(evaluation.id, evaluation.revision),
            'Ocena zatwierdzona.',
        );
    };

    startEvaluation = (application: Application) => {
        if (!this.competition) return;
        this.view = 'evaluations';
        this.openEditor(
            'evaluation',
            newEvaluation(application, this.competition),
        );
    };

    importApplication = async (event: Event) => {
        const input = event.currentTarget as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) return;
        this.busy = true;
        this.error = '';
        this.message = '';
        try {
            const result = await api.importApplication(file);
            const data = { ...result.application, id: undefined, revision: 0 };
            this.openEditor('application', data);
            this.message = result.diagnostics.join(' ');
        } catch (cause) {
            this.error = (cause as Error).message;
        } finally {
            this.busy = false;
            input.value = '';
        }
    };

    loadFundingPreview = async () => {
        this.busy = true;
        this.error = '';
        try {
            this.fundingPreview = await api.previewFunding();
        } catch (cause) {
            this.error = (cause as Error).message;
        } finally {
            this.busy = false;
        }
    };

    issueDecisions = async () => {
        const draft = this.linkedDecisions.filter(
            (decision) => decision.state === 'DRAFT',
        );
        if (!draft.length) return;
        if (
            !window.confirm(
                `Wydać ${draft.length} decyzji? Wydanych decyzji nie można edytować.`,
            )
        )
            return;
        await this.runAction(
            () =>
                api.issueDecisions(
                    draft.map((decision) => ({
                        id: decision.id,
                        revision: decision.revision,
                    })),
                ),
            'Decyzje wydane.',
        );
        this.fundingPreview = null;
    };

    runReport = async () => {
        this.busy = true;
        this.error = '';
        try {
            this.report = await api.executeQuery(this.selectedQueryId, {
                threshold:
                    this.selectedQueryId === 'over-budget'
                        ? this.reportThreshold
                        : '0',
                offset: 0,
                limit: 500,
            });
        } catch (cause) {
            this.error = (cause as Error).message;
        } finally {
            this.busy = false;
        }
    };

    applicationTitle = (id: string) => {
        return (
            this.applications.find((application) => application.id === id)
                ?.title ?? id
        );
    };

    applicationStatus = (application: Application) => {
        const issued = this.linkedDecisions.some(
            (decision) =>
                decision.applicationId === application.id &&
                decision.state === 'ISSUED',
        );
        if (issued) return 'Decyzja wydana';

        const evaluated = this.linkedEvaluations.some(
            (evaluation) =>
                evaluation.applicationId === application.id &&
                evaluation.state === 'FINAL',
        );
        if (evaluated) return 'Oceniony';
        return status(application.state);
    };
}
