import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
import { client } from './generated/api/client.gen';

const fetcher = vi.fn<typeof fetch>();
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), {
	status, headers: { 'Content-Type': 'application/json' }
});
const sent = () => fetcher.mock.calls[0][0] as Request;

beforeEach(() => {
	fetcher.mockReset();
	client.setConfig({ baseUrl: 'http://cloudmigration.test', fetch: fetcher });
});

describe('generated API integration', () => {
	it('lists documents through the generated endpoint', async () => {
		fetcher.mockResolvedValue(json([{ id: 'competition-1', name: 'Konkurs' }]));
		const result = await api.listCompetitions();
		expect(sent().url).toBe('http://cloudmigration.test/api/competitions');
		expect(result[0].id).toBe('competition-1');
	});
	it('creates and updates using generated request bodies', async () => {
		fetcher.mockResolvedValue(json({ id: 'application-1', revision: 1 }, 201));
		await api.saveDocument('application', { title: 'Migracja' });
		expect(sent().method).toBe('POST');
		expect(await sent().json()).toEqual({ title: 'Migracja' });
		fetcher.mockClear();
		fetcher.mockResolvedValue(json({ id: 'application-1', revision: 2 }));
		await api.saveDocument('application', { id: 'application-1', revision: 1 });
		expect(sent().method).toBe('PUT');
		expect(sent().url).toContain('/api/applications/application-1');
	});
	it('sends revision in submission requests', async () => {
		fetcher.mockResolvedValue(json({ id: 'application-1', state: 'SUBMITTED' }));
		await api.submitApplication('application-1', 3);
		expect(sent().url).toContain('/api/applications/application-1/submit');
		expect(await sent().json()).toEqual({ revision: 3 });
	});
	it('serializes DOCX uploads as multipart form data', async () => {
		fetcher.mockResolvedValue(json({ application: { id: 'preview-1' }, diagnostics: [] }));
		await api.importApplication(new File(['docx'], 'wniosek.docx'), 'competition-1');
		expect(sent().url).toContain('/api/documents/import?competitionId=competition-1');
		expect(sent().headers.get('Content-Type')).toMatch(/^multipart\/form-data; boundary=/);
		const form = await sent().formData();
		expect((form.get('file') as File).name).toBe('wniosek.docx');
	});
	it('handles deletion with no response body', async () => {
		fetcher.mockResolvedValue(new Response(null, { status: 204 }));
		await expect(api.removeDocument('decision', 'decision-1', 2)).resolves.toBeUndefined();
		expect(sent().method).toBe('DELETE');
		expect(sent().url).toContain('/api/decisions/decision-1?revision=2');
	});
	it('preserves backend conflict messages', async () => {
		fetcher.mockResolvedValue(json({ message: 'Dokument został zmieniony.' }, 409));
		await expect(api.submitApplication('application-1', 1)).rejects.toMatchObject({
			message: 'Dokument został zmieniony.', status: 409
		});
	});
	it('handles non-JSON proxy errors', async () => {
		fetcher.mockResolvedValue(new Response('Unavailable', { status: 503 }));
		await expect(api.listApplications()).rejects.toMatchObject({ status: 503 });
	});
	it('handles connection failures without an HTTP response', async () => {
		fetcher.mockRejectedValue(new TypeError('fetch failed'));
		await expect(api.listApplications()).rejects.toBeInstanceOf(ApiError);
	});
});
