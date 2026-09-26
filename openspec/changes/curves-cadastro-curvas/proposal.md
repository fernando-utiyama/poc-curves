## Why

O engine só lê o cadastro das curvas (`tCurvaMercd`, `tCurvaPrvdr` e `tConfgCurva` com os parâmetros em `cModDado`), e o processor só lê as ligações em `tCurvaPrvdr` para saber sob qual curva gravar os dados brutos. Ninguém cria nem mantém esse cadastro: no poc ele foi inserido por SQL e por migrations. No sistema real, o cadastro é do serviço de curvas (`acts-srv-curvas`), que no poc é o `services/curves`, ainda não transcrito. O CRUD de provedores (`tPrvdrDadoMercd`) já está sendo feito por outro dev no mesmo serviço; faltam as curvas de mercado, as ligações com provedores e a configuração de cálculo.

## What Changes

- **CRUD da curva de mercado** (`tCurvaMercd`): criar, consultar, listar com filtros, alterar, inativar e reativar, identificando a curva pelo código. O nome é imutável, porque é a chave de todas as FKs. Não há exclusão física.
- **CRUD das ligações com provedores** (`tCurvaPrvdr`): provedor, produto, código na fonte e prioridade, com unicidade por curva, e uma consulta de quais curvas recebem um código da fonte. O `idLigacao` é gerado sem mudar o schema.
- **Versões da configuração de cálculo** (`tConfgCurva`): modelo de construção, interpolador e parâmetros em `cModDado`, validados pelas mesmas regras do engine. A vigência é contínua, sem sobreposição e sem buraco; versões que já começaram não mudam, para o engine reprocessar datas antigas com a configuração da época.
- **Exportação e edição em lote por planilha:** exportar curvas, ligações e configurações num `.xlsx`, editar e importar de volta. A importação pode ser simulada, mostrando campo a campo o que vai mudar, e é aplicada inteira numa transação ou não é aplicada.
- **Proteções de produção:**
  - concorrência otimista (`ETag`/`If-Match`), para duas pessoas não sobrescreverem a alteração uma da outra;
  - auditoria imutável no Blob de toda alteração, com estado anterior e novo;
  - Entra ID com o papel novo `Curvas.Cadastro` para escrita;
  - erros padronizados e horário de Brasília.
- **Valores aceitos num lugar só:** `GET /api/v1/curvas-mercado/valores` repassa os valores aceitos no cálculo, que o engine expõe gerados dos seus próprios enums (inclusive os modelos Groovy ativos), mais os provedores. O front monta os formulários por essa rota, o Swagger declara os `enum` fixos, e a planilha exportada traz listas suspensas.
- **Painel de acompanhamento do gestor:** `GET /api/v1/curvas-mercado/painel`, uma linha por curva numa data-base, com a última data publicada, quem calculou, a situação na data (`CONSTRUIDA`, `EDITADA_MANUALMENTE`, `DESATUALIZADA`, `FALHOU`, `CARGA_RECEBIDA`, `AGUARDANDO_CARGA`, `IGNORADA`, `NAO_E_DIA_UTIL`), atraso em relação ao horário esperado do provedor, último erro e avisos, contadores e filtro "só o que precisa de atenção". Junta o cadastro e os pontos, que o curves tem, com a situação das construções, que o engine expõe por uma rota de leitura; com o engine fora, o painel continua mostrando o que o curves sabe.
- **Exemplo pronto:** `exemplo-cadastro-7-curvas.txt` com o cadastro das 7 curvas iniciais pela API e pela planilha.
- **Avisos de coerência com o engine**, sem bloquear: curva sem origem, origem incompatível com o modelo de construção, modelo não nativo (depende de script Groovy).

## Capabilities

### New Capabilities
- `cadastro-curva-mercado`: CRUD da curva de mercado e as regras comuns do serviço (rotas, autenticação, erros, concorrência, auditoria, horário).
- `ligacao-curva-provedor`: CRUD das ligações entre curva e provedor e consulta por código na fonte.
- `configuracao-calculo-curva`: versões da configuração de cálculo com vigência, validação dos parâmetros e coerência com a curva.
- `cadastro-curvas-planilha`: exportação e importação em lote por planilha, com simulação e aplicação atômica.
- `painel-curvas`: painel de acompanhamento das curvas por data-base, com situação, atraso, erros e avisos.

### Modified Capabilities
<!-- Nenhuma. -->

## Impact

- **services/curves:** rotas novas de curvas de mercado, ligações, configurações, histórico, planilha, valores aceitos e painel. O serviço ainda será transcrito do sistema real; esta change define o comportamento, e a implementação segue a estrutura que o serviço tiver.
- **Banco:** sem mudança de schema. O serviço escreve `tCurvaMercd` (menos `dBaseReft` e `cUsuarCalc`, que são do engine), `tCurvaPrvdr` e `tConfgCurva`.
- **Blob:** pasta `auditoria-cadastro/` no container existente.
- **Entra ID:** papel novo `Curvas.Cadastro` no mesmo registro de aplicação do engine.
- **Depende de:**
  - CRUD de provedores (`tPrvdrDadoMercd`), de outro dev, com os identificadores `B3`, `ANBIMA` e `BLOOMBERG` que o engine e o processor usam;
  - regras de parâmetros da spec `curve-build-pipeline` e rotas `GET /api/v1/valores-cadastro`, `GET /api/v1/curvas/situacao` e de exportação de calendário do change `engine-modelos-curva`.
- **Fora de escopo:** CRUD de provedores (outro dev); edição manual dos pontos (`tDadoCurva`), que está no change `curves-dado-curva`.
- **Efeito no engine:** inativar a curva, ou deixar a data-base fora da vigência dela, faz a carga não construí-la automaticamente; o usuário ainda pode construí-la pelo engine.
