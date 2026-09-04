import { Injectable } from '@angular/core';

export interface AppConfig {
  bffBaseUrl: string;
  authProviderUrl: string;
  clientId: string;
  baseHref: string;
  alertaPollIntervalMs: number;
  deadLetterIdadeAlertaMinutos: number;
}

const DEFAULT_CONFIG: AppConfig = {
  bffBaseUrl: '/api/v1',
  authProviderUrl: 'http://localhost:8180/realms/curvas',
  clientId: 'curve-web-ui',
  baseHref: '/',
  alertaPollIntervalMs: 10000,
  deadLetterIdadeAlertaMinutos: 60
};

@Injectable({
  providedIn: 'root'
})
export class RuntimeConfigService {
  private config: AppConfig = { ...DEFAULT_CONFIG };

  public async loadConfig(): Promise<void> {
    try {
      const response = await fetch('config.json');
      if (response.ok) {
        const json = await response.json();
        this.config = { ...DEFAULT_CONFIG, ...json };
      }
    } catch {
      console.warn('Não foi possível carregar config.json em tempo de execução. Usando configuração padrão.');
      this.config = { ...DEFAULT_CONFIG };
    }
  }

  public get<K extends keyof AppConfig>(key: K): AppConfig[K] {
    return this.config[key];
  }

  public getAll(): AppConfig {
    return { ...this.config };
  }

  public setConfig(customConfig: Partial<AppConfig>): void {
    this.config = { ...this.config, ...customConfig };
  }
}
