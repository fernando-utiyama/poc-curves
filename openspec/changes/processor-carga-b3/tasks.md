Guia de implementação passo a passo (arquivos, assinaturas, SQL, configuração e vetores de teste): [`implementacao.md`](implementacao.md). Cada tarefa abaixo corresponde a uma seção dele. O formato do aviso consumido e o caminho de recuperação (`b3/taxa-swap/reprocessamento`) são do change `conector-b3-webhook-ingest`, que é implantado junto com este.

## 1. Processor: leitura, parse e validação

- [ ] 1.1 Substituir o consumo de `tp-event-b3-curve` pelo aviso novo, com confirmação manual, `max.poll.records` = 1, `max.poll.interval.ms` de 30 minutos, todas as regras de validação da mensagem e propagação do `X-Correlation-Id`, registrando `CARGA_FALHOU` quando inválida; verificar mensagem válida, cada regra de rejeição, mensagem no formato antigo e mensagem sem `X-Correlation-Id`
- [ ] 1.2 Ler o arquivo do Blob por Managed Identity e conferir o tamanho e o SHA-256; verificar arquivo íntegro, arquivo inexistente, tamanho divergente e hash divergente
- [ ] 1.3 Usar o `LeiauteTaxaSwap` da `processor-v0` (ou criá-lo como o guia dela descreve, seção 11.1): parser do leiaute oficial em Java, com valor em `BigDecimal` direto do texto (escala 7); verificar a linha da DCL de `2026-09-14` (-117.9600000) e os 278 vértices das 5 curvas do `docs/TaxaSwap.txt` comparados ao arquivo
- [ ] 1.4 Implementar a validação: arquivo vazio, linha com tamanho diferente de 72, datas divergentes entre si ou da mensagem (rejeitam o arquivo), código vazio (rejeitam o arquivo), cada campo inválido da spec e dias corridos repetidos (rejeitam o código); verificar um teste por regra

## 2. Processor: gravação e aviso

- [ ] 2.1 Pelo `CurvaPrimrPersistenceAdapter` da `processor-v0` (com a `BtrsCurvaPrimrInsercao`), mapear cada código válido às curvas de mercado por `tCurvaPrvdr` (`B3`/`TS`/`cTickerPrvdr`) e gravar os vértices em `tBtrsCurvaPrimr` sob o nome de cada curva numa transação (travar a linha de cada curva em `tCurvaMercd` com `UPDLOCK, ROWLOCK` em ordem do nome e tempo limite de 60 s no comando, apagar por curva e data, inserir com o `cIdtfdUnic` gerado por `MAX + 1` com `UPDLOCK, HOLDLOCK`, conferir contagens antes do commit), com fatores nulos e sem escrever em `tCurvaMercd` nem `tCurvaPrvdr`; verificar com o acesso ao banco simulado os cenários da spec (114 códigos e 5 mapeados, código sem mapeamento, um código para duas curvas) e a falha no meio desfazendo a transação, e que o SQL da trava e o do `MAX + 1` são enviados ao banco
- [ ] 2.2 Pelo `AvisoEngineAdapter` da `processor-v0`, chamado de forma síncrona com a repetição da 3.1, chamar `POST /api/v1/cargas` do engine depois do commit, pelo endereço do serviço, com tempo limite de 150 segundos, com o corpo e o `X-Correlation-Id` da spec, sem `Authorization` e sem token (o engine não exige autenticação), uma entrada por código gravado, sem chamada quando nenhum foi gravado e com o resultado de cada curva no log; verificar 2xx e que a requisição não leva `Authorization`, engine fora por 3 minutos (`AVISO_ATRASADO` aos 2 minutos e aviso aceito ao voltar), tempo esgotado seguido de 409 e depois 200, e 4xx registrando `CARGA_FALHOU` sem repetir

## 3. Processor: robustez e limpeza

- [ ] 3.1 Implementar a repetição por janela de tempo (espera de 1 s dobrando até 60 s, com variação; 5 minutos para leitura e gravação, 10 minutos para o aviso, alerta `AVISO_ATRASADO` aos 2 minutos, tudo configurável) e o evento `CARGA_FALHOU` (log de erro e métrica) com `idCarga`, motivo, etapa, estado, tentativas e horário de Brasília, confirmando a mensagem em seguida; verificar cada etapa falhando dentro e além da janela, com as janelas reduzidas por configuração no teste, e a classificação de cada erro da spec como transitório ou definitivo; publicar as métricas da spec
- [ ] 3.2 Verificar a idempotência: a mesma mensagem duas vezes termina com as mesmas linhas e dois avisos com o mesmo `idCarga`; uma carga nova da mesma data substitui as linhas; e a curva ligada depois da carga (o reprocessamento da data grava a curva nova e o engine constrói só ela)
- [ ] 3.3 Reescrever o `B3KafkaConsumer` para o fluxo novo e remover `B3CurveRaw`, `B3CurveRawEntity`, `B3JpaRepository`, `B3CurveRepositoryAdapter`, `B3CurveRepositoryPort`, `ProcessB3CurveUseCase` e `ProcessB3CurveService`; verificar com `mvn compile` e busca sem referências a `mkt.B3CurveRaw`
- [ ] 3.4 Registrar o log JSON da carga (linhas lidas, códigos gravados com as curvas, ignorados, inválidos, duração por etapa, resposta do engine) no horário de Brasília; verificar os campos com um appender de teste

## 4. Testes (ao final da implementação)

- [ ] 4.1 Verificar a suíte existente do processor (`mvn test`), anotando o que quebrou e buscando referências que sobraram ao código removido (`implementacao.md` 2.1)
- [ ] 4.2 Adaptar os testes que continuam e remover os do código que saiu (`implementacao.md` 2.2)
- [ ] 4.3 Criar os testes novos do processor (`implementacao.md` 2.3)
- [ ] 4.4 Rodar `mvn verify`; verificar que tudo passa, inclusive os testes das outras fontes (`implementacao.md` 2.4)

## 5. Verificação ponta a ponta

- [ ] 5.2 Rodar `openspec validate processor-carga-b3 --strict` e a suíte do processor; verificar que tudo passa
