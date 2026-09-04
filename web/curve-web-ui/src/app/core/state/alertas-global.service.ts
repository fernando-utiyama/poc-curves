import { Injectable, signal, computed, OnDestroy } from '@angular/core';
import { CurveBffClientService } from '../api/curve-bff-client.service.ts';
import { RuntimeConfigService } from '../config/runtime-config.service.ts';
import { AlertasSumarioResponse, SeveridadeDlq } from '../api/models.ts';

@Injectable({
  providedIn: 'root'
})
export class AlertasGlobalService implements OnDestroy {
  private alertasSignal = signal<AlertasSumarioResponse | null>(null);
  private isErrorStateSignal = signal<boolean>(false);
  private timerId: any = null;

  public alertas = this.alertasSignal.asReadonly();
  public isErrorState = this.isErrorStateSignal.asReadonly();

  public temAlertaAtivo = computed(() => {
    const a = this.alertasSignal();
    if (!a) return false;
    return a.gruposPendentesDlq > 0 || a.curvasEmRisco > 0 || a.curvasAtrasadas > 0;
  });

  public gruposPendentesDlq = computed(() => this.alertasSignal()?.gruposPendentesDlq ?? 0);
  public totalMensagensDlq = computed(() => this.alertasSignal()?.totalMensagensDlq ?? 0);
  public idadeMaisAntigaMinutos = computed(() => this.alertasSignal()?.idadeMaisAntigaMinutos ?? 0);
  public curvasEmRisco = computed(() => this.alertasSignal()?.curvasEmRisco ?? 0);
  public curvasAtrasadas = computed(() => this.alertasSignal()?.curvasAtrasadas ?? 0);

  public severidade = computed<SeveridadeDlq>(() => {
    const a = this.alertasSignal();
    if (!a || a.gruposPendentesDlq === 0) return 'NORMAL';
    const limiteMin = this.configService.get('deadLetterIdadeAlertaMinutos') || 60;
    if (a.idadeMaisAntigaMinutos >= limiteMin || a.curvasAtrasadas > 0) {
      return 'CRITICA';
    }
    return 'ALERTA';
  });

  public isEnvelhecido = computed(() => {
    const a = this.alertasSignal();
    if (!a || a.gruposPendentesDlq === 0) return false;
    const limiteMin = this.configService.get('deadLetterIdadeAlertaMinutos') || 60;
    return a.idadeMaisAntigaMinutos >= limiteMin;
  });

  constructor(
    private bffClient: CurveBffClientService,
    private configService: RuntimeConfigService
  ) {
    this.iniciarPolling();
  }

  public iniciarPolling(): void {
    this.carregar();
    const intervalMs = this.configService.get('alertaPollIntervalMs') || 10000;
    if (typeof window !== 'undefined' && !this.timerId) {
      this.timerId = setInterval(() => this.carregar(), intervalMs);
    }
  }

  public pararPolling(): void {
    if (this.timerId) {
      clearInterval(this.timerId);
      this.timerId = null;
    }
  }

  public carregar(): void {
    if (!this.bffClient || typeof this.bffClient.getAlertasSumario !== 'function') {
      return;
    }
    this.bffClient.getAlertasSumario().subscribe({
      next: (resp) => {
        this.alertasSignal.set(resp);
        this.isErrorStateSignal.set(false);
      },
      error: () => {
        // Quando a consulta falha, marca estado de erro (não exibe como 0 nem limpo)
        this.isErrorStateSignal.set(true);
      }
    });
  }


  public setAlertasManual(alertas: AlertasSumarioResponse | null, isError: boolean = false): void {
    this.alertasSignal.set(alertas);
    this.isErrorStateSignal.set(isError);
  }

  ngOnDestroy(): void {
    this.pararPolling();
  }
}
