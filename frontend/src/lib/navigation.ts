export type View =
    | 'overview'
    | 'settings'
    | 'applications'
    | 'evaluations'
    | 'funding'
    | 'decisions'
    | 'reports';
export const navigation: { id: View; label: string }[] = [
    { id: 'overview', label: 'Przegląd' },
    { id: 'applications', label: 'Wnioski' },
    { id: 'evaluations', label: 'Oceny' },
    { id: 'funding', label: 'Ranking i dotacje' },
    { id: 'decisions', label: 'Decyzje' },
    { id: 'reports', label: 'Raporty XQuery' },
    { id: 'settings', label: 'Ustawienia' },
];
