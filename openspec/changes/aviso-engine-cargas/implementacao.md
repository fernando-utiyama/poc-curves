# Guia de implementação: aviso-engine-cargas (conferência, não reescrita)

> Conferência por funcionalidade, pelo nome que existir no repositório real: para cada item, **manter | alterar | criar**. Pacote, exceções, log e Jackson seguem o repo real. Sem `mvn test` na conferência; compilar depois de alterar. Hexagonal: o serviço usa só portas; o adapter HTTP fica em `adapter/out/client/engine`. Um tipo por arquivo; comentários de uma linha.

## 1. Aviso no processor

Corpo (`POST {engine.base-url}/api/v1/cargas`): `{ "idCarga", "fonte", "produto", "dataBase", "verticesPorCurva" }`; cabeçalho `X-Correlation-Id`; sem `Authorization`.

- `avisarEmSegundoPlano(...)`: chamado depois do commit, fora da transação, em virtual thread; retorna na hora.
- Repetir em erro de rede, 5xx e 409: espera inicial 1 s dobrando até 60 s, variação de até 20%, janela de 10 min, tempo limite de 150 s por chamada.
- Aos 2 min sem 2xx: log `AVISO_ATRASADO` (uma vez). No fim da janela, ou em 4xx diferente de 409: log de erro `CARGA_FALHOU` (idCarga, fonte, dataBase) e `counter("processor.carga.falhou", "fonte", fonte).increment()`.
- 2xx: logar o corpo devolvido.

## 2. `verticesPorCurva` por fonte

| Fonte | Chave (nome da curva) | Quantidade |
|---|---|---|
| B3 (`TS`) | `PRE`, `DCL`, `DPL`, `INP`, `PTX` | vértices gravados da curva |
| ANBIMA (`MS`) | `NTN-B` | títulos gravados na curva |
| Bloomberg (`BLC2`) | `SOFR` | 20 nós |

O código da fonte só acha a curva em `tCurvaPrvdr`; no aviso vai o nome da curva.

Contar o gravado, não o que sobra depois do modelo. Não avisar com mapa vazio.

## 3. Teste do aviso (`AvisoEngineServiceTest`, engine falso)

2xx de primeira; 503 três vezes e depois 200; 409 repetido; 400 sem repetir (`CARGA_FALHOU`); janela esgotada (`CARGA_FALHOU` e contador da fonte); aviso da ANBIMA em repetição não atrasa o da B3.

## 4. Engine: conferir a quantidade

No tratamento da carga (pela inspeção, `ProcessarCargaService`, onde `linhasAvisadas` só é repassada):

```java
long lidas = leitura.contar(curva, dataBase);          // linhas da tabela bruta
int avisadas = verticesPorCurva.get(curva.codigo());   // codigo() = nome da curva
if (lidas != avisadas) {
    throw new CurvaBusinessException(CurvaErrorCode.INSUMO_INVALIDO,
        "Quantidade de vértices diferente do aviso: avisadas " + avisadas + ", lidas " + lidas + ".");
}
```

A exceção é por curva: as outras da carga seguem. Nomes de classe e de erro: os do repo real.

Teste: quantidade igual constrói; diferente devolve `INSUMO_INVALIDO` só para aquela curva.
