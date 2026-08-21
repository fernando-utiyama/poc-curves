## 1. Esqueleto do serviço

- [ ] 1.1 Criar `services/feeder-b3-marketdata/` com Node 20, TypeScript estrito, `package.json` e scripts de build/test
- [ ] 1.2 Configurar Vitest, lint e formatação alinhados ao restante do repositório
- [ ] 1.3 Gerar os tipos TypeScript do envelope a partir de `contracts/events/envelope.schema.json`
- [ ] 1.4 Escrever o `Containerfile` para execução sob Podman

## 2. Núcleo de aquisição

- [ ] 2.1 Definir a interface `Feeder` com a operação de aquisição e o resultado de três estados
- [ ] 2.2 Implementar o resultado tipado `PUBLISHED` / `NO_DATA` / `FAILED` com motivo e diagnóstico
- [ ] 2.3 Implementar o cálculo determinístico de `loteId` (fonte, dataset, data, hash do conteúdo) e de `eventId` (lote + sequência)
- [ ] 2.3.1 Implementar a quebra do conteúdo em blocos, com corte estrutural e tamanho de bloco configurável
- [ ] 2.3.2 Implementar a publicação na faixa recebida no disparo, falhando quando a faixa não é informada
- [ ] 2.4 Implementar o cliente HTTP com timeout, backoff exponencial com jitter e retentativa só para falha de transporte
- [ ] 2.5 Implementar a verificação de integridade: conteúdo não vazio, tamanho declarado e abertura de arquivo compactado
- [ ] 2.6 Implementar o calendário de pregão B3 com os feriados reais e a decisão de dia de pregão
- [ ] 2.7 Implementar a distinção entre `NOT_YET_PUBLISHED` e fonte indisponível

## 3. Datasets B3

- [ ] 3.1 Implementar a aquisição do arquivo de Preços de Referência (PR)
- [ ] 3.2 Implementar a aquisição do BVBG.086 (preços e ajustes)
- [ ] 3.3 Implementar a aquisição do BVBG.028 (cadastro de instrumentos)
- [ ] 3.4 Implementar a aquisição da curva pronta pelo endpoint de taxas de referência, montando o pedido conforme o contrato da B3
- [ ] 3.5 Declarar `payloadKind` (`INDIVIDUAL_QUOTES` / `READY_CURVE`) em cada evento, determinado pelo endpoint consultado
- [ ] 3.6 Preservar encoding e formato originais, declarando o encoding no metadado do evento
- [ ] 3.7 Registrar os quatro datasets no roteador do feeder e falhar cedo para dataset não suportado

## 4. Publicação em Kafka

- [ ] 4.1 Implementar o produtor Kafka com o envelope comum, `loteId`, `sequencia`, `totalBlocos` e a chave de partição do catálogo
- [ ] 4.2 Validar o evento contra o schema no próprio produtor, antes do envio
- [ ] 4.3 Propagar o `correlationId` recebido para evento, log e reporte
- [ ] 4.4 Implementar log estruturado em JSON com `correlationId`, `dataset` e `referenceDate`

## 5. Adaptadores de execução

- [ ] 5.1 Implementar o adaptador de container (`main`) que lê parâmetros de ambiente e executa o núcleo
- [ ] 5.2 Implementar o handler de Azure Function sobre o mesmo núcleo, sem regra de aquisição própria
- [ ] 5.3 Implementar o reporte de início e de resultado ao `curve-orchestrator`
- [ ] 5.4 Expor endpoint de saúde para o compose Podman

## 6. Testes

- [ ] 6.1 Gravar as fixtures reais dos quatro datasets, com data de captura, URL e encoding
- [ ] 6.2 Testar os três resultados: publicação, ausência de dado e falha de fonte
- [ ] 6.3 Testar o `eventId` determinístico — mesmo conteúdo gera o mesmo id, conteúdo diferente gera id diferente
- [ ] 6.4 Testar retentativa só para falha de transporte e ausência de retentativa para erro de cliente
- [ ] 6.5 Testar feriado B3 em dia de semana resultando em `NO_DATA` sem chamada à fonte
- [ ] 6.6 Testar download truncado e arquivo compactado corrompido
- [ ] 6.7 Testar que encoding e separador decimal não são convertidos
- [ ] 6.7.1 Testar que `payloadKind` reflete o endpoint consultado, para os dois tipos de insumo
- [ ] 6.7.2 Testar a quebra em blocos: mesmo `loteId`, sequências distintas, `totalBlocos` correto
- [ ] 6.7.3 Testar arquivo que cabe em um bloco único
- [ ] 6.7.4 Testar falha no meio do arquivo deixando lote incompleto identificável
- [ ] 6.7.5 Testar que nenhum valor numérico é convertido pelo feeder
- [ ] 6.7.6 Testar a publicação em cada uma das três faixas e a falha quando a faixa não é informada
- [ ] 6.7.7 Medir o tamanho real dos arquivos B3 e calibrar o tamanho do bloco
- [ ] 6.8 Escrever o teste de contrato opcional contra a B3 real, fora do build padrão
- [ ] 6.9 Escrever o teste de fronteira que falha se o serviço declarar dependência de banco de dados
- [ ] 6.10 Verificar que a suíte padrão passa em máquina sem acesso à internet

## 7. Integração no ambiente local

- [ ] 7.1 Adicionar o feeder ao `compose.yaml` do Podman com healthcheck e variáveis de ambiente
- [ ] 7.2 Executar o feeder contra o Kafka local e confirmar o evento no tópico de dado bruto
- [ ] 7.3 Documentar em `services/feeder-b3-marketdata/README.md` os datasets, os parâmetros e como rodar isolado
- [ ] 7.4 Registrar em `docs/` o ponto de extensão para Bloomberg e LSEG, com o que um feeder novo precisa implementar
