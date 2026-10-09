# CurvaAuditoriaExcelGenerator: trocar só isto (o resto do arquivo fica)

1. Imports: apagar `ConfiguracaoCanonicoState` e `CurvaProvedorCanonicoState`; acrescentar `br.com.bradesco.domain.cadastro.ConfiguracaoCurva` e `br.com.bradesco.domain.cadastro.CurvaProvedor`.
2. Aba Provedores (laço):
   - de `for (CurvaProvedorCanonicoState prov : auditoria.curvaProvedores()) {`
   - para `for (CurvaProvedor prov : auditoria.provedores()) {`
   Os campos usados (`idCurvaProvedor()`, `provedor()`, `produto()`, `tickerProvedor()`, `prioridade()`) são os mesmos do `CurvaProvedor`.
3. Aba Configuracoes (laço):
   - de `for (ConfiguracaoCanonicoState cfg : auditoria.configuracoes()) {`
   - para `for (ConfiguracaoCurva cfg : auditoria.configuracoes()) {`
   Os campos usados (`versao()`, `inicioVigencia()`, `fimVigencia()`, `modeloConstrucao()`, `interpolador()`, `parametros()`) existem no `ConfiguracaoCurva`.
4. Parâmetros na planilha: hoje a célula é `cfg.parametros().toString()`. Com o record `ParametrosCalculo` o `toString()` sai como `ParametrosCalculo[baseInterpolacao=Discount, ...]`. Para a coluna "parametros" mostrar o JSON de `cModDado`, use o `JsonMapper` do projeto (ou deixe o `toString()`, se a auditoria só precisa ser legível).
