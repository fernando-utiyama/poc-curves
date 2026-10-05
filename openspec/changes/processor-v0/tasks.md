> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e as specs; não releia):
> 1. Antes de cada tarefa, leia **só** o cartão dela na seção 0.4 de [`implementacao.md`](implementacao.md) e as seções que o cartão cita. Leia a seção 0 do guia uma vez, no começo.
> 2. Abra só os arquivos que o cartão manda criar ou alterar; não explore o resto do repositório.
> 3. Não pare para perguntar: o guia já decidiu. Se faltar algo, deixe `// TODO(revisao): <dúvida>` no código e siga.
> 4. Cada tarefa termina com o "Pronto quando" do cartão (compilar ou o teste passar); corrija até passar e marque a tarefa.
> 5. Pare na tarefa **3.3** (PAUSA), escreva o resumo e espere a revisão. Depois da revisão, um novo `/opsx-apply` segue com 2.1 (B3), 4.1 e 4.2 (Bloomberg) e 5.x (bff e front).

Guia de implementação: [`implementacao.md`](implementacao.md). A seção 1 (base comum) vem primeiro e é feita por uma pessoa. Ao fim dela, as rotas das três fontes chegam ao caso de uso, e cada provedor responde 501 `PROVEDOR_NAO_IMPLEMENTADO`. Em seguida, a mesma pessoa faz a seção 3 (ANBIMA), que serve de modelo de provedor, e para na pausa 3.3. Depois da pausa, as seções 2 e 4 são independentes entre si e seguem o modelo da ANBIMA: cada dev implementa um provedor sem mexer no código dos outros. Testes poucos e amplos (guia, seção 12), com servidores falsos e portas em memória; o que exige infraestrutura real vai para a homologação (7.x). Nenhum script de banco.

## 1. Base comum (primeiro, uma pessoa)

- [ ] 1.1 Contrato hexagonal (guia, seções 1 e 2): `CargaArquivoUseCase`, modelo em `application/model/carga/` (`Fonte`, `OrigemCarga`, `ParametrosFonte`, `ArquivoObtido`, `IdentidadeCarga`, `CargaInterpretada`, `ResultadoCarga`, `CurvaPrimr` e os três tipos de vértice), as portas `ProvedorCargaPort`, `ArquivoOriginalPort`, `CurvaPrimrRepositoryPort`, `AvisoEnginePort`; erros da seção 3 (`CargaErrorCode`, `NotImplementedException`, `BadGatewayException` e os três métodos no `ApplicationExceptionHandler`); configuração da seção 4 (`CargaConfig`, propriedades, dependências do Azure); verificar com `mvn -q compile`
- [ ] 1.2 `CargaAPI`/`CargaController` com as três rotas e a fonte no caminho, validação, `X-Usuario` e o filtro de `X-Correlation-Id` (seção 5); verificar com `mvn -q compile`
- [ ] 1.3 Os três provedores provisórios (`B3DownloadSiteProvedor`, `AnbimaDownloadSiteProvedor`, `BloombergDataLicenseProvedor`) lançando `NotImplementedException`; escrever o `CargaRotasTest` (seção 12) e fazê-lo passar
- [ ] 1.4 `CargaArquivoService` com o roteiro da seção 5; escrever o `CargaArquivoServiceTest` (seção 12) e fazê-lo passar
- [ ] 1.5 `ArquivoOriginalBlobAdapter` (seção 6); verificar com `mvn -q compile` (Blob real só na homologação, 7.2)
- [ ] 1.6 `CurvaPrimrPersistenceAdapter` e as três `*CurvaPrimrInsercao` provisórias (seção 7); verificar com `mvn -q compile` (banco real só na homologação, 7.2)
- [ ] 1.7 `AvisoEngineService` e `AvisoEngineAdapter` (seção 8); escrever o `AvisoEngineServiceTest` (seção 12) e fazê-lo passar
- [ ] 1.8 Log da carga (seção 9); verificar com `mvn -q test` (tudo da seção 1 passando)

## 2. Provedor B3, download do site (depois da pausa 3.3; independente)

- [ ] 2.1 Pasta `adapter/out/client/b3/` e `BtrsCurvaPrimrInsercao`, copiando a estrutura da ANBIMA (guia, seção 11.1); escrever `B3CargaTest` e `LeiauteTaxaSwapTest` no molde dos da ANBIMA, com a DCL de `2026-09-14` (-117.9600000), arquivo ausente (503), data divergente (503), código com campo inválido e upload do `.ex_` com o mesmo `idCarga` do texto

## 3. Provedor ANBIMA, download do site (logo depois da seção 1; modelo dos provedores)

- [ ] 3.1 `LeiauteAnbimaMs` (seção 10.2, prazo em dias corridos, sem calendário); copiar `recursos/ms260928.txt` para `src/test/resources/anbima/`; escrever o `LeiauteAnbimaMsTest` (seção 12, vetores da seção 10.3) e fazê-lo passar
- [ ] 3.2 `AnbimaDownloadSiteProvedor`, `AnbimaMsClient` e `AnbmaCurvaPrimrInsercao` (seções 10.1 e 7); escrever o `AnbimaCargaTest` (seção 12) e fazê-lo passar
- [ ] 3.3 **PAUSA:** com 1.1 a 1.8, 3.1 e 3.2 prontas (ANBIMA gravando, B3 e Bloomberg respondendo 501), rodar `mvn compile` e `mvn test`; escrever um resumo curto (arquivos criados, arquivos existentes alterados, testes e resultado, `TODO(revisao)` deixados) e parar até a revisão; só depois a B3 (seção 2), a Bloomberg (seção 4) e o bff e o front (seção 5) começam

## 4. Provedor Bloomberg, Data License (depois da pausa 3.3; independente)

- [ ] 4.1 Pasta `adapter/out/client/bloomberg/` e `BbergCurvaPrimrInsercao`, copiando a estrutura da ANBIMA (guia, seção 11.2), com credencial do Key Vault e ticker completo em `cTickerBberg`; escrever `BloombergCargaTest` e `LeiauteRespostaDataLicenseTest` no molde dos da ANBIMA, com Data License falso: pedido novo, pedido já existente, resposta atrasada (503), ticker sem valor (503 no download, 422 no upload)
- [ ] 4.2 `TickersSofrReserva` com os 20 tickers e o `TODO(retirar)`, usada num único lugar do `CargaController` (`parametros`) quando `tickers` vem ausente ou vazio, com o log `TICKERS_RESERVA`; acrescentar ao `CargaRotasTest` a chamada sem `tickers`

## 5. bff e front (depois da pausa 3.3; independente dos provedores)

- [ ] 5.1 bff: `POST /api/v1/cargas/upload` (multipart, perfil de operação), repasse a `POST /api/v1/cargas/{fonte}/upload` do processor com `X-Usuario` e `X-Correlation-Id`, sem token, resposta do processor sem alteração, 503 com processor fora; verificar 401, 403, `fonte` inválida, arquivo grande e repasse sem `Authorization`
- [ ] 5.2 Front: tela "Carga manual de arquivo" (pt-BR, fonte, arquivo, aviso de substituição, botão desabilitado durante o envio, resultado com data `dd/mm/aaaa` e vértices por código, erro com mensagem e código); verificar no navegador o envio com sucesso, um arquivo rejeitado e o 501 de um provedor ainda não implementado

## 6. Fechamento

- [ ] 6.1 Rodar a suíte do processor e do bff; verificar que os testes existentes (consumidores Kafka) continuam passando
- [ ] 6.2 Rodar `openspec validate processor-v0 --strict`; verificar que passa

## 7. Homologação (infraestrutura real)

- [ ] 7.1 Conferir `cTickerBberg` `VARCHAR(50)` no banco (`001_SCRIPT_INICIAL.sql` atual); liberar a saída para B3, ANBIMA e Data License, o Blob e o Key Vault; cadastrar `tCurvaPrvdr` das 7 curvas (`design.md`, plano de migração)
- [ ] 7.2 Apontar os destinos do orquestrador para o processor, cadastrar as tarefas dos provedores prontos (sem agendar) e executá-las manualmente pelo orquestrador v0 (`orquestrador-v0-disparo-manual`) para hoje e para uma data passada; verificar originais no Blob, vértices nas tabelas e o engine construindo as curvas
- [ ] 7.3 Enviar pelo front um `TaxaSwap.txt` e um `ms` já carregados e conferir o mesmo `idCarga`; reprocessar uma data pelo orquestrador e conferir que nada é baixado
