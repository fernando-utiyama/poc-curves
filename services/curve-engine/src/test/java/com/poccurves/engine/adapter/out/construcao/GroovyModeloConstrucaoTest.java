package com.poccurves.engine.adapter.out.construcao;
import com.poccurves.engine.application.exception.ModeloConstrucaoException;
import com.poccurves.engine.application.model.CurvaJuros;
import com.poccurves.engine.application.model.InsumoDI1;
import com.poccurves.engine.application.model.ModeloCurva;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroovyModeloConstrucaoTest {

    private final GroovyModeloConstrucao construtor = new GroovyModeloConstrucao(2);

    private ModeloCurva modeloComScript(String script) {
        return ModeloCurva.importarGroovy("TESTE_GROOVY", "Modelo de teste", script, "sha256-fake", "tester");
    }

    private final List<InsumoDI1> insumosAmostra = List.of(
            new InsumoDI1("DI1F26", new BigDecimal("13.50"), 21, LocalDate.of(2026, 1, 2)),
            new InsumoDI1("DI1N26", new BigDecimal("13.20"), 126, LocalDate.of(2026, 7, 1))
    );

    @Test
    void suportaApenasModelosGroovy() {
        ModeloCurva builtin = ModeloCurva.builtin("PRE_DI1_B3", "nome");
        assertThat(construtor.suporta(builtin)).isFalse();
        assertThat(construtor.suporta(modeloComScript("[]"))).isTrue();
    }

    @Test
    void executaScriptValidoUsandoInsumosEProduzVertices() {
        ModeloCurva modelo = modeloComScript("""
                insumos.collect { i -> [prazoDiasUteis: i.diasUteisVencimento(), taxa: i.taxaAjuste()] }
                """);

        CurvaJuros curva = construtor.construir(modelo, insumosAmostra);

        assertThat(curva.vertices()).hasSize(2);
        assertThat(curva.taxaEm(21)).isEqualByComparingTo("13.50");
        assertThat(curva.taxaEm(126)).isEqualByComparingTo("13.20");
    }

    @Test
    void aceitaVerticeComCamposOpcionais() {
        ModeloCurva modelo = modeloComScript("""
                [[prazoDiasUteis: 30, taxa: new BigDecimal("10.0"), prazoDiasCorridos: 42, dataVencimento: "2026-02-01", fatorDesconto: new BigDecimal("0.99")]]
                """);

        CurvaJuros curva = construtor.construir(modelo, insumosAmostra);

        assertThat(curva.vertices()).hasSize(1);
        var v = curva.vertices().get(0);
        assertThat(v.prazoDiasCorridos()).isEqualTo(42);
        assertThat(v.dataVencimento()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(v.fatorDesconto()).isEqualByComparingTo("0.99");
    }

    @Test
    void rejeitaScriptQueNaoCompila() {
        ModeloCurva modelo = modeloComScript("isto não é groovy válido {{{");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.COMPILACAO));
    }

    @Test
    void rejeitaScriptQueLancaExcecaoNaExecucao() {
        ModeloCurva modelo = modeloComScript("throw new RuntimeException('falha proposital')");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.EXECUCAO));
    }

    @Test
    void rejeitaScriptCujoRetornoNaoEhListaDeMapas() {
        ModeloCurva modelo = modeloComScript("\"nao eh uma lista\"");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .hasMessageContaining("deve retornar uma List");
    }

    @Test
    void rejeitaVerticeSemChaveObrigatoria() {
        ModeloCurva modelo = modeloComScript("[[prazoDiasUteis: 21]]");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .hasMessageContaining("taxa");
    }

    // ---- Contenção real: cada teste abaixo tenta de verdade escapar do sandbox ----

    @Test
    void bloqueiaAcessoAoSistemaDeArquivos() {
        ModeloCurva modelo = modeloComScript("new File('pom.xml').text; []");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.COMPILACAO));
    }

    @Test
    void bloqueiaEncerramentoDaJvm() {
        ModeloCurva modelo = modeloComScript("System.exit(1); []");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.COMPILACAO));
    }

    @Test
    void bloqueiaExecucaoDeProcessoExterno() {
        ModeloCurva modelo = modeloComScript("Runtime.getRuntime().exec('cmd'); []");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.COMPILACAO));
    }

    @Test
    void bloqueiaExecucaoDeProcessoExternoViaProcessBuilder() {
        // Diferente de File/Socket: aqui a chamada de método (`.start()`) está encadeada na
        // mesma expressão do construtor — a checagem de receptor do SecureASTCustomizer
        // consegue inferir o tipo e bloquear na compilação (confirmado rodando; não assumido).
        ModeloCurva modelo = modeloComScript("new ProcessBuilder('cmd').start(); []");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.COMPILACAO));
    }

    @Test
    void bloqueiaProcessBuilderMesmoQuandoStartEstaEmInstrucaoSeparada() {
        // Caso mais realista da mesma lacuna: construtor e `.start()` em INSTRUÇÕES separadas
        // (variável no meio), não numa única expressão encadeada — testa se a checagem de
        // receptor do SecureASTCustomizer rastreia o tipo através de uma atribuição, ou só
        // dentro da mesma expressão. Qualquer que seja o resultado da AST, a camada 2
        // (SecurityManager.checkExec) tem que bloquear de qualquer jeito — é isso que este
        // teste prova de verdade, sem depender de qual camada pegou.
        ModeloCurva modelo = modeloComScript("def pb = new ProcessBuilder('cmd')\npb.start()\n[]");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class);
    }

    @Test
    void bloqueiaAcessoDeRede() {
        // Camada AST (SecureASTCustomizer) bloqueia a sintaxe `.text`/chamada de método sobre
        // classe fora do allowlist, mas NÃO bloqueia a própria chamada de construtor
        // (`new Socket(...)`) — lacuna real e documentada da biblioteca, achada rodando este
        // teste sem a camada 2. É o SandboxSecurityManager (checkConnect) quem impede a conexão
        // de verdade neste caso — por isso a fase esperada é EXECUCAO, não COMPILACAO.
        ModeloCurva modelo = modeloComScript("new Socket('127.0.0.1', 80); []");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.EXECUCAO));
    }

    @Test
    void bloqueiaConstrucaoDeArquivoMesmoSemChamarMetodoNele() {
        // Confirma especificamente a lacuna do item acima para File: só `new File(...)`, sem
        // nenhum método/propriedade encadeado depois — só a camada 2 (SecurityManager.checkRead,
        // que o JDK chama de dentro do próprio construtor de File) pega isto.
        ModeloCurva modelo = modeloComScript("new File('pom.xml'); []");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.EXECUCAO));
    }

    @Test
    void bloqueiaReflexaoParaAlcancarClasseNaoPermitida() {
        ModeloCurva modelo = modeloComScript("Class.forName('java.lang.Runtime'); []");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.COMPILACAO));
    }

    @Test
    void bloqueiaImportExplicito() {
        ModeloCurva modelo = modeloComScript("import java.io.File\nnew File('x'); []");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.COMPILACAO));
    }

    @Test
    void bloqueiaDefinicaoDeMetodo() {
        ModeloCurva modelo = modeloComScript("def fuga() { return 1 }\n[]");

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.COMPILACAO));
    }

    @Test
    void interrompeScriptQueExcedeOTempoLimite() {
        GroovyModeloConstrucao construtorComTimeoutCurto = new GroovyModeloConstrucao(1);
        ModeloCurva modelo = modeloComScript("while (true) { }");

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> construtorComTimeoutCurto.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.EXECUCAO));
        long duracaoSegundos = (System.nanoTime() - inicio) / 1_000_000_000L;
        assertThat(duracaoSegundos).isLessThan(5);
    }

    @Test
    void rejeitaScriptMaiorQueOTamanhoMaximo() {
        String scriptGigante = "// " + "x".repeat(70_000);
        ModeloCurva modelo = modeloComScript(scriptGigante);

        assertThatThrownBy(() -> construtor.construir(modelo, insumosAmostra))
                .isInstanceOf(ModeloConstrucaoException.class)
                .satisfies(e -> assertThat(((ModeloConstrucaoException) e).fase()).isEqualTo(ModeloConstrucaoException.Fase.COMPILACAO))
                .hasMessageContaining("tamanho máximo");
    }
}
