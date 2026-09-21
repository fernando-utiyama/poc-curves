import axios, { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios';
import * as https from 'https';

function sanitizeUrl(rawUrl: string): string {
    try {
        const parsed = new URL(rawUrl);
        return `${parsed.protocol}//${parsed.host}${parsed.pathname}`;
    } catch {
        return rawUrl;
    }
}

function getHttpsAgent(): https.Agent {
    return new https.Agent({
        rejectUnauthorized: true,
        timeout: 35000,
    });
}

export interface HttpClientOptions {
    headers?: Record<string, string>;
    data?: unknown;
    timeout?: number;
    responseType?: 'json' | 'text' | 'arraybuffer' | 'stream';
}

/**
 * Wrapper HTTP com logging automático de método, URL, status e tempo de resposta.
 *
 * Log de sucesso : [tag] METHOD url -> STATUS StatusText (Xms)
 * Log de erro    : [tag] METHOD url -> STATUS StatusText (Xms) | Erro: detalhe
 */
export async function httpClient<T = unknown>(
    method: 'GET' | 'POST',
    url: string,
    options: HttpClientOptions,
    tag: string
): Promise<AxiosResponse<T>> {
    const start = Date.now();
    const safeUrl = sanitizeUrl(url);

    const config: AxiosRequestConfig = {
        method,
        url,
        headers: options.headers,
        data: options.data,
        timeout: options.timeout ?? 30000,
        httpsAgent: getHttpsAgent(),
    };

    if (options.responseType) {
        config.responseType = options.responseType;
    }

    try {
        const response = await axios.request<T>(config);
        const elapsed = Date.now() - start;
        console.log(`[${tag}] ${method} ${safeUrl} -> ${response.status} ${response.statusText} (${elapsed}ms)`);
        return response;
    } catch (error: unknown) {
        const elapsed = Date.now() - start;
        if (error instanceof AxiosError) {
            const status = error.response?.status ?? 'N/A';
            const statusText = error.response?.statusText ?? '';
            console.error(
                `[${tag}] ${method} ${safeUrl} -> ${status} ${statusText} (${elapsed}ms) | Erro na chamada HTTP.`
            );
        } else {
            console.error(`[${tag}] ${method} ${safeUrl} -> ERRO (${elapsed}ms): Falha na chamada HTTP.`);
        }
        throw error;
    }
}
