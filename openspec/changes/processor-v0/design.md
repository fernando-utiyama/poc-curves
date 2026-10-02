## Context

A motivação está no proposal e o comportamento na spec. Estado atual:

- **Processor** (`services/processor`): só consome Kafka (`B3KafkaConsumer`, `AnbimaKafkaConsumer`, `BloombergKafkaConsumer` e outros) e grava em tabelas `mkt.*Raw`, que o engine não lê. Não baixa nada e não escreve no Blob.
- **Orquestrador** (`orquestrador-curvas`): três tarefas de carga (`carga-download-site` e `carga-data-license`) (B3, ANBIMA e Bloomberg) chamam um `destino` com `{dataBase}` e, na Bloomberg, `{tickers}`. 200 com a `dataBase` do dia é sucesso; 503, erro de rede ou tempo esgotado é "ainda não recebida", e ele tenta de novo; no `limiteHorario` gera `CARGA_NAO_RECEBIDA`. Tempo limite de 120 segundos por chamada.
- **Engine** (`engine-construcao-curvas`): lê `tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr` e `tBbergCurvaPrimr` pelo nome da curva e constrói ao receber `POST /api/v1/cargas`.
- **Fontes:** B3 `TS{AAMMDD}.ex_` (zip dentro de zip, `TaxaSwap.txt`); ANBIMA `ms{AAMMDD}.txt` (`@`, vírgula decimal, uma linha por título); Bloomberg Data License, pedido de histórico com os tickers `S0490Z <tenor> BLC2 Curncy`.

## Goals / Non-Goals

**Goals:**
- Insumo diário das 7 curvas sem depender das functions nem do Kafka.
- Original de cada carga no Blob, para auditoria e reprocessamento.
- Mesmas regras de leiaute, validação e gravação do caminho definitivo, para o código ser reaproveitado.
- Sair desta versão só trocando o `destino` das tarefas do orquestrador.

**Non-Goals:**
- Alterar ou remover os consumidores Kafka existentes (é da change `processor-carga-b3`).
- Curva Zero da ANBIMA (`CZ`), outras fontes (LSEG, CME, LCH, Treasury).
- Infraestrutura nova: tópico, fila, tabela ou coluna.
- Autenticação no processor: ele não autentica, como o engine e as functions; o bff autentica o upload e repassa o usuário.

## Decisions

### D1. Disparo por REST síncrono, no contrato das functions
O orquestrador dá uma ordem ("baixe a carga de tal data") e decide pela resposta: 200 encerra o dia, 503 tenta de novo, o limite gera alerta. Isso é uma chamada REST comum, não um webhook. Com as mesmas rotas e respostas que as functions terão, o orquestrador não muda código, e a troca para as functions é só o `destino`. **Alternativa rejeitada:** webhook ou mensagem para o processor, que não devolve ao orquestrador o "ainda não saiu" de que ele precisa para repetir e alertar.

### D2. Webhook só na saída, em segundo plano
O aviso ao engine é um evento ("carga gravada") e pode levar minutos (construção das curvas). Ele roda depois do commit, fora da resposta ao orquestrador, que nunca espera a construção. A repetição fica na memória da instância: se ela cair no meio, o aviso se perde, e a recuperação é o reprocessamento da data (os dados já estão gravados, e o engine é idempotente). É aceitável numa versão 0; o caminho definitivo (`processor-carga-b3`) resolve com o Kafka.

### D3. Bloomberg sem estado: pedido com identificador determinístico
O pedido de histórico do Data License é assíncrono e pode passar dos 120 segundos do orquestrador. Em vez de guardar o pedido em algum lugar, o processor monta o identificador com a data, o hash dos tickers e a faixa de 30 minutos: qualquer instância, na mesma faixa, reencontra o pedido pelo identificador e só consulta a resposta. Uma faixa nova gera um pedido novo, o que permite buscar de novo um valor que ainda não tinha saído. Na janela de 18h às 22h são no máximo 9 pedidos por dia (8 faixas, mais a ocorrência das 22h). **Alternativas rejeitadas:** guardar o pedido em tabela (infraestrutura nova) ou no Blob (o Blob guarda só originais); esperar a resposta na própria chamada (passa do tempo limite).

### D4. Lista de tickers cravada só como reserva, para sair depois
O parâmetro `tickers` da tarefa é a fonte da verdade. A lista cravada existe só para a chamada sem `tickers` (tarefa cadastrada antes do parâmetro, ou chamada manual). Fica numa única classe, `TickersSofrReserva`, marcada com `TODO` de retirada, e é usada numa única linha do serviço da Bloomberg. Retirar é apagar a classe e trocar essa linha por 400 `PARAMETRO_INVALIDO`.

### D5. Carga incompleta não grava
Seguindo `processor-carga-b3` (D7, rejeitar o arquivo ou o código, nunca a linha): na Bloomberg, um ticker sem valor deixa a SOFR com um tenor a menos, que parece normal. Por isso a carga só grava com todos os tickers com valor; senão, 503, e o orquestrador repete até o limite, quando alerta. Na B3, campo inválido rejeita só o código; na ANBIMA, taxa vazia grava nula, e o engine descarta o título com `SEM_TAXA`, como a spec dele define.

### D6. Prazo da NTN-B calculado no processor
`tAnbmaCurvaPrimr` guarda o prazo em dias úteis (`vVertcCurva`), e o arquivo `ms` traz só a data de vencimento. O processor conta os dias úteis até o vencimento ajustado por `Following`, com as classes de calendário copiadas do engine (seção 4 do guia do `engine-construcao-curvas`, como o orquestrador faz), sem chamar o engine. Um feriado decretado que o calendário nativo não conheça aparece no engine como `CALENDARIO_DIVERGENTE`, sem errar o vencimento.

### D7. Ticker completo na tabela Bloomberg
A v0 grava `cTickerBberg` com o ticker completo, como a Bloomberg publica (`S0490Z 15M BLC2 Curncy`, 22 caracteres). A coluna já é `VARCHAR(50)` no `001_SCRIPT_INICIAL.sql` (ajuste da change `banco-curvas-ajustes`, incorporado ao script). O modelo `SOFR_ZERO_BLOOMBERG` aceita o ticker completo. **Alternativa rejeitada:** gravar a forma curta `S0490Z 15M`, que caberia no `CHAR(20)` mas é diferente do que a fonte publica.

### D8. Identidade e Blob iguais ao caminho definitivo
`idCarga`, forma canônica do B3 e pastas no Blob seguem a spec `b3-taxaswap-publicacao`: o mesmo arquivo gera o mesmo `idCarga` aqui e, depois, no conector. Para ANBIMA e Bloomberg, a mesma estrutura com `anbima/` e `bloomberg/`, guardando os bytes como recebidos.

### D9. Duas instâncias sem coordenação
Nada depende de estado em memória além do aviso em segundo plano (D2). O orquestrador reivindica a tarefa antes de chamar (na v0, só execução manual; na v1, cada ocorrência), então normalmente só uma instância é chamada por vez para a mesma fonte. Se um upload coincidir com uma execução pelo orquestrador, a trava das curvas em `tCurvaMercd` serializa as gravações, e as duas gravam as mesmas linhas.

### D10. Upload pelo front, passando pelo bff
O upload é o plano B quando a fonte está fora ou o arquivo veio errado: o operador baixa o arquivo por outro meio e envia pela tela. O front fala só com o bff, que autentica e repassa o arquivo ao processor com `X-Usuario` (como o conector faz com o upload). No processor, o arquivo enviado segue o mesmo caminho do baixado: forma canônica (B3), identidade, original no Blob, parse, validação, gravação e aviso, com `origem` = `UPLOAD`. A data-base vem do conteúdo, não de um campo da tela, para não haver arquivo de um dia gravado em outro. No B3, aceita tanto o `TaxaSwap.txt` quanto o `.ex_` (reconhecido pelo cabeçalho de zip). Na Bloomberg, o arquivo é o de resposta do Data License; os tickers são os do arquivo, e qualquer ticker sem valor rejeita o envio (422), porque aqui não há "ainda não saiu". **Alternativa rejeitada:** front chamando o processor direto, que obrigaria o processor a autenticar.

### D11. Base comum primeiro, um provedor por dev
As rotas, o caso de uso (`CargaArquivoUseCase`, porta de entrada), o roteiro comum (Blob, identidade, gravação, aviso ao engine) e uma porta de saída por fonte (`ProvedorCargaPort`) são feitos primeiro. Cada fonte nasce com um provedor provisório que responde 501. Depois, cada dev implementa o provedor da sua fonte (cliente, leiaute e o `INSERT` da sua tabela) sem tocar no código das outras, e cada fonte entra em produção quando fica pronta. **Alternativa rejeitada:** um serviço inteiro por fonte, que repetiria três vezes o Blob, a trava, o `MAX + 1` e o aviso.

## Risks / Trade-offs

- **Aviso ao engine perdido se a instância cair.** → `CARGA_FALHOU` não chega a ser registrado; as curvas não constroem. Recuperação: reprocessar a data (D2). A tela de curvas mostra a curva sem pontos.
- **Custo e cota do Data License.** → No máximo um pedido por faixa de 30 minutos e por lista de tickers (D3).
- **Ticker inválido trava a carga Bloomberg.** → Intencional (D5); o 503 cita o ticker, e a correção é o `PATCH` em `tickers`.
- **Upload de arquivo arbitrário.** → Limite de 10 MB, extensão e conteúdo validados pelo parser da fonte, zip só no B3 com no máximo dois níveis e um único `TaxaSwap.txt`; o arquivo rejeitado fica no Blob como original, para auditoria.
- **Processor passa a falar com a internet.** → Endereços só da configuração, data e tickers validados (spec "Correlação, logs e segredos"); liberar no firewall só os três hosts.
- **Código duplicado com a futura function.** → O parser e a gravação ficam em classes próprias, reaproveitadas pela `processor-carga-b3`; só os clientes de download saem.

## Migration Plan

1. Conferir que o banco foi criado com o `001_SCRIPT_INICIAL.sql` atual (`cTickerBberg` `VARCHAR(50)`); se foi com a versão antiga, aplicar antes o `alter-banco-curvas.sql` da change `banco-curvas-ajustes`.
2. Liberar a saída do processor para `www.b3.com.br`, `www.anbima.com.br` e o host do Data License; dar ao processor escrita e leitura em `b3/`, `anbima/` e `bloomberg/` do Blob por Managed Identity; guardar a credencial do Data License no Key Vault.
3. Cadastro em `tCurvaPrvdr`: `B3`/`TS`/código para PRE, DCL, DPL, INP e PTX; `ANBIMA`/`MS`/`NTN-B` para a NTNB; `BLOOMBERG`/`BLC2`/`S0490Z` para a SOFR.
4. Implantar o processor.
5. No bff, configurar o endereço do processor e liberar a tela de upload para o perfil de operação.
6. No orquestrador, apontar `orquestrador.http.destinos.conector-b3`, `conector-anbima` e `conector-bloomberg` para o endereço do processor (tempo limite de 120 s) e cadastrar as três tarefas de `cadastros-sugeridos.txt` sem agendar: na v0 do orquestrador (change `orquestrador-v0-disparo-manual`) elas são executadas manualmente; o agendamento vem com a v1.
7. **Saída desta versão:** quando cada function existir, trocar o destino daquela fonte (e o do upload no bff); depois, apagar as rotas e os clientes de download e upload do processor e a classe `TickersSofrReserva`.
8. **Rollback:** parar de executar as tarefas no orquestrador e voltar o deploy do processor. Nada no schema muda; as linhas gravadas ficam e podem ser regravadas.

## Open Questions

- [A CONFIRMAR] Catálogo da conta do Data License, campo do valor (padrão `PX_LAST`) e formato do arquivo de resposta (padrão CSV com ticker, data e valor).
- [A CONFIRMAR] Faixas de horário das janelas (sugestão 18h às 22h nas três fontes).
- [A CONFIRMAR] Causa do `1D` repetido na lista recebida da SOFR; a reserva usa 20 tickers distintos.
