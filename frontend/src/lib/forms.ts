import type { Kind, Competition, Application } from './api';
export type Data = Record<string, unknown>;
export type Field = {
    key: string;
    label: string;
    type?:
        | 'text'
        | 'textarea'
        | 'number'
        | 'date'
        | 'email'
        | 'boolean'
        | 'select'
        | 'list'
        | 'object';
    options?: string[];
    children?: Field[];
    readonly?: boolean;
    optional?: boolean;
    help?: string;
};
const field = (
    key: string,
    label: string,
    type: Field['type'] = 'text',
): Field => ({ key, label, type });
const infrastructure = [
    field('name', 'System / usługa'),
    field('provider', 'Dostawca'),
    field('description', 'Opis', 'textarea'),
];
export const fields: Record<Kind, Field[]> = {
    competition: [
        field('description', 'Opis programu', 'textarea'),
        field('budget', 'Budżet konkursu (PLN)'),
        field('maxGrant', 'Maksymalna dotacja (PLN)'),
        field('threshold', 'Próg kwalifikacji (punkty)', 'number'),
        {
            key: 'stage',
            label: 'Etap',
            type: 'select',
            options: ['OPEN', 'EVALUATION', 'CLOSED'],
        },
        {
            key: 'criteria',
            label: 'Kryteria oceny',
            type: 'list',
            children: [
                field('code', 'Kod'),
                field('name', 'Nazwa'),
                field('maximum', 'Maksymalna liczba punktów', 'number'),
            ],
        },
    ],
    application: [
        {
            key: 'company',
            label: 'Wnioskodawca',
            type: 'object',
            children: [
                field('name', 'Nazwa firmy'),
                field('address', 'Adres beneficjenta'),
                field('legalId', 'NIP'),
                field('regon', 'REGON'),
                field('representative', 'Reprezentowany przez'),
                {
                    key: 'size',
                    label: 'Wielkość firmy',
                    type: 'select',
                    options: ['MICRO', 'SMALL', 'OTHER'],
                },
                field('contact', 'Osoba kontaktowa'),
                field('email', 'E-mail', 'email'),
            ],
        },
        { ...field('number', 'Numer wniosku'), readonly: true },
        { ...field('projectNumber', 'Numer projektu'), optional: true },
        field('implementationStart', 'Początek realizacji projektu', 'date'),
        field('implementationEnd', 'Koniec realizacji projektu', 'date'),
        field('title', 'Tytuł projektu'),
        field('description', 'Opis projektu', 'textarea'),
        field('objectives', 'Cele', 'textarea'),
        field('benefits', 'Korzyści biznesowe', 'textarea'),
        {
            key: 'currentInfrastructure',
            label: 'Obecna infrastruktura',
            type: 'list',
            children: infrastructure,
        },
        {
            key: 'targetInfrastructure',
            label: 'Docelowa chmura',
            type: 'list',
            children: infrastructure,
        },
        {
            key: 'tasks',
            label: 'Zadania i harmonogram',
            type: 'list',
            children: [
                field('name', 'Zadanie'),
                field('start', 'Data rozpoczęcia', 'date'),
                field('end', 'Data zakończenia', 'date'),
                field('milestone', 'Kamień milowy'),
            ],
        },
        {
            key: 'budget',
            label: 'Budżet projektu',
            type: 'list',
            children: [
                field('description', 'Pozycja'),
                field('category', 'Kategoria'),
                field('quantity', 'Ilość'),
                field('unit', 'Jednostka'),
                field('unitCost', 'Koszt jednostkowy (PLN)'),
                { ...field('total', 'Suma (PLN)'), readonly: true },
            ],
        },
        {
            ...field('projectCost', 'Całkowity koszt projektu (PLN)'),
            readonly: true,
        },
        field('requestedAmount', 'Wnioskowana dotacja (PLN)'),
        {
            ...field('ownContribution', 'Wkład własny Beneficjenta (PLN)'),
            readonly: true,
        },
        {
            key: 'team',
            label: 'Zespół',
            type: 'list',
            children: [field('name', 'Imię i nazwisko'), field('role', 'Rola')],
        },
    ],
    evaluation: [
        { ...field('number', 'Numer oceny'), readonly: true },
        field('reviewer', 'Oceniający'),
        field('date', 'Data oceny', 'date'),
        field('eligible', 'Wniosek spełnia warunki', 'boolean'),
        field('reason', 'Uzasadnienie kwalifikacji', 'textarea'),
        {
            key: 'scores',
            label: 'Ocena kryteriów',
            type: 'list',
            children: [
                { ...field('code', 'Kryterium'), readonly: true },
                field('points', 'Punkty', 'number'),
                field('comment', 'Uzasadnienie', 'textarea'),
            ],
        },
        { ...field('total', 'Suma punktów', 'number'), readonly: true },
    ],
    decision: [
        { ...field('number', 'Numer decyzji'), readonly: true },
        {
            ...field(
                'budgetAllows',
                'Budżet pozwala na pełne dofinansowanie',
                'boolean',
            ),
            readonly: true,
        },
        field('date', 'Data decyzji', 'date'),
        {
            key: 'outcome',
            label: 'Wynik',
            type: 'select',
            options: ['AWARD', 'REJECT'],
        },
        field('amount', 'Przyznana kwota (PLN)'),
        field('reason', 'Uzasadnienie', 'textarea'),
    ],
};
export const captions: Record<string, string> = {
    OPEN: 'Nabór otwarty',
    EVALUATION: 'Ocena',
    CLOSED: 'Zamknięty',
    MICRO: 'Mikroprzedsiębiorstwo',
    SMALL: 'Małe przedsiębiorstwo',
    OTHER: 'Inna wielkość',
    AWARD: 'Przyznaj dotację',
    REJECT: 'Odmów finansowania',
};
export function empty(fields: Field[]): Data {
    return Object.fromEntries(
        fields.map((f) => [
            f.key,
            f.type === 'object'
                ? empty(f.children!)
                : f.type === 'list'
                  ? [empty(f.children!)]
                  : f.type === 'number'
                    ? 0
                    : f.type === 'boolean'
                      ? false
                      : f.type === 'select'
                        ? f.options![0]
                        : f.type === 'date'
                          ? new Date().toISOString().slice(0, 10)
                          : '',
        ]),
    );
}
export function newApplication(): Data {
    return {
        ...empty(fields.application),
        revision: 0,
        state: 'DRAFT',
        submittedAt: null,
        requestedAmount: '0.00',
        projectCost: '0.00',
        ownContribution: '0.00',
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
    };
}
export function newEvaluation(a: Application, c: Competition): Data {
    return {
        revision: 0,
        applicationId: a.id,
        state: 'DRAFT',
        reviewer: '',
        date: new Date().toISOString().slice(0, 10),
        eligible: a.company.size !== 'OTHER',
        reason: '',
        scores: c.criteria.map((criterion) => ({
            code: criterion.code,
            points: 0,
            comment: '',
        })),
        total: 0,
    };
}
