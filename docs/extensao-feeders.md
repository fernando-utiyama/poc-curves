# Ponto de extensão: novos feeders (ANBIMA, Bloomberg, LSEG)

`services/feeder-marketdata` hoje tem feeders reais de B3 (`PR_DI1`,
`BVBG.086`, `BVBG.028` — ver `src/feeders/b3-arquivo-pesquisa-pregao.ts`),
mas o contrato foi desenhado desde o início para acomodar outras fontes sem
mudar nada fora do próprio feeder novo. Este documento descreve o que um
feeder de uma fonte nova (ANBIMA, Bloomberg, LSEG, ou qualquer outra)
precisa implementar.

## O contrato: interface `Feeder`

Definida em `services/feeder-marketdata/src/feeder.ts`:

```ts
export interface Feeder {
  acquire(params: AcquisitionParams): Promise<AcquisitionResult>;
}
```

- `AcquisitionParams` (`source`, `dataset`, `referenceDate`, `faixa`,
  `correlationId`) chega pronto — quem dispara a aquisição já resolve isso.
- `AcquisitionResult` é uma union discriminada de três estados, e o feeder
  novo é quem decide qual devolver a cada chamada:
  - `PUBLISHED` — adquiriu e publicou no Kafka com sucesso.
  - `NO_DATA` — a fonte respondeu, mas não há dado para a data pedida (não é
    erro: dia sem pregão, fonte ainda não divulgou, etc.).
  - `FAILED` — falha real (rede, parsing, integridade), com `motivo` (resumo)
    e `diagnostico` (detalhe técnico para investigação).

Um feeder novo não implementa nada além disto. Tudo que já existe no núcleo
do serviço é reutilizável e não precisa ser reescrito:

| Peça já pronta | Onde | O que resolve |
|---|---|---|
| Cliente HTTP com retentativa | `src/http-client.ts` | timeout, backoff exponencial com jitter, retentativa só para falha de transporte |
| Verificação de integridade | `src/integridade.ts` | conteúdo vazio, tamanho declarado, arquivo compactado corrompido |
| Quebra em blocos | `src/blocos.ts`, `src/xml-estrutural.ts` | corte estrutural de arquivos grandes, com `loteId`/`sequencia`/`totalBlocos` |
| Identificadores determinísticos | `src/lote-id.ts` | `loteId` e `eventId` — mesmo conteúdo produz o mesmo id, sempre |
| Produtor Kafka | `src/kafka-publisher.ts` | monta o envelope, resolve tópico/chave de partição pela faixa, valida contra o schema (ajv) antes de publicar |
| Roteador de datasets | `src/registro-feeders.ts` | expõe `RegistroFeeders.registrar(dataset, feeder)` — o feeder novo só se registra ali |

## O que o feeder novo precisa decidir sozinho

1. **Como buscar o dado na fonte** — chamada HTTP, arquivo, ou outro
   transporte (ver "Modo de entrega" abaixo). Isto é específico da fonte;
   nada no núcleo assume HTTP.
2. **Como extrair os registros do formato bruto da fonte** e entregá-los como
   uma lista para `dividirEmBlocos`/`dividirXmlEmBlocos` — corte sempre
   estrutural ("este arquivo tem N registros"), nunca semântico.
3. **Qual `payloadKind` declarar** em cada evento: `INDIVIDUAL_QUOTES` para
   cotações/contratos individuais, `READY_CURVE` para curva pronta —
   depende do endpoint/dataset consultado, não é uma escolha livre por feeder.
4. **`source`** no envelope: o tipo `EventSource` (`src/envelope.ts`) já
   inclui `'ANBIMA'`, `'BLOOMBERG'` e `'LSEG'` além de `'B3'` e `'MANUAL'` —
   não precisa de mudança de contrato para declarar a origem.
5. **Registrar-se** no `RegistroFeeders` por nome de dataset, com
   `registrar(dataset, feeder)` — falha cedo e nomeada
   (`DatasetNaoSuportadoError`) se alguém pedir um dataset sem feeder.

## Modo de entrega

O contrato atual (`Feeder.acquire`) é *pull agendado*: alguém chama
`acquire()` e espera a resposta. Isso cobre bem uma fonte que expõe arquivo
ou API para busca sob demanda. Se LSEG entregar por **stream** (push
contínuo, não sob demanda), o contrato de feeder atual não cobre esse modo —
precisaria de um adaptador adicional, não coberto por este documento. Isto
continua registrado como pergunta em aberto em
`openspec/changes/curves-solution-architecture/design.md`
("Bloomberg e LSEG entregam por arquivo, API ou stream?") — **respondido
para Bloomberg** (ver abaixo); **LSEG continua em aberto**, não presuma qual
modo será usado.

**Bloomberg — respondido**: entrega por **arquivo**, mas em fluxo
**assíncrono** (submeter pedido → aguardar geração em lote → buscar arquivo
pronto), o padrão real do Bloomberg Data License. Isso não exigiu mudar o
contrato `Feeder` — a espera acontece inteira dentro do `acquire()` (que já
devolve uma `Promise`, então já suportava esperar o tempo que fosse
necessário). A única restrição real: esse feeder só roda pelo caminho
CLI/agendado (`main.ts`), nunca pelo disparo HTTP síncrono onde um operador
aguarda a resposta na tela — ver `src/feeders/bloomberg/feeder-bloomberg.ts`
e a mudança `feeder-bloomberg` (`openspec/changes/feeder-bloomberg/`) para o
desenho completo, incluindo a ressalva de que o transporte HTTP real
(endpoints, formato) não foi verificado contra um ambiente Bloomberg real.

## O que este documento não cobre

Nenhum detalhe de autenticação, formato de arquivo, ou endpoint real de
Bloomberg/LSEG — nada disso foi verificado ainda contra documentação real
dessas fontes. Quando uma integração real for iniciada, o primeiro passo é o
mesmo que foi feito para a B3: obter documentação/exemplo real da fonte antes
de escrever qualquer parsing, para não fabricar suposições sobre formato.
