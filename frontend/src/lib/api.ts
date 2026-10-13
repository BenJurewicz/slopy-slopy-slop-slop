import * as sdk from './generated/api/sdk.gen';
import type * as Models from './generated/api/types.gen';

// Existing view models expect complete documents returned by the backend.
// Request bodies and endpoint signatures remain defined by the generated SDK.
type Complete<T> = T extends (infer U)[] ? Complete<U>[] : T extends object ? { [K in keyof T]-?: Complete<NonNullable<T[K]>> } : T;
export type Competition = Complete<Models.Competition>;
export type Application = Complete<Models.Application>;
export type Evaluation = Complete<Models.Evaluation>;
export type Decision = Complete<Models.Decision>;
export type Query = Complete<Models.Query>;
export type Preview = Complete<Models.Preview>;
export type Kind = 'competition' | 'application' | 'evaluation' | 'decision';
export class ApiError extends Error { constructor(message: string, public status: number) { super(message); } }

async function unwrap<T>(pending: Promise<{ data?: T; error?: unknown; response?: Response }>): Promise<T> {
	const { data, error, response } = await pending;
	if (!response) throw new ApiError("Nie można połączyć się z serwerem. Spróbuj ponownie.", 0);
	if (!response.ok) {
		const message = error && typeof error === 'object' && 'message' in error && typeof error.message === 'string'
			? error.message : `Nie można wykonać operacji (HTTP ${response.status}).`;
		throw new ApiError(message, response.status);
	}
	return (response.status === 204 ? undefined : data) as T;
}
const complete = <T>(value: T) => value as Complete<T>;

export const api = {
	listCompetitions: async () => complete(await unwrap(sdk.listCompetitions())),
	listApplications: async () => complete(await unwrap(sdk.listApplications())),
	listEvaluations: async () => complete(await unwrap(sdk.listEvaluations())),
	listDecisions: async () => complete(await unwrap(sdk.listDecisions())),
	listQueries: async () => complete(await unwrap(sdk.listQueries())),
	loadDemo: () => unwrap(sdk.loadDemo()),
	submitApplication: (id: string, revision: number) => unwrap(sdk.submit({ path: { id }, body: { revision } })),
	finalizeEvaluation: (id: string, revision: number) => unwrap(sdk.finalizeDocument({ path: { id }, body: { revision } })),
	importApplication: async (file: File) => complete(await unwrap(sdk.importApplication({ body: { file } }))),
	calculateBudget: (body: Models.CalculateBudgetData['body']) => unwrap(sdk.calculateBudget({ body })),
	previewFunding: async (competitionId: string) => complete(await unwrap(sdk.previewFunding({ path: { competitionId } }))),
	prepareDecisions: (competitionId: string) => unwrap(sdk.prepareDecisions({ path: { competitionId } })),
	issueDecisions: (body: Models.IssueDecisionsData['body']) => unwrap(sdk.issueDecisions({ body })),
	executeQuery: async (id: string, body: Models.ExecuteQueryData['body']) => complete(await unwrap(sdk.executeQuery({ path: { id }, body }))),
	async saveDocument(kind: Kind, value: Record<string, unknown>) {
		// The schema-driven editor is dynamic; this is its sole transport boundary.
		const id = typeof value.id === 'string' ? value.id : undefined;
		switch (kind) {
			case 'competition': return unwrap(id ? sdk.updateCompetition({ path: { id }, body: value as Models.Competition }) : sdk.createCompetition({ body: value as Models.Competition }));
			case 'application': return unwrap(id ? sdk.updateApplication({ path: { id }, body: value as Models.Application }) : sdk.createApplication({ body: value as Models.Application }));
			case 'evaluation': return unwrap(id ? sdk.updateEvaluation({ path: { id }, body: value as Models.Evaluation }) : sdk.createEvaluation({ body: value as Models.Evaluation }));
			case 'decision': return unwrap(id ? sdk.updateDecision({ path: { id }, body: value as Models.Decision }) : sdk.createDecision({ body: value as Models.Decision }));
		}
	},
	removeDocument(kind: Kind, id: string, revision: number) {
		const options = { path: { id }, query: { revision } };
		switch (kind) {
			case 'competition': return unwrap(sdk.deleteCompetition(options));
			case 'application': return unwrap(sdk.deleteApplication(options));
			case 'evaluation': return unwrap(sdk.deleteEvaluation(options));
			case 'decision': return unwrap(sdk.deleteDecision(options));
		}
	}
};
export function money(value: string) { return new Intl.NumberFormat('pl-PL', { style: 'currency', currency: 'PLN' }).format(Number(value)); }
export function status(value: string) { return ({ DRAFT: 'Szkic', SUBMITTED: 'Złożony', FINAL: 'Ocena końcowa', ISSUED: 'Wydana', OPEN: 'Nabór otwarty', EVALUATION: 'Ocena', CLOSED: 'Zamknięty', AWARD: 'Przyznano', REJECT: 'Odmowa' } as Record<string,string>)[value] ?? value; }
