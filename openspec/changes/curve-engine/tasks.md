## 1. Módulo do kernel

- [ ] 1.1 Criar `libs/curve-kernel` como módulo Maven de biblioteca pura, Java 21, sem Spring, sem banco, sem rede
- [ ] 1.2 Adicionar a verificação de dependências que falha o build se o módulo declarar framework de aplicação ou artefato de repositório externo de curvas
- [ ] 1.3 Implementar as estruturas de curva: curva de juros, vértice e amostragem de taxa e fator de desconto
- [ ] 1.4 Implementar a validação de vértices: ordenação determinística e recusa de prazos duplicados
- [ ] 1.5 Implementar a política de arredondamento com truncamento de primeira classe e a potência decimal
- [ ] 1.6 Adicionar a verificação estática que falha o build se ponto flutuante for usado em valor de mercado

## 2. Convenções e calendário

- [ ] 2.1 Implementar o calendário de dias úteis B3/ANBIMA com feriados fixos e móveis reais
- [ ] 2.2 Implementar a falha explícita para data fora do intervalo coberto pelo calendário
- [ ] 2.3 Implementar as contagens de dias: dias úteis base 252, atual/360 e atual/365
- [ ] 2.4 Testar o calendário e as contagens contra datas reais conhecidas, incluindo feriado móvel

## 3. Interpoladores e extrapolação

- [ ] 3.1 Implementar flat forward, linear, log-linear e log-cúbico
- [ ] 3.2 Implementar spline cúbico natural, monotônico convexo e flat forward linear
- [ ] 3.3 Implementar o registro de interpoladores por identificador, com falha nomeada para identificador inexistente
- [ ] 3.4 Implementar as políticas de extrapolação estrita, taxa constante, forward constante e forward linear
- [ ] 3.5 Testar reprodutibilidade e o comportamento de cada política fora do intervalo dos vértices

## 4. Bootstrap e montagem da curva PRE

- [ ] 4.1 Implementar o algoritmo de bootstrap com falha explícita para conjunto insuficiente e para não convergência
- [ ] 4.2 Implementar os helpers de taxa por instrumento (CDI, DI1, inflação implícita)
- [ ] 4.3 Implementar a montagem da curva PRE a partir dos contratos DI1
- [ ] 4.4 Implementar o reconciliador contra curva de referência oficial, arredondando o oráculo pela política de produção e comparando exato
- [ ] 4.5 Reconciliar a curva PRE construída contra a taxa de referência publicada pela B3 para a mesma data, com fixture real
- [ ] 4.6 Verificar que nenhum teste compara um método contra o próprio delegado interno

## 5. Esqueleto do serviço

- [ ] 5.1 Criar `services/curve-engine` com Java 21, Spring Boot 3.4.x, sem Lombok, dependendo de `libs/curve-kernel`
- [ ] 5.2 Configurar o consumo Kafka do pedido de construção e a produção do evento de curva publicada
- [ ] 5.3 Configurar a fonte de dados com credencial restrita às tabelas da fronteira do serviço
- [ ] 5.4 Configurar o cliente Redis para o cache de interpolação
- [ ] 5.5 Escrever o `Containerfile` e adicionar o serviço ao compose Podman com healthcheck

## 6. Construção

- [ ] 6.1 Implementar o consumo de `curve.build.requested.v1` com propagação do `correlationId`
- [ ] 6.2 Implementar a resolução de definição, versão vigente na data e modelo apontado
- [ ] 6.3 Implementar a recusa para definição com modo de origem `IMPORTED`
- [ ] 6.4 Implementar o resolvedor de insumos a partir de `ponto_dado_mercado`, conforme a definição
- [ ] 6.5 Implementar o gate de insumo faltante, falhando nomeando índice ou instrumento e data
- [ ] 6.6 Implementar a validação dos vértices retornados: não vazio, prazos distintos, ordenáveis
- [ ] 6.7 Implementar a resolução de dependência entre curvas, com detecção de ciclo
- [ ] 6.8 Implementar o controle de construção concorrente da mesma curva, data e momento
- [ ] 6.9 Implementar a atualização de estado e duração na execução

## 7. Modelos de construção

- [ ] 7.1 Definir a interface interna de modelo de construção, implementada por embutidos e Groovy
- [ ] 7.2 Implementar o modelo embutido da curva PRE de DI1 da B3 e registrá-lo na inicialização
- [ ] 7.3 Implementar o catálogo de modelos com tipo, estado, checksum e autoria
- [ ] 7.4 Implementar a importação de modelo Groovy com validação de compilação e execução contra insumos de amostra
- [ ] 7.5 Implementar a contenção: classloader isolado, lista de permissão de pacotes, bloqueio de arquivo, rede e processo
- [ ] 7.6 Implementar os limites de tempo e de memória, com aborto nomeando a violação
- [ ] 7.7 Implementar a superfície somente leitura entregue ao modelo (insumos resolvidos + contexto)
- [ ] 7.8 Implementar a seleção do modelo pela versão de definição, com troca gerando nova versão da definição
- [ ] 7.9 Implementar a autorização de administrador para importar, desabilitar e trocar modelo
- [ ] 7.10 Implementar a comparação entre dois modelos para a mesma curva e data, sem publicar
- [ ] 7.11 Implementar o console local de desenvolvimento de modelo, desabilitado fora do perfil local

## 8. Validação de consistência

- [ ] 8.1 Criar o módulo de validação, sem dependência do código de orquestração do motor e testável sem banco nem broker
- [ ] 8.2 Definir a interface de teste de validação, com identificador, classificação, medida observada e limite aplicado
- [ ] 8.3 Implementar o teste estrutural: vértices presentes, prazos crescentes sem duplicata, valores não nulos, cobertura mínima
- [ ] 8.4 Implementar o teste de monotonicidade dos fatores de desconto
- [ ] 8.5 Implementar o teste de limites da taxa forward entre vértices adjacentes
- [ ] 8.6 Implementar o teste de faixa plausível de taxas
- [ ] 8.7 Implementar o teste de suavidade da estrutura a termo
- [ ] 8.8 Implementar a reprecificação dos instrumentos de calibração e a comparação com o observado
- [ ] 8.9 Implementar o teste de variação contra a última curva publicada em data anterior, com o caso de ausência como não aplicável
- [ ] 8.10 Implementar a comparação contra a curva importada da mesma data, tratando prazos sem contraparte
- [ ] 8.11 Implementar a classificação bloqueante/aviso por definição de curva
- [ ] 8.12 Implementar a leitura dos limites a partir da versão de definição, falhando quando um limite habilitado está ausente
- [ ] 8.13 Implementar a persistência do resultado em `validacao_curva`, para aprovação, reprovação e não aplicável
- [ ] 8.14 Implementar o orçamento de tempo da bateria e o registro da duração efetiva
- [ ] 8.15 Garantir que falha na execução da própria bateria nunca resulte em promoção

## 9. Publicação

- [ ] 9.1 Implementar a gravação atômica de versão, vértices e procedência em uma transação, com a versão em `EM_VALIDACAO`
- [ ] 9.2 Implementar a numeração incremental de versão por curva, data e momento
- [ ] 9.3 Implementar a promoção para `PUBLICADA` após o gate, e a marcação como `REPROVADA` quando um bloqueante falha
- [ ] 9.4 Implementar a marcação da versão anterior como substituída **apenas na promoção**, preservando seus vértices
- [ ] 9.5 Implementar a procedência completa, incluindo modelo e checksum do Groovy executado
- [ ] 9.6 Implementar a emissão de `curve.published.v1` após a promoção, com o mesmo contrato do processor
- [ ] 9.7 Implementar a idempotência de publicação por execução, sem criar versão duplicada em retentativa
- [ ] 9.8 Garantir que consulta de curva vigente nunca retorne versão em `EM_VALIDACAO` ou `REPROVADA`

## 10. Interpolação

- [ ] 10.1 Implementar a API síncrona de interpolação sobre versão publicada, sem reconstruir
- [ ] 10.2 Implementar a seleção de versão: corrente, por identificador e por `asOf`
- [ ] 10.3 Implementar a seleção de interpolador pela definição, com alternativa explícita sinalizada na resposta
- [ ] 10.4 Implementar o comportamento fora do intervalo conforme a política, com erro nomeado sob política estrita
- [ ] 10.5 Implementar a consulta em lote de prazos, preservando a ordem e isolando prazo inválido
- [ ] 10.6 Implementar o cache Redis com chave contendo o identificador de versão
- [ ] 10.7 Implementar a degradação para leitura direta do banco quando o cache estiver indisponível
- [ ] 10.8 Implementar a serialização de taxa e fator como texto numérico

## 11. Testes

- [ ] 11.1 Testar determinismo: mesma construção duas vezes produz vértices idênticos dígito a dígito
- [ ] 11.2 Testar o gate de insumo faltante para fixing ausente e conjunto de contratos incompleto
- [ ] 11.3 Testar que nenhum caminho preenche insumo por interpolação, repetição ou default
- [ ] 11.4 Testar a contenção: modelo que tenta rede, arquivo, processo, que estoura tempo e que lança exceção
- [ ] 11.5 Testar a importação recusando script que não compila e script que não produz vértices
- [ ] 11.6 Testar a troca de modelo com o serviço no ar, sem reinício, e a volta ao modelo embutido
- [ ] 11.7 Testar a publicação atômica revertendo por falha em vértices e por falha em procedência
- [ ] 11.8 Testar substituição da versão anterior com vértices preservados e unicidade da versão publicada
- [ ] 11.9 Testar interpolação em vértice exato, entre vértices e fora do intervalo nas duas políticas
- [ ] 11.10 Testar interpolação sobre curva importada com o mesmo formato de resposta da construída
- [ ] 11.11 Testar invalidação de cache por publicação de versão nova
- [ ] 11.12 Testar a recusa de construção e de publicação para definição `IMPORTED`
- [ ] 11.13 Testar a comparação entre modelos, incluindo o caso em que um dos modelos falha
- [ ] 11.14 Testar cada teste de validação com curva boa e com curva defeituosa construída de propósito
- [ ] 11.15 Testar que reprovação bloqueante impede a publicação e preserva a versão anterior
- [ ] 11.16 Testar que teste de aviso reprovado publica a curva com o aviso registrado
- [ ] 11.17 Testar que consulta de curva vigente ignora versão em `EM_VALIDACAO` e `REPROVADA`
- [ ] 11.18 Testar o caso de ausência de curva anterior e de curva importada como não aplicável
- [ ] 11.19 Testar que limite ausente na definição falha a validação em vez de assumir padrão
- [ ] 11.20 Testar que falha na execução da bateria nunca promove a versão

## 12. Integração

- [ ] 12.1 Rodar o motor contra Kafka, SQL Server e Redis locais, construindo a curva PRE de uma data real
- [ ] 12.2 Confirmar a reconciliação exata da curva construída contra a curva oficial da B3 da mesma data
- [ ] 12.3 Importar um modelo Groovy alternativo, apontar a curva para ele e comparar contra o modelo embutido
- [ ] 12.4 Publicar uma curva com defeito injetado e confirmar que o gate a reprova, com o resultado auditável
- [ ] 12.5 Documentar em `services/curve-engine/README.md` o ciclo de modelo, os limites da contenção, a bateria de validação e a API de interpolação
