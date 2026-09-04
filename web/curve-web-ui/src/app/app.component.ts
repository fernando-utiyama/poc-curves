import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from './core/auth/auth.service.ts';
import { AlertaBannerComponent } from './shared/components/alerta-banner/alerta-banner.component.ts';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterModule, AlertaBannerComponent],
  template: `
    <div class="app-layout">
      <!-- Barra Global de Alertas DLQ e Risco -->
      <app-alerta-banner></app-alerta-banner>

      <!-- Header Principal de Navegação -->
      <header class="app-header">
        <div class="header-marca">
          <a routerLink="/" class="marca-link">
            <span class="marca-icone">📈</span>
            <span class="marca-texto">Plataforma de Curvas</span>
          </a>
          <span class="ambiente-badge">POC LOCAL</span>
        </div>

        <nav class="nav-links">
          <a routerLink="/painel" routerLinkActive="active" class="nav-item">Painel do Dia</a>
          <a routerLink="/catalogo" routerLinkActive="active" class="nav-item">Catálogo</a>
          <a routerLink="/interpolacao" routerLinkActive="active" class="nav-item">Interpolação</a>
          <a routerLink="/ingestao/disparo" routerLinkActive="active" class="nav-item">Disparo Ingestão</a>
          <a routerLink="/ingestao/backfill" routerLinkActive="active" class="nav-item">Backfill</a>
          <a routerLink="/execucoes" routerLinkActive="active" class="nav-item">Execuções</a>
          <a routerLink="/pendencias-dlq" routerLinkActive="active" class="nav-item">Dead-Letter</a>
          <a routerLink="/modelos" routerLinkActive="active" class="nav-item">Modelos</a>
          <a routerLink="/comparacao" routerLinkActive="active" class="nav-item">Comparação</a>
        </nav>

        <div class="header-usuario">
          <div class="usuario-info">
            <span class="usuario-nome">{{ authService.currentUser().name }}</span>
            <span class="usuario-papeis">{{ authService.currentUser().roles.join(', ') }}</span>
          </div>
          <button class="btn btn-secondary btn-sm" (click)="authService.logout()">Sair</button>
        </div>
      </header>

      <!-- Conteúdo Principal da Rota -->
      <main class="app-main">
        <router-outlet></router-outlet>
      </main>

      <!-- Modal de Sessão Expirada (Tratamento de 401 com Preservação de Dados) -->
      @if (authService.isSessionExpired()) {
        <div class="modal-backdrop">
          <div class="modal-dialog">
            <h3 class="modal-title">Sessão Expirada</h3>
            <p class="modal-descricao">
              Sua sessão expirou junto ao provedor de identidade.
              Seus dados em preenchimento foram <strong>preservados</strong> com segurança.
            </p>
            <div class="modal-acoes" style="margin-top: 16px;">
              <button class="btn btn-primary" (click)="authService.reauthenticate()">
                🔑 Renovar Sessão e Continuar
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .app-layout {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }
    .app-header {
      background-color: var(--color-bg-surface);
      border-bottom: 1px solid var(--color-border);
      padding: 0 var(--space-xl);
      height: 56px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-lg);
    }
    .header-marca {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .marca-link {
      display: flex;
      align-items: center;
      gap: 8px;
      color: var(--color-text-primary);
      text-decoration: none;
      font-weight: 700;
      font-size: 15px;
    }
    .marca-icone {
      font-size: 20px;
    }
    .ambiente-badge {
      font-size: 10px;
      background-color: #0284c7;
      color: white;
      padding: 1px 6px;
      border-radius: 4px;
      font-weight: 600;
    }
    .nav-links {
      display: flex;
      align-items: center;
      gap: 4px;
      height: 100%;
    }
    .nav-item {
      color: var(--color-text-secondary);
      text-decoration: none;
      padding: 6px 10px;
      border-radius: var(--radius-sm);
      font-size: 13px;
      font-weight: 500;
      transition: all 0.15s;
    }
    .nav-item:hover {
      background-color: var(--color-bg-surface-hover);
      color: var(--color-text-primary);
    }
    .nav-item.active {
      background-color: #334155;
      color: var(--color-border-focus);
      font-weight: 600;
    }
    .header-usuario {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .usuario-info {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      line-height: 1.3;
    }
    .usuario-nome {
      font-size: 12px;
      font-weight: 600;
      color: var(--color-text-primary);
    }
    .usuario-papeis {
      font-size: 10px;
      color: var(--color-text-muted);
    }
    .app-main {
      flex: 1;
      background-color: var(--color-bg-page);
    }
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background-color: rgba(0, 0, 0, 0.75);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }
    .modal-dialog {
      background-color: var(--color-bg-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg);
      padding: 24px;
      max-width: 450px;
      width: 100%;
      text-align: center;
    }
    .modal-title {
      font-size: 18px;
      font-weight: 600;
      margin-bottom: 8px;
      color: #fca5a5;
    }
    .modal-descricao {
      font-size: 13px;
      color: var(--color-text-secondary);
      line-height: 1.5;
    }
  `]
})
export class AppComponent {
  public authService = inject(AuthService);
}
