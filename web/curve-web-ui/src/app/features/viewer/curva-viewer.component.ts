import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { CurvaViewerResponse } from '../../core/api/models.ts';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component.ts';
import { SecaoDegradadaComponent } from '../../shared/components/secao-degradada/secao-degradada.component.ts';
import { TaxaFormatPipe, FatorDescontoPipe } from '../../shared/pipes/taxa-format.pipe.ts';

@Component({
  selector: 'app-curva-viewer',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    StatusBadgeComponent,
    SecaoDegradadaComponent,
    TaxaFormatPipe,
    FatorDescontoPipe
  ],
  template: `
    <div class="page-container">
      <div class="viewer-header">
        <div>
          <div class="breadcrumb">
            <a routerLink="/catalogo">Catálogo</a> / <span>{{ codigoCurva }}</span>
          </div>
          <h1 class="page-title">
            Curva {{ codigoCurva }} — {{ dados()?.nomeCurva || 'Estrutura a Termo' }}
          </h1>
        </div>

        <div class="controles-topo">
          <div class="seletor-grupo">
            <label>Data Ref:</label>
            <input type="date" [(ngModel)]="dataReferencia" (change)="carregarCurva()" class="form-input" />
          </div>
          <div class="seletor-grupo">
            <label>Momento:</label>
            <select [(ngModel)]="momento" (change)="carregarCurva()" class="form-select">
              <option value="FECHAMENTO">Fechamento</option>
              <option value="INTRADIARIO">Intradiário</option>
              <option value="ABERTURA">Abertura</option>
            </select>
          </div>
          <div class="seletor-grupo">
            <label>Versão:</label>
            <input type="number" [(ngModel)]="versaoFiltro" (change)="carregarCurva()" placeholder="Corrente" class="form-input versao-input" />
          </div>
          <button class="btn btn-secondary" (click)="carregarCurva()">🔄 Consultar</button>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-box">Carregando dados da curva...</div>
      } @else if (naoEncontrada()) {
        <div class="sem-curva-box">
          <span class="icone">📭</span>
          <h2>Nenhuma curva publicada para esta data</h2>
          <p>Não há versão publicada para a curva <strong>{{ codigoCurva }}</strong> em <strong>{{ dataReferencia }}</strong> ({{ momento }}).</p>
          <div class="acoes-sem-curva">
            <a [routerLink]="['/ingestao/disparo']" class="btn btn-primary">⚡ Disparar Ingestão do Dia</a>
            <a [routerLink]="['/curvas', codigoCurva, 'carga-manual']" class="btn btn-secondary">📥 Carga Manual de Contingência</a>
          </div>
        </div>
      } @else {
        <!--
          NG5002: "as" só é permitido no @if primário, nunca em @else if —
          por isso este ramo vira um @if aninhado, novo e primário, dentro
          do @else (corrigido na auditoria desta sessão: o build real nunca
          tinha sido rodado antes, então este erro de sintaxe nunca foi pego).
        -->
        @if (dados(); as c) {
        <!-- Faixa de Alerta de Versão Não-Corrente -->
        @if (!c.isVersaoCorrente && c.versao) {
          <div class="aviso-historico">
            ⚠️ Você está visualizando a versão histórica <strong>v{{ c.versao }}</strong> (Substituída ou Histórica), não a versão vigente atual.
          </div>
        }

        <!-- Resumo de Metadados e Procedência -->
        <div class="grid-metadados">
          <div class="card-meta">
            <span class="meta-label">Status da Versão</span>
            <div class="meta-valor"><app-status-badge [status]="c.estadoVersao"></app-status-badge></div>
          </div>
          <div class="card-meta">
            <span class="meta-label">Origem</span>
            <div class="meta-valor">{{ c.origemPublicacao || 'CALCULADA' }}</div>
          </div>
          <div class="card-meta">
            <span class="meta-label">Versão Publicada</span>
            <div class="meta-valor tabular-nums">v{{ c.versao }}</div>
          </div>
          <div class="card-meta">
            <span class="meta-label">Publicado em</span>
            <div class="meta-valor" style="font-size: 13px;">{{ c.procedencia?.publicadoEm ? (c.procedencia?.publicadoEm | date:'dd/MM/yyyy HH:mm:ss') : '-' }}</div>
          </div>
          <div class="card-meta">
            <span class="meta-label">Modelo de Cálculo</span>
            <div class="meta-valor" style="font-size: 13px;"><code>{{ c.procedencia?.modeloNome || 'Builtin PRE' }}</code></div>
          </div>
          <div class="card-meta">
            <span class="meta-label">Correlation ID</span>
            <div class="meta-valor meta-mono" [title]="c.procedencia?.correlationId || ''">{{ c.procedencia?.correlationId ? (c.procedencia?.correlationId | slice:0:8) + '...' : '-' }}</div>
          </div>
        </div>

        @if (c.origemPublicacao === 'CARREGADA' && c.procedencia?.justificativa) {
          <div class="justificativa-box">
            <strong>Origem da Carga:</strong> Carregado manualmente por <code>{{ c.procedencia?.carregadoPor }}</code>.
            <br /><strong>Justificativa:</strong> {{ c.procedencia?.justificativa }}
          </div>
        }

        <!-- Seções Degradadas se houver -->
        @if (c.secaoDegradada && c.secaoDegradada['interpolacao']) {
          <app-secao-degradada
            titulo="Módulo de Interpolação"
            [motivo]="c.secaoDegradada['interpolacao']"
            (repetir)="carregarCurva()">
          </app-secao-degradada>
        }

        <!-- Grid: Gráfico e Vértices -->
        <div class="layout-viewer">
          <!-- Tabela de Vértices -->
          <div class="painel-vertices">
            <div class="painel-topo">
              <h3 class="painel-titulo">Estrutura a Termo ({{ c.vertices.length }} Vértices)</h3>
              <span class="dica">Valores numéricos preservados com precisão original</span>
            </div>
            <div class="tabela-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th class="th-num">Prazo (DU)</th>
                    <th class="th-num">Prazo (DC)</th>
                    <th>Vencimento</th>
                    <th class="th-num">Taxa Anualizada (% a.a.)</th>
                    <th class="th-num">Fator de Desconto</th>
                  </tr>
                </thead>
                <tbody>
                  @for (v of c.vertices; track v.prazoDiasUteis) {
                    <tr>
                      <td class="td-num tabular-nums">{{ v.prazoDiasUteis }}</td>
                      <td class="td-num tabular-nums">{{ v.prazoDiasCorridos || '-' }}</td>
                      <td>{{ v.dataVencimento || '-' }}</td>
                      <td class="td-num tabular-nums taxa-destaque">{{ v.taxa | taxaFormat }}</td>
                      <td class="td-num tabular-nums">{{ v.fatorDesconto | fatorDesconto }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>

          <!-- Validação de Consistência -->
          @if (c.validacao) {
            <div class="painel-validacao">
              <div class="painel-topo">
                <h3 class="painel-titulo">Gate de Validação de Consistência</h3>
                <app-status-badge [status]="c.validacao.statusGeral"></app-status-badge>
              </div>
              <div class="lista-testes">
                @for (t of c.validacao.itens; track t.testeId) {
                  <div class="teste-item" [class.teste-reprovado]="t.resultado === 'REPROVADO'" [class.teste-aviso]="t.classificacao === 'AVISO' && t.resultado === 'REPROVADO'">
                    <div class="teste-topo">
                      <span class="teste-nome">{{ t.nomeTeste }}</span>
                      <span class="teste-badge" [class.badge-aprovado]="t.resultado === 'APROVADO'" [class.badge-falha]="t.resultado === 'REPROVADO'">
                        {{ t.resultado }}
                      </span>
                    </div>
                    @if (t.mensagem) {
                      <div class="teste-msg">{{ t.mensagem }}</div>
                    }
                    @if (t.medidaObservada) {
                      <div class="teste-medida">
                        Medida: <code>{{ t.medidaObservada }}</code>
                        @if (t.limiteAplicado) { | Limite: <code>{{ t.limiteAplicado }}</code> }
                      </div>
                    }
                  </div>
                }
              </div>
            </div>
          }
        </div>
        }
      }
    </div>
  `,
  styles: [`
    .page-container {
      padding: var(--space-xl);
      max-width: 1400px;
      margin: 0 auto;
    }
    .viewer-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      margin-bottom: var(--space-lg);
      flex-wrap: wrap;
      gap: var(--space-md);
    }
    .breadcrumb {
      font-size: 12px;
      color: var(--color-text-muted);
      margin-bottom: 4px;
    }
    .breadcrumb a {
      color: var(--color-border-focus);
      text-decoration: none;
    }
    .page-title {
      font-size: 22px;
      font-weight: 700;
    }
    .controles-topo {
      display: flex;
      align-items: center;
      gap: 12px;
      background-color: var(--color-bg-surface);
      padding: 8px 12px;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      flex-wrap: wrap;
    }
    .seletor-grupo {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
    }
    .versao-input {
      width: 80px;
    }
    .grid-metadados {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
      margin-bottom: var(--space-lg);
    }
    .card-meta {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: 10px 14px;
    }
    .meta-label {
      font-size: 11px;
      color: var(--color-text-muted);
      text-transform: uppercase;
      font-weight: 600;
    }
    .meta-valor {
      font-size: 15px;
      font-weight: 600;
      margin-top: 4px;
    }
    .meta-mono {
      font-family: var(--font-mono);
      font-size: 12px;
    }
    .justificativa-box {
      background-color: rgba(120, 53, 15, 0.15);
      border: 1px solid #d97706;
      border-radius: var(--radius-md);
      padding: 10px 16px;
      margin-bottom: var(--space-lg);
      font-size: 13px;
      color: #fde68a;
    }
    .aviso-historico {
      background-color: rgba(245, 158, 11, 0.15);
      border: 1px solid var(--color-status-aviso);
      padding: 10px 16px;
      border-radius: var(--radius-md);
      margin-bottom: var(--space-lg);
      font-size: 13px;
      color: #fef08a;
    }
    .layout-viewer {
      display: grid;
      grid-template-columns: 1fr 360px;
      gap: var(--space-lg);
    }
    @media (max-width: 1000px) {
      .layout-viewer { grid-template-columns: 1fr; }
    }
    .painel-vertices, .painel-validacao {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-md);
    }
    .painel-topo {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: var(--space-md);
      border-bottom: 1px solid var(--color-border);
      padding-bottom: 8px;
    }
    .painel-titulo {
      font-size: 15px;
      font-weight: 600;
    }
    .dica {
      font-size: 11px;
      color: var(--color-text-muted);
    }
    .tabela-scroll {
      max-height: 600px;
      overflow-y: auto;
    }
    .taxa-destaque {
      color: #38bdf8;
      font-weight: 600;
    }
    .lista-testes {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .teste-item {
      padding: 10px;
      background-color: var(--color-bg-page);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-sm);
    }
    .teste-reprovado {
      border-color: var(--color-status-reprovada);
      background-color: rgba(220, 38, 38, 0.1);
    }
    .teste-aviso {
      border-color: var(--color-status-aviso);
      background-color: rgba(245, 158, 11, 0.1);
    }
    .teste-topo {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 4px;
    }
    .teste-nome {
      font-size: 13px;
      font-weight: 600;
    }
    .teste-badge {
      font-size: 10px;
      padding: 1px 6px;
      border-radius: 3px;
      font-weight: 600;
    }
    .badge-aprovado { background-color: rgba(16, 185, 129, 0.2); color: #10b981; }
    .badge-falha { background-color: rgba(239, 68, 68, 0.2); color: #ef4444; }
    .teste-msg {
      font-size: 12px;
      color: var(--color-text-secondary);
      margin-bottom: 4px;
    }
    .teste-medida {
      font-size: 11px;
      color: var(--color-text-muted);
    }
    .sem-curva-box {
      text-align: center;
      padding: 48px 24px;
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
    }
    .sem-curva-box .icone {
      font-size: 40px;
      margin-bottom: 12px;
      display: block;
    }
    .sem-curva-box h2 {
      font-size: 18px;
      margin-bottom: 6px;
    }
    .sem-curva-box p {
      color: var(--color-text-muted);
      margin-bottom: 24px;
      font-size: 14px;
    }
    .acoes-sem-curva {
      display: flex;
      justify-content: center;
      gap: 12px;
    }
    .loading-box {
      padding: var(--space-xl);
      text-align: center;
      background-color: var(--color-bg-surface);
      border-radius: var(--radius-md);
    }
  `]
})
export class CurvaViewerComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private bffClient = inject(CurveBffClientService);

  public codigoCurva: string = 'PRE';
  public dataReferencia: string = new Date().toISOString().substring(0, 10);
  public momento: string = 'FECHAMENTO';
  public versaoFiltro: number | undefined = undefined;

  public dados = signal<CurvaViewerResponse | null>(null);
  public loading = signal<boolean>(false);
  public naoEncontrada = signal<boolean>(false);

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      this.codigoCurva = params.get('codigo') || 'PRE';
      this.route.queryParamMap.subscribe(qParams => {
        if (qParams.get('dataReferencia')) {
          this.dataReferencia = qParams.get('dataReferencia')!;
        }
        this.carregarCurva();
      });
    });
  }

  public carregarCurva(): void {
    this.loading.set(true);
    this.naoEncontrada.set(false);

    this.bffClient.getCurvaViewer(
      this.codigoCurva,
      this.dataReferencia,
      this.momento,
      this.versaoFiltro
    ).subscribe({
      next: (resp) => {
        this.dados.set(resp);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 404) {
          this.naoEncontrada.set(true);
          this.dados.set(null);
        }
      }
    });
  }
}
