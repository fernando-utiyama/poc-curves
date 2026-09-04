import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'painel',
    pathMatch: 'full'
  },
  {
    path: 'painel',
    loadComponent: () => import('./features/painel-do-dia/painel-do-dia.component.ts').then(m => m.PainelDoDiaComponent)
  },
  {
    path: 'catalogo',
    loadComponent: () => import('./features/catalogo/catalogo.component.ts').then(m => m.CatalogoComponent)
  },
  {
    path: 'catalogo/nova',
    loadComponent: () => import('./features/catalogo/curva-cadastro.component.ts').then(m => m.CurvaCadastroComponent)
  },
  {
    path: 'catalogo/:codigo/editar',
    loadComponent: () => import('./features/catalogo/curva-cadastro.component.ts').then(m => m.CurvaCadastroComponent)
  },
  {
    path: 'curvas/:codigo',
    loadComponent: () => import('./features/viewer/curva-viewer.component.ts').then(m => m.CurvaViewerComponent)
  },
  {
    path: 'curvas/:codigo/carga-manual',
    loadComponent: () => import('./features/carga-manual/carga-manual.component.ts').then(m => m.CargaManualComponent)
  },
  {
    path: 'interpolacao',
    loadComponent: () => import('./features/interpolacao/interpolacao.component.ts').then(m => m.InterpolacaoComponent)
  },
  {
    path: 'ingestao/disparo',
    loadComponent: () => import('./features/ingestao/disparo-manual.component.ts').then(m => m.DisparoManualComponent)
  },
  {
    path: 'ingestao/backfill',
    loadComponent: () => import('./features/ingestao/backfill.component.ts').then(m => m.BackfillComponent)
  },
  {
    path: 'execucoes',
    loadComponent: () => import('./features/execucoes/execucoes-monitor.component.ts').then(m => m.ExecucoesMonitorComponent)
  },
  {
    path: 'pendencias-dlq',
    loadComponent: () => import('./features/pendencias-dlq/pendencias-dlq.component.ts').then(m => m.PendenciasDlqComponent)
  },
  {
    path: 'modelos',
    loadComponent: () => import('./features/modelos/modelos.component.ts').then(m => m.ModelosComponent)
  },
  {
    path: 'comparacao',
    loadComponent: () => import('./features/modelos/comparacao.component.ts').then(m => m.ComparacaoComponent)
  },
  {
    path: '**',
    redirectTo: 'painel'
  }
];
