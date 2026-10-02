Guia de implementação: [`implementacao.md`](implementacao.md). A seção 1 (base comum) vem primeiro e é feita por uma pessoa. Ao fim dela, as rotas das três fontes chegam ao caso de uso, e cada provedor responde 501 `PROVEDOR_NAO_IMPLEMENTADO`. As seções 2, 3 e 4 são independentes entre si: cada dev implementa um provedor sem mexer no código dos outros. Testes com mocks e servidores simulados; o que exige infraestrutura real vai para a homologação (7.x). Nenhum script de banco.

## 1. Base comum (primeiro, uma pessoa)

- [ ] 1.1 Criar o contrato hexagonal da seção 1 do guia: porta de entrada `CargaArquivoUseCase` (`baixar`, `reprocessar`, `receber`), modelo (`Fonte`, `OrigemCarga`, `ArquivoObtido`, `CargaInterpretada`, `ResultadoCarga`, linhas por fonte) e porta de saída `ProvedorCargaPort` (uma implementação por fonte), com as exceções da spec mapeadas para HTTP no handler que o repositório já tem; verificar com `mvn compile`
- [ ] 1.2 Criar os três controllers (`CargaB3Controller`, `CargaAnbimaController`, `CargaBloombergController`) com as nove rotas (download, reprocessamento e upload por fonte), sem autenticação, validação de data (não futura em Brasília), `tickers`, `X-Usuario` no upload, limite de 10 MB e `X-Correlation-Id`, todas chamando o `CargaArquivoUseCase`; verificar com MockMvc cada rota chegando ao caso de uso com os parâmetros certos e os 400 da spec
- [ ] 1.3 Criar os três provedores provisórios (`B3ProvedorCarga`, `AnbimaProvedorCarga`, `BloombergProvedorCarga`) lançando `ProvedorNaoImplementadoException` (501); verificar que as nove rotas respondem 501 com o corpo de erro da spec
- [ ] 1.4 `CargaArquivoService` (implementa o caso de uso): o roteiro da seção 3 do guia, igual para as três fontes, escolhendo o `ProvedorCargaPort` pela `Fonte`; verificar com provedor e portas simulados a ordem dos passos, 422 com o original arquivado, 503 sem arquivar, e a resposta 200 só depois da gravação
- [ ] 1.5 `ArquivoOriginalBlobAdapter`: gravação condicional (`If-None-Match: *`, já existe = sucesso) em `{fonte}/{AAAAMMDD}/cargas/{idCarga}/{arquivo}`, leitura do original mais recente da data e Managed Identity (string de conexão só local); identidade da carga (SHA-256, `idCarga` pelo prefixo da fonte); verificar gravação, repetição, Blob fora e reprocessamento sem original (404)
- [ ] 1.6 `CurvaPrimariaJdbcAdapter`, parte comum: mapeamento por `tCurvaPrvdr`, transação única, trava das curvas em `tCurvaMercd` (`UPDLOCK, ROWLOCK`, ordem do nome, 60 s), apagar por curva e data, `MAX + 1` com `UPDLOCK, HOLDLOCK`, conferência de contagens, com o `INSERT` de cada tabela num método por fonte, deixado vazio para o dev de cada provedor; verificar com o acesso ao banco simulado código sem curva, um código para duas curvas e falha no meio desfazendo tudo
- [ ] 1.7 Aviso ao engine depois do commit, em segundo plano, com repetição de até 10 min, `AVISO_ATRASADO` aos 2 min, `CARGA_FALHOU` no fim ou em 4xx diferente de 409, sem `Authorization`; verificar que a resposta da rota não espera o aviso, engine fora por 3 min e 409 seguido de 200
- [ ] 1.8 Log JSON da carga (fonte, data-base, `idCarga`, origem, usuário, caminho do original, códigos gravados, ignorados e rejeitados, duração por etapa, resultado do aviso) no horário de Brasília, sem segredo; e a configuração da seção 2 do guia; verificar os campos com um appender de teste

## 2. Provedor B3 (independente)

- [ ] 2.1 `B3ProvedorCarga` no lugar do provisório: cliente do `TS{AAMMDD}.ex_` (endereço da configuração), extração do zip em dois níveis (também no upload), forma canônica, data-base do conteúdo, parser e validação iguais à spec `b3-carga-processor` (reaproveitar as classes, se a `processor-carga-b3` já estiver feita), e o `INSERT` em `tBtrsCurvaPrimr`; verificar a DCL de `2026-09-14` (-117.9600000), arquivo ausente (503), data divergente (503), código com campo inválido e upload do `.ex_` com o mesmo `idCarga` do texto

## 3. Provedor ANBIMA (independente)

- [ ] 3.1 Copiar do engine as classes de calendário da seção 4 do guia do `engine-construcao-curvas` (`Calendario`, `Brazil`, `UnitedStates`, `CalendarioPorLista`), sem mudar regra; verificar os dias úteis de um vencimento contra a conta do engine
- [ ] 3.2 `AnbimaProvedorCarga` no lugar do provisório: cliente do `ms{AAMMDD}.txt`, parser por nome de coluna, filtro NTN-B com SELIC terminado em `99`, taxa `BigDecimal` da vírgula decimal (vazia ou `--` = nula), prazo em dias úteis até o vencimento ajustado por `Following`, e o `INSERT` em `tAnbmaCurvaPrimr`; verificar com o `ms260928.txt` real nos recursos de teste, 404 da ANBIMA (503), Principal ignorada e arquivo sem cabeçalho (422)

## 4. Provedor Bloomberg (independente)

- [ ] 4.1 `BloombergProvedorCarga` no lugar do provisório: cliente do Data License (pedido de histórico com identificador determinístico por data, hash dos tickers e faixa de 30 min; reaproveitamento do pedido já feito; espera de até 90 s; leitura do arquivo de resposta), credencial do Key Vault, ticker completo em `cTickerBberg` (depende do `ALTER` da `banco-curvas-ajustes`), carga só com todos os tickers com valor, e o `INSERT` em `tBbergCurvaPrimr`; verificar com servidor simulado pedido novo, pedido já existente, resposta atrasada (503), ticker sem valor (503 no download, 422 no upload)
- [ ] 4.2 Criar `TickersSofrReserva` com os 20 tickers e o `TODO` de retirada, usada numa única linha quando `tickers` vem ausente ou vazio, com o log `TICKERS_RESERVA`; verificar a chamada sem `tickers` e que apagar a classe só quebra essa linha

## 5. bff e front (depois da 1.2; independente dos provedores)

- [ ] 5.1 bff: `POST /api/v1/cargas/upload` (multipart, perfil de operação), repasse ao processor da fonte com `X-Usuario` e `X-Correlation-Id`, sem token, resposta do processor sem alteração, 503 com processor fora; verificar 401, 403, `fonte` inválida, arquivo grande e repasse sem `Authorization`
- [ ] 5.2 Front: tela "Carga manual de arquivo" (pt-BR, fonte, arquivo, aviso de substituição, botão desabilitado durante o envio, resultado com data `dd/mm/aaaa` e linhas por código, erro com mensagem e código); verificar no navegador o envio com sucesso, um arquivo rejeitado e o 501 de um provedor ainda não implementado

## 6. Fechamento

- [ ] 6.1 Rodar a suíte do processor e do bff; verificar que os testes existentes (consumidores Kafka) continuam passando
- [ ] 6.2 Rodar `openspec validate processor-emergencial-v0 --strict`; verificar que passa

## 7. Homologação (infraestrutura real)

- [ ] 7.1 Aplicar o `ALTER` de `cTickerBberg` (`banco-curvas-ajustes`); liberar a saída para B3, ANBIMA e Data License, o Blob e o Key Vault; cadastrar `tCurvaPrvdr` das 7 curvas (`design.md`, plano de migração)
- [ ] 7.2 Apontar os destinos do orquestrador para o processor, cadastrar as tarefas dos provedores prontos (sem agendar) e executá-las manualmente pelo orquestrador v0 (`orquestrador-v0-disparo-manual`) para hoje e para uma data passada; verificar originais no Blob, linhas nas tabelas e o engine construindo as curvas
- [ ] 7.3 Enviar pelo front um `TaxaSwap.txt` e um `ms` já carregados e conferir o mesmo `idCarga`; reprocessar uma data pelo orquestrador e conferir que nada é baixado
