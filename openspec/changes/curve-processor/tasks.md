## 1. Esqueleto do serviço

- [ ] 1.1 Criar o módulo Maven `services/curve-processor` com Java 21, Spring Boot 3.4.x e sem Lombok
- [ ] 1.2 Configurar Spring for Apache Kafka com confirmação manual de offset e desserializador que encapsula falha em vez de lançá-la
- [ ] 1.3 Configurar um listener container por faixa (rotina, prioritária, massa), com grupo de consumo e pool de thread próprios
- [ ] 1.4 Configurar tratamento de erro com recuperador terminal em dead-letter, sem retentativa infinita
- [ ] 1.5 Dimensionar registros por poll e intervalo máximo entre polls sobre o pior caso medido
- [ ] 1.6 Configurar a fonte de dados do SQL Server com credencial restrita às tabelas da fronteira do serviço
- [ ] 1.7 Escrever o `Containerfile` e adicionar o serviço ao compose Podman com healthcheck

## 2. Consumo e validação

- [ ] 2.1 Implementar a validação do envelope contra o schema de `contracts/events`, antes de qualquer parsing
- [ ] 2.2 Implementar o envio à dead-letter com motivo, `correlationId`, `eventId`, offset e payload
- [ ] 2.3 Implementar a retentativa com backoff exponencial só para falha transitória
- [ ] 2.4 Implementar o desvio por `payloadKind` entre dado individual e curva pronta
- [ ] 2.5 Implementar o reconhecimento de `eventId` já processado

## 3. Normalização de dado individual

- [ ] 3.1 Definir a interface `DatasetParser` e o registro de parsers por `dataset`
- [ ] 3.2 Implementar o parser do arquivo de Preços de Referência
- [ ] 3.3 Implementar o parser do BVBG.086
- [ ] 3.4 Implementar o parser do BVBG.028
- [ ] 3.5 Implementar a conversão de texto para `BigDecimal` tratando explicitamente o separador decimal da fonte
- [ ] 3.6 Implementar a derivação documentada do `instrumentKey` por dataset
- [ ] 3.7 Implementar a decodificação pelo encoding declarado no evento
- [ ] 3.8 Implementar a dead-letter para `UNKNOWN_DATASET` e `PARSE_FAILED`

## 4. Persistência de market data

- [ ] 4.1 Implementar o upsert sobre a chave `(fonte, conjunto_dados, data_referencia, chave_instrumento)`
- [ ] 4.2 Implementar a transação atômica por bloco recebido
- [ ] 4.3 Implementar a criação do lote no primeiro bloco e a consolidação pela contagem de `totalBlocos`
- [ ] 4.4 Implementar a marcação de lote incompleto após o tempo limite, nomeando as sequências faltantes
- [ ] 4.5 Impedir a emissão do evento de dado normalizado enquanto o lote estiver aberto ou incompleto
- [ ] 4.6 Implementar gravação com ordenação determinística por chave, contra deadlock entre faixas
- [ ] 4.7 Implementar o registro de `lote_ingestao` com contagens, hash do payload e `correlationId`
- [ ] 4.8 Implementar a detecção e o registro de divergência de valor
- [ ] 4.9 Implementar a emissão de `marketdata.normalized.v1` após o commit

## 5. Ingestão de curva pronta

- [ ] 5.1 Implementar o parser de vértices da curva pronta da B3, preservando prazo e valor exatos
- [ ] 5.2 Implementar a resolução da definição de curva pelo identificador de curva na origem, com dead-letter `UNMAPPED_CURVE`
- [ ] 5.3 Implementar a verificação de coerência entre `payloadKind` e o modo de origem da definição, falhando nomeando ambos
- [ ] 5.4 Implementar a publicação atômica de `versao_curva`, `vertice_curva` e `procedencia_curva` para curvas `IMPORTED`
- [ ] 5.5 Implementar a marcação da versão anterior como `SUPERSEDED` e a numeração incremental de versão
- [ ] 5.6 Implementar a proveniência com lote, `eventId`, arquivo de origem, hash e fonte
- [ ] 5.7 Implementar a emissão de `curve.published.v1` após o commit, com o mesmo contrato usado pelo motor
- [ ] 5.8 Implementar a recusa de publicação para definições com modo `BOOTSTRAPPED`
- [ ] 5.9 Implementar a dead-letter para `EMPTY_CURVE`

## 6. Carga manual de curva

- [ ] 6.1 Definir o leiaute do arquivo de carga no contrato, com cabeçalho e colunas por convenção da curva
- [ ] 6.2 Implementar o leitor de CSV com separador e decimal declarados, convertendo direto para decimal de precisão arbitrária
- [ ] 6.3 Implementar o leitor de planilha, produzindo resultado idêntico ao do CSV equivalente
- [ ] 6.4 Implementar a coleta de **todos** os erros por linha e coluna, sem aplicação parcial
- [ ] 6.5 Implementar a recusa por cabeçalho divergente, arquivo vazio, prazo duplicado e valor inválido
- [ ] 6.6 Exigir justificativa não vazia e registrar autor, instante, nome do arquivo e hash do conteúdo
- [ ] 6.7 Gravar a versão com origem `CARREGADA` em `EM_VALIDACAO` e submetê-la à bateria de validação
- [ ] 6.8 Registrar como não aplicáveis os testes que dependem de insumos de calibração inexistentes
- [ ] 6.9 Aplicar o versionamento padrão: incremento, substituição só na promoção, anterior preservada
- [ ] 6.10 Reconhecer recarga do mesmo arquivo pelo hash, sem criar versão duplicada
- [ ] 6.11 Aplicar o limite de tamanho de arquivo e a exigência de perfil operador ou administrador

## 7. Observabilidade

- [ ] 7.1 Propagar o `correlationId` do evento para log, banco e eventos derivados
- [ ] 7.2 Implementar log estruturado em JSON com `correlationId`, `dataset`, `payloadKind` e `referenceDate`
- [ ] 7.3 Expor métricas de eventos consumidos, pontos gravados, divergências, curvas publicadas e mensagens em dead-letter
- [ ] 7.4 Expor endpoints de saúde e de prontidão

## 8. Testes

- [ ] 8.1 Testar idempotência consumindo o mesmo evento repetidamente e comparando o estado completo do banco
- [ ] 8.2 Testar replay do tópico inteiro com estado final idêntico
- [ ] 8.3 Testar reversão completa do lote em falha no meio da gravação
- [ ] 8.4 Testar divisão em blocos para lote acima do limite, com contagens consolidadas
- [ ] 8.5 Testar registro de divergência quando o valor é regravado diferente, e ausência de divergência quando é igual
- [ ] 8.6 Testar dead-letter para envelope inválido, dataset desconhecido e payload não parseável
- [ ] 8.7 Testar os parsers B3 contra fixtures reais, verificando precisão decimal preservada
- [ ] 8.8 Testar que a publicação de `marketdata.normalized.v1` só ocorre após o commit
- [ ] 8.9 Testar a publicação de curva pronta: vértices idênticos aos recebidos, dígito a dígito
- [ ] 8.10 Testar a recusa por incoerência entre tipo de insumo e modo de origem
- [ ] 8.11 Testar a republicação de curva pronta gerando nova versão e preservando a anterior
- [ ] 8.12 Testar a dead-letter para curva sem mapeamento e para curva vazia
- [ ] 8.13 Escrever o teste de fronteira que falha se o serviço escrever fora das tabelas permitidas
- [ ] 8.14 Escrever a verificação estática que falha o build se `double` ou `float` for usado em valor de mercado
- [ ] 8.15 Testar blocos fora de ordem consolidando o lote corretamente
- [ ] 8.16 Testar falha em um bloco sem afetar os demais, com o lote seguindo aberto
- [ ] 8.17 Testar lote incompleto: sem evento de dado normalizado e sem pedido de construção
- [ ] 8.18 Testar que a faixa de rotina travada não impede o consumo da prioritária
- [ ] 8.19 Testar o mesmo lote chegando por duas faixas, com estado final idêntico e sem duplicata
- [ ] 8.20 Testar que mensagem que sempre falha avança o offset e vai para a dead-letter
- [ ] 8.21 Testar que falha de desserialização não trava a partição
- [ ] 8.22 Testar o teto de tempo na retentativa derivado do tempo até o horário limite
- [ ] 8.23 Testar a carga por CSV e por planilha produzindo resultado idêntico
- [ ] 8.24 Testar a listagem completa de erros por linha, sem aplicação parcial
- [ ] 8.25 Testar recusa por cabeçalho divergente, arquivo vazio e prazo duplicado
- [ ] 8.26 Testar a preservação de dígitos de uma taxa com doze casas decimais
- [ ] 8.27 Testar que curva carregada com defeito é reprovada pelo gate e não publica
- [ ] 8.28 Testar que carga reprovada preserva a versão anterior publicada
- [ ] 8.29 Testar a recusa de carga sem justificativa e por perfil de leitor
- [ ] 8.30 Testar que recarga do mesmo arquivo não cria versão duplicada

## 9. Integração

- [ ] 9.1 Rodar o processor contra o Kafka e o SQL Server locais, consumindo eventos reais do feeder B3
- [ ] 9.2 Confirmar uma data de pregão inteira persistida em `ponto_dado_mercado` a partir dos datasets de dado individual
- [ ] 9.3 Confirmar a curva oficial da mesma data publicada como curva `IMPORTED`, com proveniência completa
- [ ] 9.4 Documentar em `services/curve-processor/README.md` os parsers, o mapeamento de curvas importadas e a operação de dead-letter
