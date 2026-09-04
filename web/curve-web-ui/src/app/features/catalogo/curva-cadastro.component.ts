import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CurveBffClientService } from '../../core/api/curve-bff-client.service.ts';
import { AuthService } from '../../core/auth/auth.service.ts';
import { FormPreservationService } from '../../core/state/form-preservation.service.ts';
import { DefinicaoCurvaDTO, ModeloDTO } from '../../core/api/models.ts';

@Component({
  selector: 'app-curva-cadastro',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">{{ isEdicao ? 'Editar Definição de Curva: ' + definicao.codigo : 'Nova Definição de Curva' }}</h1>
          <p class="page-subtitle">Configure as convenções de cálculo, regras de interpolação e parâmetros de validação.</p>
        </div>
        <div>
          <a routerLink="/catalogo" class="btn btn-secondary">Voltar ao Catálogo</a>
        </div>
      </div>

      <!-- Aviso de Versionamento Imutável -->
      <div class="aviso-versionamento">
        <span class="icone">ℹ️</span>
        <div class="aviso-texto">
          <strong>Aviso de Versionamento:</strong> Salvar esta alteração <u>não sobrescreve</u> a versão atual.
          Uma nova versão da definição será criada (v{{ (definicao.versaoNumero || 0) + 1 }}), preservando o histórico de todas as curvas já publicadas sob versões anteriores.
        </div>
      </div>

      @if (erroValidacao()) {
        <div class="erro-backend">
          <strong>Erro de Validação:</strong> {{ erroValidacao() }}
        </div>
      }

      <form (ngSubmit)="salvar()" class="form-container">
        <!-- Bloco 1: Identificação Básica -->
        <div class="form-secao">
          <h2 class="secao-titulo">1. Identificação Básica</h2>
          <div class="grid-form-3">
            <div class="campo">
              <label>Código da Curva *</label>
              <input type="text" [(ngModel)]="definicao.codigo" name="codigo" [disabled]="isEdicao" required class="form-input" placeholder="Ex: PRE, DOL, DI1" />
              @if (isEdicao) { <small class="dica">O código de identificação é imutável após a criação.</small> }
            </div>
            <div class="campo">
              <label>Nome Descritivo *</label>
              <input type="text" [(ngModel)]="definicao.nome" name="nome" required class="form-input" placeholder="Ex: Curva Pré Fixada DI1" />
            </div>
            <div class="campo">
              <label>Moeda *</label>
              <input type="text" [(ngModel)]="definicao.moeda" name="moeda" required class="form-input" placeholder="Ex: BRL, USD" />
            </div>
          </div>

          <div class="grid-form-2" style="margin-top: 12px;">
            <div class="campo">
              <label>Modo de Origem *</label>
              <select [(ngModel)]="definicao.modoOrigem" name="modoOrigem" (change)="onModoOrigemChange()" class="form-select">
                <option value="BOOTSTRAPPED">Construída via Bootstrap (BOOTSTRAPPED)</option>
                <option value="IMPORTED">Importada Vértice a Vértice (IMPORTED)</option>
              </select>
            </div>
            <div class="campo">
              <label>Horário Limite de Publicação Diária (Corte) *</label>
              <input type="time" [(ngModel)]="definicao.horarioLimitePublicacao" name="horarioLimitePublicacao" required class="form-input" />
            </div>
          </div>
        </div>

        <!-- Bloco 2: Modelo de Construção -->
        <div class="form-secao">
          <h2 class="secao-titulo">2. Modelo de Construção</h2>
          @if (definicao.modoOrigem === 'IMPORTED') {
            <div class="info-bloqueado">
              <span>ℹ️</span> Curvas com modo de origem <strong>IMPORTED</strong> não utilizam motor de cálculo/bootstrap; os vértices são recebidos e publicados diretamente da B3.
            </div>
          } @else {
            <div class="campo">
              <label>Modelo de Cálculo Selecionado</label>
              <select [(ngModel)]="definicao.modeloApontado" name="modeloApontado" class="form-select">
                <option value="BUILTIN_PRE_DI1">Modelo Padrão Embutido (BUILTIN - Curva PRE DI1)</option>
                @for (m of modelosDisponiveis(); track m.id) {
                  <option [value]="m.nome">{{ m.nome }} ({{ m.tipo }})</option>
                }
              </select>
              <small class="dica">Pode ser trocado a qualquer momento sem necessidade de deploy.</small>
            </div>
          }
        </div>

        <!-- Bloco 3: Convenções Financeiras e Interpolação -->
        <div class="form-secao">
          <h2 class="secao-titulo">3. Convenções Financeiras e Interpolação</h2>
          <div class="grid-form-3">
            <div class="campo">
              <label>Contagem de Dias *</label>
              <select [(ngModel)]="definicao.contagemDias" name="contagemDias" class="form-select">
                <option value="DU_252">Dias Úteis / 252 (DU_252)</option>
                <option value="ACT_360">Atual / 360 (ACT_360)</option>
                <option value="ACT_365">Atual / 365 (ACT_365)</option>
              </select>
            </div>
            <div class="campo">
              <label>Calendário de Feriados *</label>
              <select [(ngModel)]="definicao.calendario" name="calendario" class="form-select">
                <option value="B3_ANBIMA">B3 / ANBIMA (Brasil)</option>
                <option value="CORRIDO">Dias Corridos</option>
              </select>
            </div>
            <div class="campo">
              <label>Política de Arredondamento *</label>
              <select [(ngModel)]="definicao.politicaArredondamento" name="politicaArredondamento" class="form-select">
                <option value="TRUNCATE_8">Truncamento em 8 casas decimais (B3)</option>
                <option value="TRUNCATE_12">Truncamento em 12 casas decimais</option>
                <option value="HALF_UP_8">Arredondamento HALF_UP (8 casas)</option>
                <option value="HALF_UP_12">Arredondamento HALF_UP (12 casas)</option>
              </select>
            </div>
          </div>

          <div class="grid-form-2" style="margin-top: 12px;">
            <div class="campo">
              <label>Método de Interpolação Padrão *</label>
              <select [(ngModel)]="definicao.interpolador" name="interpolador" class="form-select">
                <option value="LINEAR">Linear Simples</option>
                <option value="FLAT_FORWARD">Flat Forward Exponencial (ANBIMA)</option>
                <option value="LOG_LINEAR">Log-Linear</option>
                <option value="LOG_CUBIC">Log-Cúbico</option>
                <option value="NATURAL_CUBIC_SPLINE">Spline Cúbico Natural</option>
                <option value="MONOTONIC_CONVEX">Monotônico Convexo (Hagan-West)</option>
                <option value="FLAT_FORWARD_LINEAR">Flat Forward Linear</option>
              </select>
            </div>
            <div class="campo">
              <label>Política de Extrapolação *</label>
              <select [(ngModel)]="definicao.politicaExtrapolacao" name="politicaExtrapolacao" class="form-select">
                <option value="STRICT">Estrita (STRICT - Rejeita com erro fora dos vértices)</option>
                <option value="FLAT_RATE">Taxa Constante (FLAT_RATE)</option>
                <option value="FLAT_FORWARD">Forward Constante (FLAT_FORWARD)</option>
                <option value="FLAT_FORWARD_LINEAR">Forward Linear (FLAT_FORWARD_LINEAR)</option>
              </select>
            </div>
          </div>
        </div>

        <!-- Botões de Ação -->
        <div class="form-footer">
          <button
            type="submit"
            class="btn btn-primary"
            [disabled]="salvando() || !authService.isAdmin()"
            [title]="authService.isAdmin() ? 'Salvar definição' : authService.getPermissionExplanation('CURVE_ADMIN')">
            {{ salvando() ? 'Salvando...' : (isEdicao ? 'Criar Nova Versão da Definição' : 'Cadastrar Definição') }}
          </button>
          <a routerLink="/catalogo" class="btn btn-secondary">Cancelar</a>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .page-container {
      padding: var(--space-xl);
      max-width: 1000px;
      margin: 0 auto;
    }
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
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
    .aviso-versionamento {
      display: flex;
      gap: 12px;
      padding: 12px 16px;
      background-color: rgba(2, 132, 199, 0.15);
      border: 1px solid var(--color-primary);
      border-radius: var(--radius-md);
      margin-bottom: var(--space-lg);
      font-size: 13px;
      color: #bae6fd;
    }
    .erro-backend {
      background-color: var(--color-status-reprovada-bg);
      border: 1px solid var(--color-status-reprovada);
      color: #fca5a5;
      padding: 12px 16px;
      border-radius: var(--radius-md);
      margin-bottom: var(--space-lg);
      font-size: 13px;
    }
    .form-container {
      display: flex;
      flex-direction: column;
      gap: var(--space-lg);
    }
    .form-secao {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      padding: var(--space-lg);
    }
    .secao-titulo {
      font-size: 15px;
      font-weight: 600;
      margin-bottom: var(--space-md);
      border-bottom: 1px solid var(--color-border);
      padding-bottom: 6px;
    }
    .grid-form-3 {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 16px;
    }
    .grid-form-2 {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
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
    .dica {
      font-size: 11px;
      color: var(--color-text-muted);
    }
    .info-bloqueado {
      background-color: rgba(148, 163, 184, 0.1);
      padding: 10px 14px;
      border-radius: var(--radius-sm);
      font-size: 13px;
      color: var(--color-text-secondary);
    }
    .form-footer {
      display: flex;
      gap: 12px;
      margin-top: var(--space-md);
    }
  `]
})
export class CurvaCadastroComponent implements OnInit {
  public authService = inject(AuthService);
  private bffClient = inject(CurveBffClientService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private formPreservation = inject(FormPreservationService);

  public isEdicao: boolean = false;
  public salvando = signal<boolean>(false);
  public erroValidacao = signal<string | null>(null);
  public modelosDisponiveis = signal<ModeloDTO[]>([]);

  public definicao: Partial<DefinicaoCurvaDTO> = {
    codigo: '',
    nome: '',
    moeda: 'BRL',
    modoOrigem: 'BOOTSTRAPPED',
    estado: 'ATIVA',
    versaoNumero: 0,
    contagemDias: 'DU_252',
    calendario: 'B3_ANBIMA',
    interpolador: 'FLAT_FORWARD',
    politicaExtrapolacao: 'STRICT',
    politicaArredondamento: 'TRUNCATE_8',
    modeloApontado: 'BUILTIN_PRE_DI1',
    horarioLimitePublicacao: '19:00',
    janelaBloqueioMinutos: 30
  };

  ngOnInit(): void {
    this.carregarModelos();
    const codigo = this.route.snapshot.paramMap.get('codigo');
    if (codigo) {
      this.isEdicao = true;
      this.carregarDefinicao(codigo);
    } else {
      // Verifica se há rascunho preservado
      const draft = this.formPreservation.getDraft<Partial<DefinicaoCurvaDTO>>('nova_curva');
      if (draft) {
        this.definicao = { ...this.definicao, ...draft };
      }
    }
  }

  private carregarModelos(): void {
    this.bffClient.getModelos().subscribe({
      next: (resp) => this.modelosDisponiveis.set(resp.modelos || [])
    });
  }

  private carregarDefinicao(codigo: string): void {
    this.bffClient.getDefinicaoCurva(codigo).subscribe({
      next: (def) => {
        this.definicao = { ...def };
      }
    });
  }

  public onModoOrigemChange(): void {
    if (this.definicao.modoOrigem === 'IMPORTED') {
      this.definicao.modeloApontado = undefined;
    } else if (!this.definicao.modeloApontado) {
      this.definicao.modeloApontado = 'BUILTIN_PRE_DI1';
    }
  }

  public salvar(): void {
    if (!this.authService.isAdmin()) return;

    this.salvando.set(true);
    this.erroValidacao.set(null);

    const req = this.definicao;
    const codigo = this.definicao.codigo || '';

    const obs = this.isEdicao
      ? this.bffClient.atualizarDefinicaoCurva(codigo, req)
      : this.bffClient.criarDefinicaoCurva(codigo, req);

    obs.subscribe({
      next: () => {
        this.formPreservation.clearDraft('nova_curva');
        this.salvando.set(false);
        this.router.navigate(['/catalogo']);
      },
      error: (err) => {
        this.erroValidacao.set(err.error?.mensagem || 'Falha ao salvar definição da curva.');
        this.salvando.set(false);
      }
    });
  }
}
