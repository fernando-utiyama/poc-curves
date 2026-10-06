## Why

> **Dividida em 2026-09-30.** Esta change entrega a primeira parte: CRUD da curva de mercado, dos provedores da curva, da configuração de cálculo (com os valores aceitos) e do bruto da B3. A planilha do cadastro, o painel, os vértices, a planilha de vértices e as origens secundárias estão na change `curves-operacao-curvas`, que vem depois. O design e o guia de implementação continuam aqui e valem para as duas. O texto abaixo descreve o curves inteiro, como contexto.

O engine só lê o cadastro das curvas (`tCurvaMercd`, `tCurvaPrvdr` e `tConfgCurva` com os parâmetros em `cModDado`), e o processor só lê os provedores da curva em `tCurvaPrvdr` para saber sob qual curva gravar os dados brutos. Ninguém cria nem mantém esse cadastro, e o banco começa vazio. No sistema real, o cadastro é do serviço de curvas (`acts-srv-curvas`), que no poc é o `services/curves`, já transcrito. Nele já existem o CRUD de provedores (`tPrvdrDadoMercd`, de outro dev) e o CRUD do bruto Bloomberg (`tBbergCurvaPrimr`); faltam as curvas de mercado, os provedores da curva e a configuração de cálculo.

Além disso, quando a fonte falha ou traz um valor errado, o gestor da curva precisa corrigir ou digitar os vértices à mão (a curva construída, em `tDadoVertcCurva`), pelo front, e acompanhar a situação de todas as curvas. A decisão é separar ao máximo do engine: o engine constrói, recalcula e interpola; o curves cuida do cadastro, da edição manual dos vértices e do painel. A edição manual é contingência: não pode ser bloqueada nem pelo engine nem por uma dependência fora do ar, e tem preferência sobre a construção automática.

## What Changes

- **CRUD da curva de mercado** (`tCurvaMercd`): criar, consultar, listar com filtros, alterar, inativar e reativar, identificando a curva pelo código. O nome é imutável, porque é a chave de todas as FKs. Não há exclusão física.
- **CRUD dos provedores da curva** (`tCurvaPrvdr`): provedor, produto, código na fonte e prioridade, com unicidade por curva, e uma consulta de quais curvas recebem um código da fonte. O `idCurvaProvedor` é gerado sem mudar o schema.
- **Versões da configuração de cálculo** (`tConfgCurva`): modelo de construção, interpolador e parâmetros em `cModDado`, validados pelas mesmas regras do engine. A vigência é contínua, sem sobreposição e sem buraco; versões que já começaram não mudam, para o engine reprocessar datas antigas com a configuração da época.
- **Exportação e edição em lote por planilha:** exportar curvas, provedores da curva e configurações num `.xlsx`, editar e importar de volta. A importação pode ser simulada, mostrando campo a campo o que vai mudar, e é aplicada inteira numa transação ou não é aplicada.
- **Edição manual dos vértices de uma curva numa data-base** (`tDadoVertcCurva`), com a regravação da curva interpolada (`tDadoCurva`) pedida ao engine depois de cada edição: listar datas com vértices, consultar, gravar a lista completa e apagar a data. O valor chega como string decimal e é arredondado pela configuração vigente, com aviso. O que o gestor envia é exatamente o que fica: a gravação é pela diferença e conferida pelo `hashPontos` antes do commit.
  - **Preferência da edição manual:** sem conferência de versão; se o engine estiver construindo a mesma curva, a edição espera e grava por cima; a construção automática nunca sobrescreve vértices gravados, só um recálculo forçado por um usuário.
  - **Só a consistência do banco barra:** lista vazia, data ou valor malformado, data repetida, valor que não cabe na coluna. Toda regra de negócio (fim de semana, feriado, data antes da data-base, preço não positivo, sem configuração, engine fora) vira aviso, e o vértice é gravado; o engine trata esses vértices na interpolação.
  - **Mesma trava e mesmo `hashPontos` do engine**, pelo banco; a única chamada ao engine depois de gravar é a regravação da curva interpolada, e a edição nunca depende dela; sem auditoria (contingência), com o log `VERTICES_EDITADOS`.
  - **Planilha de vértices** com 5 colunas (`Curva`, `DataBase`, `DataVertice`, `Valor` e `DiasUteis` opcional, obedecido pelo engine), simulação vértice a vértice e aplicação atômica.
- **Manutenção do dado bruto dos três provedores** (`tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr`, `tBbergCurvaPrimr`), como contingência para a carga que falhou ou veio errada: os CRUDs já existentes ganham as rotas `/dados-mercado/{provedor}` da tela Dados de mercado (tickers, consulta por código na fonte e data, vértice a vértice, exclusão da data e planilha). Só grava o bruto: nada é disparado no engine; a curva usa as linhas na próxima construção, ou num recálculo forçado se já estiver construída (aviso `CURVA_JA_CONSTRUIDA`). Recusa só o que não cabe nas colunas; o que faria o modelo da B3 falhar (dias corridos repetidos, dias úteis incoerentes) vira aviso. Mesma trava por curva, `cIdtfdUnic` como o processor, log `CURVA_PRIMARIA_EDITADA`, sem auditoria.
- **Repasse ao engine para a tela Curvas:** construir, recalcular (inclusive pela origem secundária), regravar a interpolada e consultar vértices, pontos e interpolação, sem regra própria além de conferir a curva e os parâmetros.
- **Proteções de produção:**
  - auditoria de toda alteração no log (`CADASTRO_ALTERADO`, com estado anterior e novo) e arquivo de auditoria do cadastro montado na hora, a pedido do front, sem nada no Blob;
  - Entra ID com o papel novo `Curvas.Cadastro` para escrita (adiado para uma change própria);
  - erros padronizados e horário de Brasília.
- **Valores aceitos num lugar só:** `GET /api/v1/curvas-mercado/valores` serve os valores aceitos no cálculo a partir da tabela do validador de parâmetros embutida no serviço (modelos nativos), mais os provedores e os catálogos do serviço. Na primeira parte o curves não chama o engine em lugar nenhum; na segunda (`curves-operacao-curvas`), a rota passa a consultar o engine também para os modelos Groovy ativos, com o aviso `VALORES_SEM_ENGINE` quando ele não responde. O front monta os formulários por essa rota, o Swagger declara os `enum` fixos, e a planilha exportada traz listas suspensas.
- **Painel de acompanhamento do gestor:** `GET /api/v1/curvas-mercado/painel`, uma linha por curva numa data-base, com a última data publicada, quem calculou, a situação na data (`CONSTRUIDA`, `DIVERGENTE_DA_FONTE`, `INTERPOLADA_DESATUALIZADA`, `COM_ERRO`, `NAO_CONSTRUIDA`, `AGUARDANDO_CARGA`, `AGUARDANDO_COMPONENTES`, `IGNORADA`, `NAO_E_DIA_UTIL`, `SITUACAO_INDISPONIVEL`), atraso (data-base passada sem carga ou sem construção), contadores e filtro "só o que precisa de atenção". Junta o cadastro e os vértices, que o curves tem, com a conferência contra a fonte atual, que o engine calcula na hora; nada é guardado para o painel, e com o engine fora ele continua mostrando o que o curves sabe.
- **Exemplo pronto:** `exemplo-cadastro-7-curvas.txt` com o cadastro das 7 curvas iniciais pela API e pela planilha.
- **Origens secundárias.** Provedores da curva de prioridade maior são fontes de reserva: a configuração pode dizer, em `MODELOS_POR_ORIGEM`, qual modelo lê cada uma, e o painel lista as reservas de cada curva para o front oferecer a construção por elas no engine.
- **Estrutura para curvas derivadas** (ex.: inflação implícita = PRE sobre a NTN-B), sem construir nenhuma agora: provedor da curva às curvas componentes pelo provedor interno `TCEN`, recusa de componente inexistente e de ciclo, aviso ao inativar uma curva componente, e situações `AGUARDANDO_COMPONENTES` e `DIVERGENTE_DA_FONTE` no painel.
- **Contrato de tipos para o front pt-BR,** igual ao do engine: decimais como string, datas ISO, enums com caixa exata, avisos e erros num formato único, textos em pt-BR, catálogos com rótulo e descrição em `GET /api/v1/curvas-mercado/valores`, colunas `CHAR` lidas sem espaços, e planilhas com datas `dd/mm/aaaa` e importação aceitando vírgula decimal.
- **Avisos de coerência com o engine**, sem bloquear: curva sem origem, origem incompatível com o modelo de construção, modelo não nativo (depende de script Groovy).

## Capabilities

### New Capabilities
- `cadastro-curva-mercado`: CRUD da curva de mercado e as regras comuns do serviço (rotas, autenticação, erros, última gravação vence, auditoria, horário).
- `provedor-curva`: CRUD dos provedores da curva e consulta por código na fonte.
- `configuracao-calculo-curva`: versões da configuração de cálculo com vigência, validação dos parâmetros e coerência com a curva.
- `vertices-brutos-provedor`: rotas `/dados-mercado/{provedor}` (B3, ANBIMA, Bloomberg) sobre os CRUDs já existentes do dado bruto: tickers, consulta por código na fonte e data, manutenção do vértice e planilha.
- `acoes-curva-mercado`: rotas que repassam ao engine a construção (inclusive recálculo e origem secundária), a regravação da interpolada e as consultas de vértices, pontos e interpolação, para a tela Curvas.

### Modified Capabilities
<!-- Nenhuma. -->

## Impact

- **services/curves:** rotas novas de curvas de mercado, provedores da curva, configurações, auditoria montada na hora, planilha, valores aceitos, painel, vértices e planilha de vértices. O serviço já está transcrito; esta change define o comportamento, e a implementação acrescenta classes à estrutura existente (hexagonal, `application/port`, `adapter/out/persistence`).
- **Banco:** sem mudança de schema. O serviço escreve `tCurvaMercd` (menos `dBaseReft` e `cUsuarCalc`, que são do engine), `tCurvaPrvdr`, `tConfgCurva`, `tDadoVertcCurva` (edição manual, com a mesma trava por curva do engine) e `tBtrsCurvaPrimr` (manutenção do bruto da B3, com a mesma trava; uma nova carga ou reprocessamento da data pelo processor substitui as linhas editadas); em `tDadoCurva`, só apaga a interpolada da data quando os vértices da data são apagados.
- **Blob:** nenhum uso; o Blob fica só com os originais dos feeders e os scripts Groovy do engine.
- **Entra ID (adiado para uma change própria):** papel novo `Curvas.Cadastro` para escrita, além dos papéis de leitura e operação do próprio curves. O engine não exige autenticação, então o curves não precisa de token para chamá-lo.
- **Depende de:**
  - CRUD de provedores (`tPrvdrDadoMercd`), de outro dev, com os identificadores `B3`, `ANBIMA` e `BLOOMBERG` que o engine e o processor usam, e o provedor interno `TCEN` das curvas derivadas;
  - do change `engine-construcao-curvas`: regras de parâmetros da spec `curve-build-pipeline`, copiadas para o `ValidadorParametros` (a primeira parte não chama o engine); a segunda parte (`curves-operacao-curvas`) usa também a fórmula do `hashPontos` e as rotas `GET /api/v1/valores-cadastro`, `GET /api/v1/curvas/situacao` e de exportação de calendário.
- **Fora de escopo:** CRUD de provedores (outro dev); construção, recálculo e interpolação (engine).
- **Efeito no engine:** inativar a curva, ou deixar a data-base fora da vigência dela, faz a carga não construí-la automaticamente; o usuário ainda pode construí-la pelo engine. A edição de vértices sai do engine, que passa a tratar na interpolação os vértices gravados à mão fora das regras de negócio.
