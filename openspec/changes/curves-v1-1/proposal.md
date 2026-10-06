## Why

A curves v1 (change `curves-cadastro-curvas`, já aplicada) tem o cadastro e os CRUDs do bruto, mas as telas novas do `fed` precisam de duas coisas que ela não tem (relatório `docs/inspecao/inspecao-curves.md` do poc): repassar ao engine as ações da tela Curvas, e a listagem do cadastro com provedor, dono e última execução. Esta v1.1 entrega só o mínimo para essas telas.

## What Changes

- **Repasse ao engine** para a tela Curvas: construir/recalcular (inclusive por origem secundária), regravar a interpolada, consultar os vértices e interpolar prazos, com 503 `ENGINE_INDISPONIVEL` quando o engine não responde.
- **Listagem do cadastro** com os provedores de cada curva, o dono e a última execução, e os filtros por provedor e por dono.
- **Dono da curva** (`cPprioDado`) gravado e devolvido no CRUD da curva.
- **Interpoladores iguais aos do engine:** sem `LogLinear`, e `FlatForward` aceito com qualquer base (as curvas `INP` e `PTX` usam `Price` + `FlatForward`).

Fica para a v2 (não entra aqui): rota `/dados-mercado` unificada, formato de erro com `correlationId`, decimais da ANBIMA e da Bloomberg como texto, instantes em `-03:00`, `avisos` em toda resposta, `PUT` do provedor que troca o provedor, `CURVA_COM_FILHAS`, correção retroativa de configuração, rótulos dos parâmetros em `/valores`, rota de pontos interpolados e segurança.

## Capabilities

### New Capabilities
- `repasse-engine`: rotas da curves que repassam ao engine as ações e consultas da tela Curvas.
- `listagem-curvas-v1-1`: provedores, dono e última execução na listagem e no CRUD da curva.
- `validacao-interpolador`: interpoladores aceitos na configuração iguais aos do engine.

### Modified Capabilities
<!-- Nenhuma em openspec/specs (não há specs arquivadas). -->

## Impact

- **services/curves:** cliente HTTP do engine (só repasse), controller e serviço das ações, `ENGINE_INDISPONIVEL`; consulta da listagem e mapeamento do `cPprioDado`.
- **engine:** nenhuma mudança aqui; o `situacao` na resposta da construção vem da change `engine-v1-1`.
- **Banco:** nenhuma mudança de schema.
