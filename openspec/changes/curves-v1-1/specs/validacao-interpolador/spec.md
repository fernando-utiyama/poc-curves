## ADDED Requirements

### Requirement: Interpoladores aceitos iguais aos do engine
A validação da configuração de cálculo na curves SHALL aceitar os mesmos interpoladores que o engine: `Linear`, `FlatForward`, `BackwardFlat`, `ForwardFlat` e `Cubic`, sem `LogLinear`. O `FlatForward` SHALL ser aceito com qualquer `BASE_INTERPOLACAO` (com `Price`, é a interpolação de preços das curvas `INP` e `PTX`). `GET /api/v1/curvas-mercado/valores` SHALL listar os mesmos interpoladores.

#### Scenario: PTAX com preço
- **WHEN** o gestor cria a configuração da `PTX` com `interpolador` = `FlatForward` e `BASE_INTERPOLACAO` = `Price`
- **THEN** a configuração é aceita

#### Scenario: LogLinear recusado
- **WHEN** uma configuração chega com `interpolador` = `LogLinear`
- **THEN** a resposta é 422 `DADOS_INVALIDOS` no campo `interpolador`
