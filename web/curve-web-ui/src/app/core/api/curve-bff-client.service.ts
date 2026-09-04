import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RuntimeConfigService } from '../config/runtime-config.service.ts';
import {
  PainelDoDiaResponse,
  AlertasSumarioResponse,
  CatalogoResponse,
  DefinicaoCurvaDTO,
  CurvaViewerResponse,
  InterpolacaoRequest,
  InterpolacaoResponse,
  CargaManualResponse,
  ComparacaoResponse,
  DisparoIngestaoRequest,
  DisparoIngestaoResponse,
  BackfillRequest,
  BackfillStatusResponse,
  ExecucoesResponse,
  PendenciasDlqGruposResponse,
  PendenciasDlqDetalheResponse,
  ModelosResponse,
  ModeloDTO
} from './models.ts';

@Injectable({
  providedIn: 'root'
})
export class CurveBffClientService {
  constructor(
    private http: HttpClient,
    private configService: RuntimeConfigService
  ) {}

  private get baseUrl(): string {
    return this.configService.get('bffBaseUrl') || '/api/v1';
  }

  public getPainelDoDia(dataReferencia?: string): Observable<PainelDoDiaResponse> {
    let params = new HttpParams();
    if (dataReferencia) {
      params = params.set('dataReferencia', dataReferencia);
    }
    return this.http.get<PainelDoDiaResponse>(`${this.baseUrl}/painel-do-dia`, { params });
  }

  public getAlertasSumario(): Observable<AlertasSumarioResponse> {
    return this.http.get<AlertasSumarioResponse>(`${this.baseUrl}/alertas/sumario`);
  }

  public getCatalogo(filtros?: {
    codigo?: string;
    modoOrigem?: string;
    estado?: string;
    pagina?: number;
    tamanho?: number;
  }): Observable<CatalogoResponse> {
    let params = new HttpParams();
    if (filtros?.codigo) params = params.set('codigo', filtros.codigo);
    if (filtros?.modoOrigem) params = params.set('modoOrigem', filtros.modoOrigem);
    if (filtros?.estado) params = params.set('estado', filtros.estado);
    if (filtros?.pagina !== undefined) params = params.set('pagina', filtros.pagina.toString());
    if (filtros?.tamanho !== undefined) params = params.set('tamanho', filtros.tamanho.toString());

    return this.http.get<CatalogoResponse>(`${this.baseUrl}/catalogo`, { params });
  }

  public getDefinicaoCurva(codigo: string): Observable<DefinicaoCurvaDTO> {
    return this.http.get<DefinicaoCurvaDTO>(`${this.baseUrl}/curvas/${encodeURIComponent(codigo)}/definicao`);
  }

  public criarDefinicaoCurva(codigo: string, req: unknown): Observable<DefinicaoCurvaDTO> {
    return this.http.post<DefinicaoCurvaDTO>(`${this.baseUrl}/curvas/${encodeURIComponent(codigo)}/definicao`, req);
  }

  public atualizarDefinicaoCurva(codigo: string, req: unknown): Observable<DefinicaoCurvaDTO> {
    return this.http.put<DefinicaoCurvaDTO>(`${this.baseUrl}/curvas/${encodeURIComponent(codigo)}/definicao`, req);
  }

  public downloadModeloCargaUrl(codigo: string, formato: 'CSV' | 'XLSX'): string {
    return `${this.baseUrl}/curvas/${encodeURIComponent(codigo)}/modelo-carga?formato=${formato}`;
  }

  public getCurvaViewer(
    codigo: string,
    dataReferencia: string,
    momento?: string,
    versao?: number,
    asOf?: string
  ): Observable<CurvaViewerResponse> {
    let params = new HttpParams().set('dataReferencia', dataReferencia);
    if (momento) params = params.set('momento', momento);
    if (versao !== undefined) params = params.set('versao', versao.toString());
    if (asOf) params = params.set('asOf', asOf);

    return this.http.get<CurvaViewerResponse>(`${this.baseUrl}/curvas/${encodeURIComponent(codigo)}/viewer`, { params });
  }

  public interpolarCurva(codigo: string, req: InterpolacaoRequest): Observable<InterpolacaoResponse> {
    return this.http.post<InterpolacaoResponse>(`${this.baseUrl}/curvas/${encodeURIComponent(codigo)}/interpolacao`, req);
  }

  public submeterCargaManual(codigo: string, formData: FormData): Observable<CargaManualResponse> {
    return this.http.post<CargaManualResponse>(`${this.baseUrl}/curvas/${encodeURIComponent(codigo)}/carga-manual`, formData);
  }

  public compararCurvas(req: unknown): Observable<ComparacaoResponse> {
    return this.http.post<ComparacaoResponse>(`${this.baseUrl}/comparacao`, req);
  }

  public dispararIngestao(req: DisparoIngestaoRequest): Observable<DisparoIngestaoResponse> {
    return this.http.post<DisparoIngestaoResponse>(`${this.baseUrl}/ingestao/disparo`, req);
  }

  public iniciarBackfill(req: BackfillRequest): Observable<BackfillStatusResponse> {
    return this.http.post<BackfillStatusResponse>(`${this.baseUrl}/ingestao/backfill`, req);
  }

  public getBackfillStatus(id: string): Observable<BackfillStatusResponse> {
    const params = new HttpParams().set('id', id);
    return this.http.get<BackfillStatusResponse>(`${this.baseUrl}/ingestao/backfill`, { params });
  }

  public interromperBackfill(id: string): Observable<BackfillStatusResponse> {
    return this.http.post<BackfillStatusResponse>(`${this.baseUrl}/ingestao/backfill/${encodeURIComponent(id)}/interromper`, {});
  }

  public getExecucoes(filtros?: {
    curva?: string;
    dataReferencia?: string;
    estado?: string;
    correlationId?: string;
    pagina?: number;
    tamanho?: number;
  }): Observable<ExecucoesResponse> {
    let params = new HttpParams();
    if (filtros?.curva) params = params.set('curva', filtros.curva);
    if (filtros?.dataReferencia) params = params.set('dataReferencia', filtros.dataReferencia);
    if (filtros?.estado) params = params.set('estado', filtros.estado);
    if (filtros?.correlationId) params = params.set('correlationId', filtros.correlationId);
    if (filtros?.pagina !== undefined) params = params.set('pagina', filtros.pagina.toString());
    if (filtros?.tamanho !== undefined) params = params.set('tamanho', filtros.tamanho.toString());

    return this.http.get<ExecucoesResponse>(`${this.baseUrl}/execucoes`, { params });
  }

  public redispararExecucao(id: string): Observable<DisparoIngestaoResponse> {
    return this.http.post<DisparoIngestaoResponse>(`${this.baseUrl}/execucoes/${encodeURIComponent(id)}/redisparar`, {});
  }

  public getPendenciasDlqGrupos(): Observable<PendenciasDlqGruposResponse> {
    return this.http.get<PendenciasDlqGruposResponse>(`${this.baseUrl}/pendencias-dlq`);
  }

  public getPendenciasDlqDetalhes(grupoId: string): Observable<PendenciasDlqDetalheResponse> {
    return this.http.get<PendenciasDlqDetalheResponse>(`${this.baseUrl}/pendencias-dlq/grupos/${encodeURIComponent(grupoId)}`);
  }

  public reprocessarPendenciasDlq(req: { grupoId?: string; pendenciaId?: string }): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/pendencias-dlq/reprocessar`, req);
  }

  public descartarPendenciasDlq(req: { justificativa: string; grupoId?: string; pendenciaId?: string }): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/pendencias-dlq/descartar`, req);
  }

  public getModelos(): Observable<ModelosResponse> {
    return this.http.get<ModelosResponse>(`${this.baseUrl}/modelos`);
  }

  public importarModeloGroovy(req: { nome: string; scriptConteudo: string; descricao: string }): Observable<ModeloDTO> {
    return this.http.post<ModeloDTO>(`${this.baseUrl}/modelos`, req);
  }

  public trocarModeloCurva(codigo: string, req: { modeloId: string }): Observable<DefinicaoCurvaDTO> {
    return this.http.post<DefinicaoCurvaDTO>(`${this.baseUrl}/curvas/${encodeURIComponent(codigo)}/trocar-modelo`, req);
  }
}
