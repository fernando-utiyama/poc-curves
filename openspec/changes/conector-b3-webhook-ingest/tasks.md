Guia de implementação passo a passo (arquivos, assinaturas, SQL, configuração e vetores de teste): [`implementacao.md`](implementacao.md). Cada tarefa abaixo corresponde a uma seção dele.

## 1. Conector: identidade, arquivamento e aviso

- [ ] 1.1 Converter o texto na forma canônica (linhas por `\r\n`, `\n` ou `\r`, sem linhas vazias, junção por `\n`, Latin-1, rejeitando caractere fora do Latin-1), calcular `hashArquivo` sobre ela, ler a data de geração (posições 12–19 da primeira linha não vazia) e gerar `idCarga`, interrompendo sem publicar se o arquivo for vazio ou a data inválida; verificar que o mesmo conteúdo com `\r\n` e com `\n` gera o mesmo `idCarga`, que um caractere diferente gera outro, e os casos de interrupção
- [ ] 1.2 Arquivar o arquivo em `b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt` com `If-None-Match: *`, tratando arquivo existente como sucesso; verificar com o cliente do Blob simulado a primeira gravação, a repetição (409) e a falha do Blob interrompendo antes da publicação
- [ ] 1.3 Publicar o aviso de carga em `tp-event-b3-curve` (no poc; no real, o tópico configurado em `spring.kafka.topics.b3.name`) (`KAFKA_TOPIC`) com chave `B3-TS-{AAAAMMDD}`, produtor idempotente, `acks=all` e `geradoEm` no horário de Brasília; verificar o corpo contra a spec, que duas cargas da mesma data usam a mesma chave e que nenhuma mensagem por vértice é publicada

## 2. Conector: rotas

- [ ] 2.1 Renomear o `b3HttpTrigger` (hoje rota `swap-process`) para `b3TaxaSwapReprocessamentoHttpTrigger` (rota `b3/taxa-swap/reprocessamento`) e adaptá-lo: ler do Blob `b3/{AAAAMMDD}/TaxaSwap.txt` quando vier `dataBase`, ou `recebidos/TaxaSwap.txt` sem data (gravando a cópia de trabalho da data do arquivo e sem mexer em `recebidos`, criando `recebidos/.keep` na subida e a cada chamada sem data se a pasta não existir), sem `B3_SWAP_URL` e sem download, arquivar a cópia imutável e publicar com `origem` = `REPROCESSAMENTO`; tirar do caminho o parser, `normalizeCurveType`, `shouldPublishB3Curve` e `curveB3Factors`; verificar o processamento forçado de uma data gravada pelo `.ex_`, o arquivo colocado em `recebidos` sem data, a primeira execução com o container sem a pasta (cria `recebidos/.keep`), arquivo inexistente nos dois casos (404) e data divergente (422)
- [ ] 2.2 Renomear o gatilho do `.ex_` (`b3ContingencyHttpTrigger` → `b3TaxaSwapDownloadHttpTrigger`, `b3ContingencyTrigger` → `b3TaxaSwapDownloadTrigger`, `b3ContingencyHandler` → `b3TaxaSwapDownloadHandler`, rota `swap-contingency` → `b3/taxa-swap/download`, sem o termo "contingência" em nomes e logs) e fazê-lo gravar na pasta da data de geração do arquivo, arquivar e publicar com `origem` = `DOWNLOAD`; verificar o arquivo de `2026-09-14` obtido pela busca de dias anteriores num dia seguinte, e que não sobra `contingency` no código do conector
- [ ] 2.3 Criar `POST /api/b3/taxa-swap/upload` (`multipart/form-data`, campo `arquivo`, até 20 MB, `.txt` lido como Latin-1 ou `.ex_` extraído, `dataBase` opcional), com `origem` = `UPLOAD` e o usuário na mensagem; verificar upload de `.txt`, de `.ex_`, extensão inválida, arquivo acima do limite, data divergente e que o mesmo arquivo gera o mesmo `idCarga` pelo upload e pelo download
- [ ] 2.4 Trocar o acesso anônimo pela autenticação do Entra ID nas três rotas B3 (autenticação do App Service e conferência do papel no handler, sem chave de função); verificar 401 sem token e 403 sem papel
- [ ] 2.5 Aplicar o corpo de erro comum (`codigoErro`, mensagem em pt-BR, `correlationId`, `detalhes`) com os códigos da spec nas três rotas; verificar um teste por código, inclusive `DATA_BASE_DIVERGENTE` com as duas datas em `detalhes`
- [ ] 2.6 Registrar o log de execução com `correlationId`, `idCarga`, origem, usuário, data-base, hash, tamanho, caminho e duração no horário de Brasília; verificar os campos
- [ ] 2.7 Configurar `coverageThreshold` do Jest em 90% para os arquivos novos e alterados; verificar que `npm test -- --coverage` passa

## 3. Processor: leitura, parse e validação

- [ ] 3.1 Substituir o consumo de `tp-event-b3-curve` pelo aviso novo, com confirmação manual, `max.poll.records` = 1, `max.poll.interval.ms` de 20 minutos, todas as regras de validação da mensagem e propagação do `X-Correlation-Id`, registrando `CARGA_FALHOU` quando inválida; verificar mensagem válida, cada regra de rejeição, mensagem no formato antigo e mensagem sem `X-Correlation-Id`
- [ ] 3.2 Ler o arquivo do Blob por Managed Identity e conferir o tamanho e o SHA-256; verificar arquivo íntegro, arquivo inexistente, tamanho divergente e hash divergente
- [ ] 3.3 Criar o parser do leiaute oficial em Java, com valor em `BigDecimal` direto do texto (escala 7); verificar a linha da DCL de `2026-09-14` (-117.9600000) e os 278 vértices das 5 curvas do `docs/TaxaSwap.txt` comparados ao arquivo
- [ ] 3.4 Implementar a validação: arquivo vazio, linha com tamanho diferente de 72, datas divergentes entre si ou da mensagem (rejeitam o arquivo), código vazio (rejeitam o arquivo), cada campo inválido da spec e dias corridos repetidos (rejeitam o código); verificar um teste por regra

## 4. Processor: gravação e aviso

- [ ] 4.1 Mapear cada código válido às curvas de mercado por `tCurvaPrvdr` (`B3`/`TS`/`cTickerPrvdr`) e gravar os vértices em `tBtrsCurvaPrimr` sob o nome de cada curva numa transação (apagar por curva e data, inserir com o `cldtfdUnic` gerado como no sistema real (a confirmar), conferir contagens antes do commit), com fatores nulos e sem escrever em `tCurvaMercd` nem `tCurvaPrvdr`; verificar com o acesso ao banco simulado os cenários da spec (110 códigos e 5 mapeados, código sem mapeamento, um código para duas curvas) e a falha no meio desfazendo a transação; a transação real é conferida na homologação
- [ ] 4.2 Chamar `POST /api/v1/cargas` do engine depois do commit, pelo endereço do serviço, com token do Entra ID (client credentials, `Curvas.Processor`) e tempo limite de 150 segundos, com o corpo e os cabeçalhos da spec (`Authorization`, `X-Correlation-Id`), uma entrada por código gravado, sem chamada quando nenhum foi gravado e com o resultado de cada curva no log; verificar 2xx, engine fora por 3 minutos (`AVISO_ATRASADO` aos 2 minutos e aviso aceito ao voltar), tempo esgotado seguido de 409 e depois 200, e 4xx registrando `CARGA_FALHOU` sem repetir

## 5. Processor: robustez e limpeza

- [ ] 5.1 Implementar a repetição por janela de tempo (espera de 1 s dobrando até 60 s, com variação; 5 minutos para leitura e gravação, 10 minutos para o aviso, alerta `AVISO_ATRASADO` aos 2 minutos, tudo configurável) e o evento `CARGA_FALHOU` (log de erro e métrica) com `idCarga`, motivo, etapa, estado, tentativas e horário de Brasília, confirmando a mensagem em seguida; verificar cada etapa falhando dentro e além da janela, com as janelas reduzidas por configuração no teste, e a classificação de cada erro da spec como transitório ou definitivo; publicar as métricas da spec
- [ ] 5.2 Verificar a idempotência: a mesma mensagem duas vezes termina com as mesmas linhas e dois avisos com o mesmo `idCarga`; uma carga nova da mesma data substitui as linhas; e a curva ligada depois da carga (o reprocessamento da data grava a curva nova e o engine constrói só ela)
- [ ] 5.3 Reescrever o `B3KafkaConsumer` para o fluxo novo e remover `B3CurveRaw`, `B3CurveRawEntity`, `B3JpaRepository`, `B3CurveRepositoryAdapter`, `B3CurveRepositoryPort`, `ProcessB3CurveUseCase` e `ProcessB3CurveService`; verificar com `mvn compile` e busca sem referências a `mkt.B3CurveRaw`
- [ ] 5.4 Registrar o log JSON da carga (linhas lidas, códigos gravados com as curvas, ignorados, inválidos, duração por etapa, resposta do engine) no horário de Brasília; verificar os campos com um appender de teste

## 6. Testes (ao final da implementação)

- [ ] 6.1 Verificar as suítes existentes do conector e do processor, anotando o que quebrou e buscando referências que sobraram ao código removido (`implementacao.md` 3.1)
- [ ] 6.2 Adaptar os testes que continuam e remover os do código que saiu (`implementacao.md` 3.2)
- [ ] 6.3 Criar os testes novos do conector e do processor (`implementacao.md` 3.3)
- [ ] 6.4 Rodar `npm test -- --coverage` e `mvn verify`; verificar que tudo passa, com 90% de cobertura nos arquivos novos e alterados do conector

## 7. Verificação ponta a ponta

- [ ] 7.1 Na homologação, com Kafka, Blob e SQL Server do projeto: obter o arquivo pelo `.ex_` (B3 simulada), depois forçar o processamento da mesma data pelo `b3/taxa-swap/reprocessamento` e enviar o mesmo conteúdo pelo upload; verificar um único `idCarga`, 278 linhas para cada uma das 5 curvas em `tBtrsCurvaPrimr`, valores iguais ao arquivo e três avisos idênticos ao engine simulado
- [ ] 7.2 Rodar `openspec validate conector-b3-webhook-ingest --strict` e as suítes do conector e do processor; verificar que tudo passa
