# poc-curvas

Plataforma de Curvas de Tesouraria/Risco: especificação (OpenSpec) e código transcrito dos serviços reais, com os nomes de pacote trocados para `br.com.poc`.

Alvo produtivo em Azure (AKS, Azure SQL, Kafka, Blob Storage, Entra ID). Cada serviço é um projeto Maven (ou Node) independente, como no sistema real, com o seu próprio `docker/`.

## Serviços

| Pasta | Sistema real | O que faz |
|---|---|---|
| `services/conector` | conector B3 (Node/TS) | obtém o `TaxaSwap.txt` da B3 (download, upload ou reprocessamento), guarda a cópia no Blob e publica a carga no Kafka |
| `services/processor` | `acts-srv-curvas-processor` | consome as cargas do Kafka, grava o dado bruto (`tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr`, `tBbergCurvaPrimr`) e avisa o engine (`POST /api/v1/cargas`) |
| `services/engine` | `acts-srv-tcen-curvas-engine` | constrói as curvas (modelos nativos e Groovy), grava os pontos em `tDadoVertcCurva` e a interpolada em `tDadoCurva`; consulta, simulação e auditoria |
| `services/orchestrator` | orquestrador | agenda tarefas, dispara o download do conector e a rede de segurança de construção do engine |
| `services/curves` | `acts-srv-curvas` | cadastro de provedores e de curvas de mercado, ligações, configuração de cálculo, painel, edição manual de pontos e do bruto B3 |
| `services/curve-bff` | BFF | fronteira do navegador; será transcrito do sistema real |
| `web/curve-web-ui` | front Angular | telas do gestor |

Banco: o schema real está em `db/migration/001_SCRIPT_INICIAL.sql`. Nenhum serviço cria tabela; mudanças de schema vão como script para o dono do banco (change `banco-curvas-ajustes`).

## Especificações

```
openspec/changes/
├── conector-b3-webhook-ingest   # conector B3: obtém o TaxaSwap, arquiva e avisa a carga
├── processor-carga-b3           # processor: interpreta, valida e grava o TaxaSwap, avisa o engine
├── engine-construcao-curvas     # engine, parte 1: as 7 curvas e as rotas usadas pelos outros serviços
├── engine-modelos-curva         # engine, parte 2: Groovy, memória de cálculo, auditoria, resiliência
├── curves-cadastro-curvas       # curves, parte 1: CRUD de curva, ligações, configuração e bruto B3
├── curves-operacao-curvas       # curves, parte 2: planilhas, painel, pontos manuais, origens secundárias
├── orquestrador-curvas          # orquestrador: tarefas e disparos
└── banco-curvas-ajustes         # ALTER de tBbergCurvaPrimr e DROP de tCurvaData
```

Cada change tem `proposal.md`, `specs/` e `tasks.md`. O `design.md` e o `implementacao.md` (guia passo a passo) ficam na primeira parte de cada serviço e valem para a segunda.

```bash
openspec list
openspec validate <change> --strict
```

## Convenções

- **Java 21 nativo e hexagonal:** records, sealed e `switch` com pattern matching, `java.time`, virtual threads; `domain` sem framework, portas em `application/port`, adaptadores em `adapter/in` e `adapter/out`. Lombok e MapStruct só onde o código existente já usa.
- **Precisão:** valor de curva é sempre `BigDecimal`, nunca `double`; no JSON, decimal como texto.
- **Nomes:** classes de persistência com o nome da tabela real (`CurvaMercdEntity`, `BtrsCurvaPrimrEntity`).
- **Fuso:** a JVM roda em `America/Sao_Paulo`.
- **Idioma:** documentação e mensagens em pt-BR com acentuação; códigos (enums, erros, avisos) não se traduzem.
- **Blob:** só os arquivos originais dos feeders e os scripts Groovy.
