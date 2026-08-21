# poc-curves

POC executável do desenho de solução completo de uma **Plataforma de Curvas** de Tesouraria/Risco.

O alvo produtivo roda em Azure (Azure Functions, Kafka, Azure SQL, pods em AKS, front Angular). Esta POC reproduz a mesma topologia **100% local em Podman rootless**, com uma única fonte de dados: **B3**.

Neste momento o repositório contém **o desenho** — especificações e diagramas. A implementação ainda não começou.

## O que a plataforma faz

Ingere dado de mercado da B3, constrói curvas de juros por bootstrap, publica vértices versionados com procedência, e responde interpolação em prazo arbitrário — com prazo de publicação, validação de consistência e rastreabilidade como requisitos, não como expectativas.

Duas curvas distintas convivem para o mesmo mercado:

| Origem da versão | Como é produzida | Quem publica |
|---|---|---|
| `CALCULADA` | bootstrap a partir dos contratos individuais (DI1) | `curve-engine` |
| `IMPORTADA` | curva pronta divulgada pela B3, transcrita vértice a vértice | `curve-processor` |
| `CARREGADA` | CSV ou planilha carregada pela tela, em contingência | `curve-processor` |

Mantê-las separadas e comparáveis transforma a conferência manual em controle diário.

## Arquitetura

O desenho está em [`docs/architecture/curves-platform.drawio`](docs/architecture/curves-platform.drawio) — 7 abas: contexto, componentes, os dois fluxos de curva, o motor por dentro, telas e modelo de dados. Abra em [app.diagrams.net](https://app.diagrams.net) ou na extensão Draw.io do VS Code.

Componentes:

- **`feeder-b3-marketdata`** (Node/TS) — adquire da B3 e publica em blocos no Kafka
- **`curve-processor`** (Java) — normaliza e persiste; publica curvas importadas e carregadas
- **`curve-engine`** (Java) — constrói por bootstrap, valida e publica; interpola sob demanda
- **`curve-orchestrator`** (Java) — agenda, dispara, faz backfill e acompanha execuções
- **`curve-api`** (Java) — cadastro de curva e consulta de tudo o que foi publicado
- **`curve-bff`** (Java) — única fronteira exposta ao navegador
- **`curve-web-ui`** (Angular) — painel do dia, viewer, disparo manual, carga e comparação
- **`libs/curve-kernel`** (Java) — matemática de curva, convenções B3/ANBIMA, reconciliador

## Três regras que atravessam o desenho

1. **Insumo ausente é ausência de dado, nunca valor a estimar.** A construção falha nomeando índice e data, e nada é publicado.
2. **Publicação nunca sobrescreve.** Reprocessar cria versão nova; a anterior vira `SUBSTITUIDA` com os vértices intactos.
3. **Todo número tem procedência.** Cada curva publicada aponta para a execução, os insumos, a versão da definição e o modelo que a produziu.

## Especificações

O planejamento usa [OpenSpec](https://github.com/Fission-AI/OpenSpec). Oito mudanças, cada uma com proposta, design técnico, especificações e tarefas:

```
openspec/changes/
├── curves-solution-architecture   # guarda-chuva: contratos, modelo de dados, runtime local
├── feeder-b3-marketdata
├── curve-processor
├── curve-engine
├── curve-orchestrator
├── curve-api
├── curve-bff
└── curve-web-ui
```

```bash
openspec list                                    # mudanças
openspec show --change curves-solution-architecture
openspec validate curves-solution-architecture
```

## Convenções

- **Java 21 / Spring Boot 3.4.x**, sem Lombok. Node 20 + TypeScript nos feeders. Angular 18+ no front.
- **Precisão**: todo valor com política de arredondamento de mercado é `BigDecimal` e `DECIMAL(28,12)`. `double` é proibido fora de numérica iterativa interna, e o JSON serializa esses valores como texto.
- **Banco**: tabelas, colunas e índices em **português**, sem acento, em snake_case. Código, tópicos Kafka e campos de evento em inglês. A tradução acontece no mapeamento objeto-relacional.
- **Containers**: Podman rootless. Sem Docker.
