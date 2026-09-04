import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { AuthService } from '../../core/auth/auth.service.ts';
import { CargaManualResponse, ErroLinhaCargaDTO } from '../../core/api/models.ts';

@Component({
  selector: 'app-carga-manual',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Carga Manual de Curva (Contingência)</h1>
          <p class="page-subtitle">Carregue arquivos CSV ou Planilhas com vértices digitados para publicação emergencial com validação completa.</p>
        </div>
      </div>

      <div class="card-carga">
        <h2 class="secao-titulo">1. Parâmetros e Arquivo de Contingência</h2>
        <div class="grid-form-3">
          <div class="campo">
            <label>Curva Alvo *</label>
            <input type="text" [(ngModel)]="codigoCurva" class="form-input" placeholder="Ex: PRE, DOL" />
          </div>
          <div class="campo">
            <label>Data de Referência *</label>
            <input type="date" [(ngModel)]="dataReferencia" class="form-input" />
          </div>
          <div class="campo">
            <label>Momento *</label>
            <select [(ngModel)]="momento" class="form-select">
              <option value="FECHAMENTO">Fechamento</option>
              <option value="INTRADIARIO">Intradiário</option>
              <option value="ABERTURA">Abertura</option>
            </select>
          </div>
        </div>

        <!-- Download de Modelos -->
        <div class="bloco-templates">
          <span class="rotulo-templates">Templates com vértices e cabeçalho oficiais da curva:</span>
          <div class="botoes-template">
            <a [href]="obterUrlModelo('CSV')" download class="btn btn-secondary btn-sm">📄 Baixar Modelo CSV</a>
            <a [href]="obterUrlModelo('XLSX')" download class="btn btn-secondary btn-sm">📊 Baixar Modelo Excel (XLSX)</a>
          </div>
        </div>

        <div class="campo" style="margin-top: 16px;">
          <label>Selecione o Arquivo Preenchido (.csv ou .xlsx) *</label>
          <input type="file" (change)="onArquivoSelecionado($event)" accept=".csv,.xlsx" class="form-file" />
        </div>

        <div class="campo" style="margin-top: 16px;">
          <label>Justificativa Operacional Obrigatória *</label>
          <textarea
            [(ngModel)]="justificativa"
            class="form-textarea"
            rows="3"
            placeholder="Descreva detalhadamente o motivo da carga manual (Ex: Atraso na divulgação do boletim B3 pré-fechamento de risco)."></textarea>
          @if (!justificativa.trim()) {
            <small class="aviso-justificativa">⚠️ A justificativa é obrigatória para habilitar a submissão.</small>
          }
        </div>

        <div class="card-footer">
          <button
            class="btn btn-primary"
            [disabled]="submetendo() || !arquivo || !justificativa.trim() || !authService.isOperator()"
            [title]="authService.isOperator() ? 'Submeter arquivo para validação e publicação' : authService.getPermissionExplanation('CURVE_OPERATOR')"
            (click)="submeter()">
            {{ submetendo() ? 'Processando e Validando...' : '📤 Submeter e Validar Curva' }}
          </button>
        </div>
      </div>

      <!-- Erros de Leitura por Linha se houver -->
      @if (errosLeitura().length > 0) {
        <div class="card-erros">
          <div class="erros-header">
            <span class="icone">❌</span>
            <div>
              <h3>Erros de Leitura no Arquivo (Nenhum vértice foi publicado)</h3>
              <p>Corrija as inconsistências abaixo e submeta novamente o arquivo.</p>
            </div>
          </div>

          <table class="data-table">
            <thead>
              <tr>
                <th class="th-num">Linha</th>
                <th>Coluna</th>
                <th>Valor Encontrado</th>
                <th>Descrição do Erro</th>
              </tr>
            </thead>
            <tbody>
              @for (e of errosLeitura(); track e.linha + '-' + e.coluna) {
                <tr>
                  <td class="td-num tabular-nums">{{ e.linha }}</td>
                  <td><code>{{ e.coluna }}</code></td>
                  <td><code>{{ e.valorEncontrado || '-' }}</code></td>
                  <td class="erro-desc">{{ e.mensagem }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <!-- Resultado da Carga / Gate de Validação -->
      @if (resultadoCarga(); as r) {
        <div class="card-resultado" [class.resultado-sucesso]="r.estadoPublicacao === 'PUBLICADA'" [class.resultado-falha]="r.estadoPublicacao === 'REPROVADA'">
          @if (r.estadoPublicacao === 'PUBLICADA') {
            <div class="res-conteudo">
              <span class="icone">🎉</span>
              <div>
                <h3>Curva Publicada com Sucesso!</h3>
                <p>Versão <strong>v{{ r.numeroVersao }}</strong> criada com origem <strong>CARREGADA</strong>.</p>
                <a [routerLink]="['/curvas', codigoCurva]" [queryParams]="{ dataReferencia: dataReferencia }" class="btn btn-secondary btn-sm" style="margin-top: 8px;">
                  Abrir Viewer da Curva &rarr;
                </a>
              </div>
            </div>
          } @else {
            <div class="res-conteudo">
              <span class="icone">🚫</span>
              <div>
                <h3>Curva Reprovada no Gate de Consistência!</h3>
                <p>A curva carregada foi rejeitada em testes bloqueantes de consistência financeira. <strong>A versão anterior permanece vigente.</strong></p>
                @if (r.testesReprovados?.length) {
                  <div class="testes-bloqueantes">
                    Testes reprovados: <code>{{ r.testesReprovados?.join(', ') }}</code>
                  </div>
                }
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .page-container {
      padding: var(--space-xl);
      max-width: 1000px;
      margin: 0 auto;
    }
    .page-header {
      margin-bottom: var(--space-lg);
    }
    .page-title {
      font-size: 22px;
      font-weight: 700;
    }
    .page-subtitle {
      font-size: 13px;
      color: var(--color-text-muted);
    }
    .card-carga {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-lg);
      margin-bottom: var(--space-xl);
    }
    .secao-titulo {
      font-size: 15px;
      font-weight: 600;
      margin-bottom: var(--space-md);
    }
    .grid-form-3 {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 16px;
    }
    .campo {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .campo label {
      font-size: 12px;
      font-weight: 600;
      color: var(--color-text-secondary);
    }
    .bloco-templates {
      margin-top: 16px;
      padding: 12px;
      background-color: var(--color-bg-page);
      border-radius: var(--radius-sm);
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 12px;
    }
    .rotulo-templates {
      font-size: 13px;
      color: var(--color-text-secondary);
    }
    .botoes-template {
      display: flex;
      gap: 8px;
    }
    .form-file {
      background-color: var(--color-bg-input);
      color: var(--color-text-primary);
      border: 1px solid var(--color-border);
      padding: 8px;
      border-radius: var(--radius-sm);
      font-size: 13px;
    }
    .aviso-justificativa {
      font-size: 12px;
      color: var(--color-status-aviso);
    }
    .card-footer {
      margin-top: 20px;
      display: flex;
      justify-content: flex-end;
    }
    .card-erros {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-status-reprovada);
      border-radius: var(--radius-md);
      padding: var(--space-lg);
      margin-bottom: var(--space-xl);
    }
    .erros-header {
      display: flex;
      gap: 12px;
      align-items: flex-start;
      margin-bottom: 16px;
    }
    .erros-header .icone {
      font-size: 24px;
    }
    .erros-header h3 {
      font-size: 16px;
      color: #f87171;
    }
    .erros-header p {
      font-size: 13px;
      color: var(--color-text-muted);
    }
    .erro-desc {
      color: #fca5a5;
    }
    .card-resultado {
      border-radius: var(--radius-md);
      padding: var(--space-lg);
    }
    .resultado-sucesso {
      background-color: rgba(16, 185, 129, 0.1);
      border: 1px solid var(--color-status-publicada);
    }
    .resultado-falha {
      background-color: rgba(220, 38, 38, 0.1);
      border: 1px solid var(--color-status-reprovada);
    }
    .res-conteudo {
      display: flex;
      gap: 12px;
    }
    .res-conteudo .icone {
      font-size: 32px;
    }
    .res-conteudo h3 {
      font-size: 16px;
      margin-bottom: 4px;
    }
    .testes-bloqueantes {
      margin-top: 8px;
      font-size: 13px;
      color: #fca5a5;
    }
  `]
})
export class CargaManualComponent implements OnInit {
  public authService = inject(AuthService);
  private bffClient = inject(CurveBffClientService);
  private route = inject(ActivatedRoute);

  public codigoCurva: string = 'PRE';
  public dataReferencia: string = new Date().toISOString().substring(0, 10);
  public momento: string = 'FECHAMENTO';
  public justificativa: string = '';
  public arquivo: File | null = null;

  public submetendo = signal<boolean>(false);
  public errosLeitura = signal<ErroLinhaCargaDTO[]>([]);
  public resultadoCarga = signal<CargaManualResponse | null>(null);

  ngOnInit(): void {
    const cod = this.route.snapshot.paramMap.get('codigo');
    if (cod) {
      this.codigoCurva = cod;
    }
  }

  public obterUrlModelo(formato: 'CSV' | 'XLSX'): string {
    return this.bffClient.downloadModeloCargaUrl(this.codigoCurva, formato);
  }

  public onArquivoSelecionado(event: any): void {
    const files = event.target.files;
    if (files && files.length > 0) {
      this.arquivo = files[0];
    }
  }

  public submeter(): void {
    if (!this.arquivo || !this.justificativa.trim() || !this.authService.isOperator()) return;

    this.submetendo.set(true);
    this.errosLeitura.set([]);
    this.resultadoCarga.set(null);

    const formData = new FormData();
    formData.append('arquivo', this.arquivo);
    formData.append('dataReferencia', this.dataReferencia);
    formData.append('momento', this.momento);
    formData.append('justificativa', this.justificativa);

    this.bffClient.submeterCargaManual(this.codigoCurva, formData).subscribe({
      next: (resp) => {
        this.resultadoCarga.set(resp);
        this.submetendo.set(false);
      },
      error: (err) => {
        this.submetendo.set(false);
        if (err.status === 400 && err.error?.errosLinha) {
          this.errosLeitura.set(err.error.errosLinha);
        } else {
          alert(err.error?.mensagem || 'Falha ao processar carga manual.');
        }
      }
    });
  }
}
