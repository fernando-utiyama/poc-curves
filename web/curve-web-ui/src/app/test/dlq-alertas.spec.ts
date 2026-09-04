import { describe, it, expect, beforeEach } from 'vitest';
import { AlertasGlobalService } from '../core/state/alertas-global.service.ts';
import { RuntimeConfigService } from '../core/config/runtime-config.service.ts';
import { AlertasSumarioResponse } from '../core/api/models.ts';

describe('Dead-Letter & Alertas Globais', () => {
  let alertasService: AlertasGlobalService;
  let configService: RuntimeConfigService;

  beforeEach(() => {
    configService = new RuntimeConfigService();
    const mockBff = {
      getAlertasSumario: () => ({
        subscribe: () => ({ unsubscribe: () => {} })
      })
    };
    alertasService = new AlertasGlobalService(mockBff as any, configService);
    alertasService.pararPolling();
  });


  it('não deve exibir alerta quando a contagem for zero', () => {
    alertasService.setAlertasManual({
      gruposPendentesDlq: 0,
      totalMensagensDlq: 0,
      idadeMaisAntigaMinutos: 0,
      curvasEmRisco: 0,
      curvasAtrasadas: 0
    });

    expect(alertasService.temAlertaAtivo()).toBe(false);
  });

  it('deve sinalizar severidade crítica e destacar idade quando a falha mais antiga ultrapassar o limite', () => {
    const alertaEnvelhecido: AlertasSumarioResponse = {
      gruposPendentesDlq: 2,
      totalMensagensDlq: 150,
      idadeMaisAntigaMinutos: 95, // > 60 min limite padrão
      curvasEmRisco: 1,
      curvasAtrasadas: 0
    };

    alertasService.setAlertasManual(alertaEnvelhecido);

    expect(alertasService.temAlertaAtivo()).toBe(true);
    expect(alertasService.isEnvelhecido()).toBe(true);
    expect(alertasService.severidade()).toBe('CRITICA');
  });

  it('deve marcar estado desconhecido quando a consulta de alertas falhar em vez de assumir zero', () => {
    alertasService.setAlertasManual(null, true);
    expect(alertasService.isErrorState()).toBe(true);
    expect(alertasService.temAlertaAtivo()).toBe(false);
  });
});
