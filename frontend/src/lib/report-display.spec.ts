import { describe, expect, it } from 'vitest';
import { money } from './api';
import { formatReportCell } from './report-display';

describe('report cell presentation', () => {
    it.each([
        'awarded',
        'remaining',
        'remainingAfter',
        'amount',
        'requestedAmount',
    ])('formats %s as server-supplied money', (column) =>
        expect(formatReportCell(column, '1234.50')).toBe(money('1234.50')),
    );

    it('keeps monetary-column precedence over boolean formatting', () => {
        expect(formatReportCell('amount', true)).toBe(money('true'));
    });

    it('translates states and outcomes but keeps unknown values', () => {
        expect(formatReportCell('state', 'DRAFT')).toBe('Szkic');
        expect(formatReportCell('outcome', 'REJECT')).toBe('Odmowa');
        expect(formatReportCell('state', 'OTHER')).toBe('OTHER');
    });

    it('renders booleans and arbitrary values as before', () => {
        expect(formatReportCell('qualified', true)).toBe('Tak');
        expect(formatReportCell('qualified', false)).toBe('Nie');
        expect(formatReportCell('score', 80)).toBe('80');
        expect(formatReportCell('title', null)).toBe('null');
        expect(formatReportCell('title', undefined)).toBe('undefined');
    });
});
