## Why

Quando a fonte falha ou traz um valor errado, o gestor da curva precisa corrigir ou digitar os pontos da curva à mão, pelo front. Até aqui essa edição estava no engine. A decisão é separar ao máximo: o engine constrói, recalcula e interpola, e a edição manual dos pontos (`tDadoCurva`) fica no `services/curves`, junto com o cadastro (change `curves-cadastro-curvas`). A edição manual é contingência: não pode ser bloqueada nem pelo engine nem por uma dependência fora do ar, e tem preferência sobre a construção automática.

## What Changes

- **CRUD dos pontos de uma curva numa data-base** no `services/curves`: listar datas com pontos, consultar, gravar a lista completa (substituindo a atual) e apagar a data. O valor chega como string decimal e é arredondado pela configuração vigente.
- **Preferência da edição manual:**
  - sem `If-Match` e sem conferência de versão;
  - se o engine estiver construindo a mesma curva, a edição espera a transação dele e grava por cima;
  - pontos gravados à mão nunca são sobrescritos pela construção automática, só por um recálculo forçado por um usuário.
- **Nunca bloqueada por dependência:** sem configuração vigente, grava sem arredondar; com o engine fora, grava sem conferir feriados. Nos dois casos, a resposta traz um aviso. Só é recusado o que não pode ser gravado de forma consistente no banco (lista vazia, data ou valor malformado, data repetida, valor que não cabe na coluna); toda regra de negócio (fim de semana, feriado, data antes da data-base, preço não positivo) vira aviso, e o ponto é gravado.
- **Feriados pelo engine, só leitura:** o curves consulta a exportação de calendário do engine e avisa (`PONTO_EM_FERIADO`) quando um ponto cai em feriado, sem recusar; o engine trata o ponto na interpolação.
- **Mesma trava e mesmo `hashPontos` do engine**, pelo banco: nenhuma chamada de escrita entre os serviços.
- **Sem auditoria (contingência), com log** `PONTOS_EDITADOS`: usuário e `hashPontos` antes e depois.
- **Exportação e edição em lote por planilha:** exportar pontos de várias curvas e datas, editar, simular ponto a ponto e aplicar tudo numa transação.

## Capabilities

### New Capabilities
- `pontos-curva-manual`: CRUD manual dos pontos de uma curva numa data-base, validação, preferência sobre o engine, trava e `hashPontos` compartilhados, log.
- `pontos-curva-planilha`: exportação e importação em lote dos pontos por planilha, com simulação e aplicação atômica.

### Modified Capabilities
<!-- Nenhuma. -->

## Impact

- **services/curves:** rotas novas de pontos e de planilha de pontos; cliente HTTP do engine só para a exportação de calendário.
- **engine (`engine-modelos-curva`):** perde a edição de pontos; continua com construção, recálculo, consulta, interpolação e simulação. Na interpolação, trata pontos gravados em dia não útil: no mesmo prazo, fica o de menor data, com aviso.
- **Banco:** sem mudança de schema; o curves grava `tDadoCurva` e trava `tCurvaMercd` como o engine.
- **Depende de:** `curves-cadastro-curvas` (curva, configuração vigente, papéis e padrões de erro) e `engine-modelos-curva` (exportação de calendário e fórmula do `hashPontos`).
