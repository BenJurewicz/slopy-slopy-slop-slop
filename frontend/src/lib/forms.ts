import type { Kind, Competition, Application } from './api';
export type Data = Record<string, unknown>;
export type Field = { key: string; label: string; type?: 'text' | 'textarea' | 'number' | 'date' | 'email' | 'boolean' | 'select' | 'list' | 'object'; options?: string[]; children?: Field[]; readonly?: boolean; help?: string };
const field = (key: string, label: string, type: Field['type'] = 'text'): Field => ({ key, label, type });
const infrastructure = [field('name','System / usługa'),field('provider','Dostawca'),field('description','Opis','textarea')];
export const fields: Record<Kind, Field[]> = {
	competition: [field('name','Nazwa konkursu'),field('description','Opis programu','textarea'),field('budget','Budżet konkursu (PLN)'),field('maxGrant','Maksymalna dotacja (PLN)'),field('threshold','Próg kwalifikacji (punkty)','number'),{key:'stage',label:'Etap',type:'select',options:['OPEN','EVALUATION','CLOSED']},{key:'criteria',label:'Kryteria oceny',type:'list',children:[field('code','Kod'),field('name','Nazwa'),field('maximum','Maksymalna liczba punktów','number')]}],
	application: [
		{key:'company',label:'Wnioskodawca',type:'object',children:[field('name','Nazwa firmy'),field('legalId','NIP / identyfikator'),{key:'size',label:'Wielkość firmy',type:'select',options:['MICRO','SMALL','OTHER']},field('contact','Osoba kontaktowa'),field('email','E-mail','email')]},
		field('title','Tytuł projektu'),field('description','Opis projektu','textarea'),field('objectives','Cele','textarea'),field('benefits','Korzyści biznesowe','textarea'),
		{key:'currentInfrastructure',label:'Obecna infrastruktura',type:'list',children:infrastructure},{key:'targetInfrastructure',label:'Docelowa chmura',type:'list',children:infrastructure},
		{key:'tasks',label:'Zadania i harmonogram',type:'list',children:[field('name','Zadanie'),field('start','Data rozpoczęcia','date'),field('end','Data zakończenia','date'),field('milestone','Kamień milowy')]},
		{key:'budget',label:'Budżet projektu',type:'list',children:[field('description','Pozycja'),field('category','Kategoria'),field('quantity','Ilość'),field('unit','Jednostka'),field('unitCost','Koszt jednostkowy (PLN)'),{...field('total','Suma (PLN)'),readonly:true}]},
		field('requestedAmount','Wnioskowana dotacja (PLN)'),{key:'team',label:'Zespół',type:'list',children:[field('name','Imię i nazwisko'),field('role','Rola')]}
	],
	evaluation:[field('reviewer','Oceniający'),field('date','Data oceny','date'),field('eligible','Wniosek spełnia warunki','boolean'),field('reason','Uzasadnienie kwalifikacji','textarea'),{key:'scores',label:'Ocena kryteriów',type:'list',children:[{...field('code','Kryterium'),readonly:true},field('points','Punkty','number'),field('comment','Uzasadnienie','textarea')]},{...field('total','Suma punktów','number'),readonly:true}],
	decision:[field('date','Data decyzji','date'),{key:'outcome',label:'Wynik',type:'select',options:['AWARD','REJECT']},field('amount','Przyznana kwota (PLN)'),field('reason','Uzasadnienie','textarea')]
};
export const captions: Record<string,string> = { OPEN:'Nabór otwarty',EVALUATION:'Ocena',CLOSED:'Zamknięty',MICRO:'Mikroprzedsiębiorstwo',SMALL:'Małe przedsiębiorstwo',OTHER:'Inna wielkość',AWARD:'Przyznaj dotację',REJECT:'Odmów finansowania' };
export function empty(fields: Field[]): Data { return Object.fromEntries(fields.map(f=>[f.key,f.type==='object'?empty(f.children!):f.type==='list'?[empty(f.children!)]:f.type==='number'?0:f.type==='boolean'?false:f.type==='select'?f.options![0]:f.type==='date'?new Date().toISOString().slice(0,10):''])); }
export function newCompetition(): Data {
	return {...empty(fields.competition),revision:0,currency:'PLN',budget:'250000.00',maxGrant:'150000.00',threshold:60,stage:'OPEN',criteria:[{code:'technical',name:'Wykonalność techniczna',maximum:25},{code:'security',name:'Bezpieczeństwo',maximum:25},{code:'benefits',name:'Korzyści biznesowe',maximum:20},{code:'team',name:'Harmonogram i zespół',maximum:20},{code:'budget',name:'Uzasadnienie budżetu',maximum:10}]};
}
export function newApplication(competitionId: string): Data { return {...empty(fields.application),revision:0,competitionId,state:'DRAFT',submittedAt:null,requestedAmount:'0.00',budget:[{description:'',category:'Usługi',quantity:'1',unit:'projekt',unitCost:'0.00',total:'0.00'}]}; }
export function newEvaluation(a: Application,c: Competition): Data { return {revision:0,applicationId:a.id,state:'DRAFT',reviewer:'',date:new Date().toISOString().slice(0,10),eligible:a.company.size!=='OTHER',reason:'',scores:c.criteria.map(criterion=>({code:criterion.code,points:0,comment:''})),total:0}; }
