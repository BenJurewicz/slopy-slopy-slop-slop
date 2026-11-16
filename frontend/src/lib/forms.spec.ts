import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Application, Competition } from './api';
import {
    initialValues,
    newApplication,
    newEvaluation,
    type Data,
    type Field,
} from './forms';

beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-02-03T12:00:00.000Z'));
});

afterEach(() => vi.useRealTimers());

const scalarDefaults: [Field['type'], unknown][] = [
    [undefined, ''],
    ['text', ''],
    ['textarea', ''],
    ['email', ''],
    ['number', 0],
    ['boolean', false],
    ['select', 'first'],
    ['date', '2026-02-03'],
];

describe('initial form values', () => {
    it.each(scalarDefaults)('preserves the %s default', (type, expected) => {
        const values = initialValues([
            {
                key: 'value',
                label: 'Value',
                type,
                options: ['first', 'second'],
            },
        ]);

        expect(values.value).toEqual(expected);
    });

    it('creates independent nested objects and a first row for each list', () => {
        const children: Field[] = [{ key: 'name', label: 'Name' }];
        const definition: Field[] = [
            { key: 'object', label: 'Object', type: 'object', children },
            { key: 'rows', label: 'Rows', type: 'list', children },
        ];
        const first = initialValues(definition);
        const second = initialValues(definition);

        expect(first).toEqual({ object: { name: '' }, rows: [{ name: '' }] });
        (first.object as Data).name = 'Changed';
        (first.rows as Data[])[0].name = 'Changed';
        expect(second).toEqual({ object: { name: '' }, rows: [{ name: '' }] });
    });
});

describe('new document drafts', () => {
    it('keeps application amounts as strings and the initial service budget row', () => {
        const application = newApplication();

        expect(application).toMatchObject({
            revision: 0,
            state: 'DRAFT',
            submittedAt: null,
            implementationStart: '2026-02-03',
            implementationEnd: '2026-02-03',
            requestedAmount: '0.00',
            projectCost: '0.00',
            ownContribution: '0.00',
            company: { size: 'MICRO' },
            budget: [
                {
                    description: '',
                    category: 'Usługi',
                    quantity: '1',
                    unit: 'projekt',
                    unitCost: '0.00',
                    total: '0.00',
                },
            ],
        });
    });

    it.each(['MICRO', 'SMALL', 'OTHER'] as const)(
        'preserves the initial eligibility and criterion order for %s companies',
        (size) => {
            const application = {
                id: 'application',
                company: { size },
            } as Application;
            const competition = {
                criteria: [
                    { code: 'B', name: 'Second', maximum: 40 },
                    { code: 'A', name: 'First', maximum: 60 },
                ],
            } as Competition;

            expect(newEvaluation(application, competition)).toEqual({
                revision: 0,
                applicationId: 'application',
                state: 'DRAFT',
                reviewer: '',
                date: '2026-02-03',
                eligible: size !== 'OTHER',
                reason: '',
                scores: [
                    { code: 'B', points: 0, comment: '' },
                    { code: 'A', points: 0, comment: '' },
                ],
                total: 0,
            });
        },
    );
});
