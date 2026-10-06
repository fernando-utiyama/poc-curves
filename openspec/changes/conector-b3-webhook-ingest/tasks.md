Guia de implementação passo a passo (arquivos, assinaturas, SQL, configuração e vetores de teste): [`implementacao.md`](implementacao.md). Cada tarefa abaixo corresponde a uma seção dele. Interpretar, validar e gravar o arquivo, e avisar o engine, é do change `processor-carga-b3`, que é implantado junto com este.

## 1. Conector: identidade, arquivamento e aviso

- [ ] 1.1 Converter o texto na forma canônica (linhas por `\r\n`, `\n` ou `\r`, sem linhas vazias, junção por `\n`, Latin-1, rejeitando caractere fora do Latin-1), calcular `hashArquivo` sobre ela, ler a data de geração (posições 12–19 da primeira linha não vazia) e gerar `idCarga`, interrompendo sem publicar se o arquivo for vazio ou a data inválida; verificar que o mesmo conteúdo com `\r\n` e com `\n` gera o mesmo `idCarga`, que um caractere diferente gera outro, e os casos de interrupção
- [ ] 1.2 Arquivar o arquivo em `b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt` com `If-None-Match: *`, tratando arquivo existente como sucesso; verificar com o cliente do Blob simulado a primeira gravação, a repetição (409) e a falha do Blob interrompendo antes da publicação
- [ ] 1.3 Publicar o aviso de carga em `tp-event-b3-curve` (no poc; no real, o tópico configurado em `spring.kafka.topics.b3.name`) com chave `B3-TS-{AAAAMMDD}`, produtor idempotente, `acks=all` e `geradoEm` no horário de Brasília; verificar o corpo contra a spec, que duas cargas da mesma data usam a mesma chave e que nenhuma mensagem por vértice é publicada

## 2. Conector: rotas

- [ ] 2.1 Renomear o `b3HttpTrigger` (hoje rota `swap-process`) para `b3TaxaSwapReprocessamentoHttpTrigger` (rota `b3/taxa-swap/reprocessamento`) e adaptá-lo: ler do Blob `b3/{AAAAMMDD}/TaxaSwap.txt` quando vier `dataBase`, ou `recebidos/TaxaSwap.txt` sem data (gravando a cópia de trabalho da data do arquivo e sem mexer em `recebidos`, criando `recebidos/.keep` na subida e a cada chamada sem data se a pasta não existir), sem `B3_SWAP_URL` e sem download, arquivar a cópia imutável e publicar com `origem` = `REPROCESSAMENTO`; tirar do caminho o parser, `normalizeCurveType`, `shouldPublishB3Curve` e `curveB3Factors`; verificar o processamento forçado de uma data gravada pelo `.ex_`, o arquivo colocado em `recebidos` sem data, a primeira execução com o container sem a pasta (cria `recebidos/.keep`), arquivo inexistente nos dois casos (404) e data divergente (422)
- [ ] 2.2 Renomear o gatilho do `.ex_` (`b3ContingencyHttpTrigger` → `b3TaxaSwapDownloadHttpTrigger`, `b3ContingencyTrigger` → `b3TaxaSwapDownloadTrigger`, `b3ContingencyHandler` → `b3TaxaSwapDownloadHandler`, rota `swap-contingency` → `b3/taxa-swap/download`, sem o termo "contingência" em nomes e logs) e fazê-lo gravar na pasta da data de geração do arquivo, arquivar e publicar com `origem` = `DOWNLOAD`; verificar o arquivo de `2026-09-14` obtido pela busca de dias anteriores num dia seguinte, e que não sobra `contingency` no código do conector
- [ ] 2.3 Criar `POST /api/b3/taxa-swap/upload` (`multipart/form-data`, campo `arquivo`, até 20 MB, `.txt` lido como Latin-1 ou `.ex_` extraído, `dataBase` opcional), com `origem` = `UPLOAD` e o usuário na mensagem; verificar upload de `.txt`, de `.ex_`, extensão inválida, arquivo acima do limite, data divergente e que o mesmo arquivo gera o mesmo `idCarga` pelo upload e pelo download
- [ ] 2.4 Aplicar o corpo de erro comum (`codigoErro`, mensagem em pt-BR, `correlationId`, `detalhes`) com os códigos da spec nas três rotas; verificar um teste por código, inclusive `DATA_BASE_DIVERGENTE` com as duas datas em `detalhes`
- [ ] 2.5 Registrar o log de execução com `correlationId`, `idCarga`, origem, usuário, data-base, hash, tamanho, caminho e duração no horário de Brasília; verificar os campos
- [ ] 2.6 Configurar `coverageThreshold` do Jest em 90% para os arquivos novos e alterados; verificar que `npm test -- --coverage` passa

## 3. Testes (ao final da implementação)

- [ ] 3.1 Verificar a suíte existente do conector (`npm test`), anotando o que quebrou e buscando referências que sobraram ao código removido (`implementacao.md` 2.1)
- [ ] 3.2 Adaptar os testes que continuam e remover os do código que saiu (`implementacao.md` 2.2)
- [ ] 3.3 Criar os testes novos do conector (`implementacao.md` 2.3)
- [ ] 3.4 Rodar `npm test -- --coverage`; verificar que tudo passa, com 90% de cobertura nos arquivos novos e alterados (`implementacao.md` 2.4)

## 4. Verificação ponta a ponta

- [ ] 4.2 Rodar `openspec validate conector-b3-webhook-ingest --strict` e a suíte do conector; verificar que tudo passa
