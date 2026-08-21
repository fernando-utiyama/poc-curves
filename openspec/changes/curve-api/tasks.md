## 1. Esqueleto do serviço

- [ ] 1.1 Criar o módulo Maven `services/curve-api` com Java 21, Spring Boot 3.4.x, sem Lombok
- [ ] 1.2 Configurar a fonte de dados com credencial de escrita apenas em `definicao_curva` e `versao_definicao_curva`, e leitura no restante
- [ ] 1.3 Configurar o cliente HTTP do motor para a interpolação, com timeout e tratamento de indisponibilidade
- [ ] 1.4 Escrever o `Containerfile` e adicionar o serviço ao compose Podman com healthcheck

## 2. Contrato

- [ ] 2.1 Escrever `contracts/openapi/curve-api.yaml` com todos os recursos de cadastro e consulta
- [ ] 2.2 Declarar no contrato que valores de mercado trafegam como texto numérico
- [ ] 2.3 Implementar o teste de conformidade entre implementação e contrato
- [ ] 2.4 Configurar a serialização de `BigDecimal` como texto em todos os DTOs

## 3. Cadastro de curva

- [ ] 3.1 Implementar a criação de definição com código imutável, nome, moeda, modo de origem e estado
- [ ] 3.2 Implementar a criação da primeira versão de definição junto com a definição
- [ ] 3.3 Implementar a edição gerando nova versão de definição com número incremental e vigência
- [ ] 3.4 Implementar a recusa de alteração de código
- [ ] 3.5 Implementar o ciclo de vida rascunho → ativa → aposentada
- [ ] 3.6 Implementar a resposta identificando a versão criada e a versão de origem

## 4. Validações de cadastro

- [ ] 4.1 Validar coerência entre modo de origem e vínculos de fonte declarados
- [ ] 4.2 Validar existência e habilitação do modelo apontado, e recusar modelo em definição importada
- [ ] 4.3 Aplicar o modelo embutido padrão quando a definição construída não indicar modelo
- [ ] 4.4 Validar interpolador, política de extrapolação e política de arredondamento contra os suportados
- [ ] 4.5 Validar existência das curvas declaradas como dependência
- [ ] 4.6 Implementar a detecção de ciclo de dependência, descrevendo o ciclo na mensagem

## 5. Modelo de carga e limites de validação

- [ ] 5.1 Persistir no cadastro o horário limite, o orçamento por etapa e a janela de bloqueio
- [ ] 5.2 Persistir os limites de cada teste de validação e a classificação bloqueante/aviso
- [ ] 5.3 Recusar o cadastro quando um teste habilitado não tem limite declarado
- [ ] 5.4 Gerar o modelo de carga em CSV a partir da definição, com cabeçalho e prazos esperados
- [ ] 5.5 Gerar o modelo equivalente em planilha, com o mesmo leiaute
- [ ] 5.6 Refletir no modelo as alterações de convenção e de prazos da definição

## 6. Consulta de curva

- [ ] 6.1 Implementar a consulta por versão corrente, com a versão usada sempre identificada na resposta
- [ ] 6.2 Implementar a consulta por identificador de versão explícito
- [ ] 6.3 Implementar a consulta por `asOf`, indicando que a seleção foi por instante
- [ ] 6.4 Implementar a resposta explícita de ausência quando não há versão publicada na data
- [ ] 6.5 Garantir contrato de resposta idêntico para curva construída e importada, com o modo indicado

## 7. Vértices e interpolação

- [ ] 7.1 Implementar a listagem de vértices ordenada por prazo, com paginação
- [ ] 7.2 Resolver a versão uma vez e manter a paginação estável contra ela
- [ ] 7.3 Implementar a consulta de curva interpolada delegando ao motor
- [ ] 7.4 Distinguir na resposta motor indisponível de curva inexistente
- [ ] 7.5 Propagar o erro nomeado de prazo fora do intervalo sob política estrita

## 8. Histórico, procedência e comparação

- [ ] 8.1 Implementar o histórico de versões com estado, publicação, execução e modelo
- [ ] 8.2 Implementar a consulta de procedência de curva construída
- [ ] 8.3 Implementar a consulta de procedência de curva importada, com lote e arquivo de origem
- [ ] 8.4 Implementar a comparação entre duas curvas da mesma data, prazo a prazo
- [ ] 8.5 Sinalizar prazos presentes em apenas uma das curvas, sem preencher por interpolação
- [ ] 8.6 Informar explicitamente quando uma das curvas não tem publicação na data

## 9. Consulta do catálogo

- [ ] 9.1 Implementar a listagem de definições com filtro por código, nome, moeda, modo de origem e estado
- [ ] 9.2 Trazer a versão de definição vigente e o modelo apontado em cada linha
- [ ] 9.3 Implementar paginação e ordenação do catálogo

## 10. Segurança e observabilidade

- [ ] 10.1 Implementar a autorização: cadastro exige administrador; consulta exige apenas autenticação
- [ ] 10.2 Propagar o `correlacao_id` recebido em log e nas chamadas ao motor
- [ ] 10.3 Implementar log estruturado em JSON
- [ ] 10.4 Expor endpoints de saúde e de métricas

## 11. Testes

- [ ] 11.1 Testar que edição cria versão nova e preserva a anterior
- [ ] 11.2 Testar que curva publicada continua apontando para a versão sob a qual foi construída
- [ ] 11.3 Testar as validações de coerência: modo × vínculos, modelo inexistente, modelo desabilitado, modelo em curva importada
- [ ] 11.4 Testar a detecção de ciclo de dependência
- [ ] 11.5 Testar as três formas de seleção de versão, incluindo `asOf` durante republicação
- [ ] 11.6 Testar paginação estável de vértices com publicação concorrente
- [ ] 11.7 Testar a serialização de valores como texto numérico, sem perda de dígito
- [ ] 11.8 Testar a delegação da interpolação e o comportamento com motor indisponível
- [ ] 11.9 Testar a comparação com prazos não coincidentes e com uma das curvas ausente
- [ ] 11.10 Testar o contrato OpenAPI contra a implementação
- [ ] 11.11 Testar a fronteira de escrita do serviço
- [ ] 11.12 Testar a autorização por perfil em cadastro e consulta
- [ ] 11.13 Testar a recusa de cadastro com teste habilitado sem limite
- [ ] 11.14 Testar que o modelo gerado reflete as convenções e os prazos da curva
- [ ] 11.15 Testar que CSV e planilha do modelo têm o mesmo leiaute
- [ ] 11.16 Testar que toda resposta de versão informa a origem

## 12. Integração

- [ ] 12.1 Cadastrar pelo API as duas definições da POC — PRE construída de DI1 e PRE oficial importada
- [ ] 12.2 Consultar as duas curvas publicadas de uma data real e comparar uma contra a outra
- [ ] 12.3 Consultar a procedência de cada uma e conferir a diferença de conteúdo entre os dois modos
- [ ] 12.4 Documentar em `services/curve-api/README.md` os recursos, os modos de seleção de versão e a comparação
