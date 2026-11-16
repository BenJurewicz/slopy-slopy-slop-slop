import { money, status } from './api';

export const reportLabels: Record<string, string> = {
    applications: 'Wnioski',
    evaluations: 'Oceny',
    averageScore: 'Średnia punktów',
    awarded: 'Przyznane środki',
    remaining: 'Pozostałe środki',
    applicationId: 'Wniosek',
    evaluationId: 'Ocena',
    title: 'Projekt',
    company: 'Firma',
    outcome: 'Wynik',
    amount: 'Kwota',
    requestedAmount: 'Wnioskowana kwota',
    budgetAllows: 'Budżet pozwala na dotację',
    remainingAfter: 'Pozostały budżet',
    qualified: 'Zakwalifikowany',
    score: 'Punkty',
    provider: 'Dostawca chmury',
    projects: 'Projekty',
    state: 'Status',
    id: 'Identyfikator',
};

const moneyColumns = new Set([
    'awarded',
    'remaining',
    'remainingAfter',
    'amount',
    'requestedAmount',
]);
const statusColumns = new Set(['state', 'outcome']);

export function formatReportCell(column: string, value: unknown): string {
    if (moneyColumns.has(column)) return money(String(value));
    if (statusColumns.has(column)) return status(String(value));
    if (typeof value === 'boolean') return value ? 'Tak' : 'Nie';
    return String(value);
}
