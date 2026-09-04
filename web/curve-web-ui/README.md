# @poc-curvas/curve-web-ui

Frontend Angular 18+ da Plataforma de Curvas de Tesouraria/Risco.

## Visão Geral

- **Arquitetura**: Aplicação Angular 18 autônoma (Standalone Components, Signals e Router), comunicando-se exclusivamente com o `curve-bff`.
- **Preservação de Opção (Shell Liquid)**: Rota base configurável, tema baseado em design tokens CSS substituíveis e contexto de usuário centralizado para permitir embarque futuro como Micro-Frontend (Module Federation) sem reescrita.
- **Precisão Numérica**: Todos os valores de mercado (taxas, fatores de desconto, spreads) são tratados estritamente como `string` de ponta a ponta, sem conversão para ponto flutuante no JavaScript (`parseFloat`/`Number` proibidos por regra de lint).
- **Tratamento de Sessão**: Detecção de expiração OIDC (401) com preservação automática de formulários em preenchimento.

## Telas Principais

1. **Painel do Dia (`/painel`)**: Monitoramento em tempo real do status das curvas ativas, horários limites de corte, alertas de risco de atraso e redisparo prioritário.
2. **Catálogo de Curvas (`/catalogo`)**: Listagem e cadastro versionado de definições de curva (`BOOTSTRAPPED` vs `IMPORTED`).
3. **Viewer de Curva (`/curvas/:codigo`)**: Visualização da estrutura a termo, procedência (modelo, insumos, lote B3), validação de consistência financeira e histórico de versões.
4. **Consulta Interpolada (`/interpolacao`)**: Amostragem de prazos arbitrários em lote com isolamento de erros fora do intervalo (política estrita).
5. **Disparo Manual de Ingestão (`/ingestao/disparo`)**: Disparo de aquisição prioritária para dados individuais (BVBG) e curvas prontas B3 com geração de `correlationId`.
6. **Backfill Histórico (`/ingestao/backfill`)**: Reconstituição histórica de dados com acompanhamento de progresso e capacidade de interrupção.
7. **Monitor de Execuções (`/execucoes`)**: Rastreabilidade do ciclo de vida das execuções com tratamento neutro para `SEM_DADO`.
8. **Gestão de Dead-Letter (`/pendencias-dlq`)**: Alerta global persistente, agrupamento inteligente de falhas e reprocessamento/descarte com justificativa obrigatória.
9. **Carga Manual de Contingência (`/curvas/:codigo/carga-manual`)**: Upload de CSV/XLSX com download de template, validação por linha e gate de consistência.
10. **Modelos e Comparação (`/modelos`, `/comparacao`)**: Importação de modelos Groovy e comparação ponto a ponto com análise de spreads em bps.

## Scripts Disponíveis

```bash
# Executar testes unitários e de integração
npm test

# Executar lint de precisão de valores de mercado
npm run lint

# Verificar tipagem TypeScript estrita
npm run build
```

## Execução com Podman

A imagem de container é construída via multi-stage build servida estaticamente pelo Nginx:

```bash
podman build -t curve-web-ui:latest -f Containerfile .
podman run -d -p 8080:80 --name curve-web-ui curve-web-ui:latest
```
