package com.poccurves.engine.adapter.out.construcao;
import com.poccurves.engine.application.exception.ModeloConstrucaoException;
import com.poccurves.engine.application.model.CurvaJuros;
import com.poccurves.engine.application.model.InsumoDI1;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.model.TipoModelo;
import com.poccurves.engine.application.model.Vertice;
import com.poccurves.engine.application.port.ModeloConstrucaoPort;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executa um modelo de construção GROOVY (tarefas 7.4-7.7 do backlog curve-engine) com
 * contenção em DUAS camadas independentes — nenhuma delas sozinha é suficiente, achado real
 * desta sessão rodando ataques de verdade contra a primeira versão desta classe, não por
 * leitura de documentação:
 * <ol>
 *   <li><b>Compilação (AST)</b>: {@link SecureASTCustomizer} com lista de permissão de
 *       classes que o script pode referenciar ({@code setReceiversWhiteList}/
 *       {@code setImportsWhitelist}), sem {@code import}, sem definição de método, sem
 *       pacote. Pega a maioria dos ataques (reflexão via {@code Class.forName}, chamada de
 *       método sobre classe fora da lista) — <b>mas tem uma lacuna real e documentada da
 *       própria biblioteca Groovy</b>: {@code ConstructorCallExpression} (ex.
 *       {@code new File(...)}, {@code new Socket(...)}) NÃO é bloqueada pelo allowlist de
 *       receptores — confirmado empiricamente: um script com só {@code new Socket('127.0.0.1', 80)}
 *       compila limpo e abre a conexão de verdade.</li>
 *   <li><b>Execução (JVM)</b>: {@link SandboxSecurityManager}, instalado uma vez para a JVM
 *       inteira e escopado por nome de thread — só nega para threads do pool desta classe
 *       (prefixo {@value #NOME_THREAD_SANDBOX}), permite tudo pra qualquer outra thread da
 *       aplicação (JDBC, Kafka, Redis, HTTP). Bloqueia de verdade, no nível da JVM,
 *       independente de como a classe foi referenciada: leitura/escrita/remoção de arquivo,
 *       conexão/escuta/aceitação de rede, execução de processo externo,
 *       {@code System.exit}. É essa camada, não a AST, que fecha a lacuna do item 1.</li>
 * </ol>
 * Depende de {@code SecurityManager} (deprecado desde o JDK 17, JEP 411, removido em versões
 * futuras) — exige a flag de JVM {@code -Djava.security.manager=allow} (sem ela,
 * {@code System.setSecurityManager} lança {@code UnsupportedOperationException} silenciosa
 * no JDK 21+; configurada no {@code argLine} do Surefire e no {@code Containerfile} deste
 * módulo). <b>Se o JDK deste projeto for atualizado além da faixa em que
 * {@code SecurityManager} funciona, esta classe perde sua camada de contenção mais forte e
 * precisa ser revisitada</b> — não escondido, documentado aqui de propósito.
 * <p>
 * Tempo limite de execução numa thread separada, cancelável.
 * <p>
 * <b>Contrato do script</b>: recebe a variável {@code insumos} ({@code List<InsumoDI1>}) e
 * deve avaliar, como última expressão, uma {@code List} de {@code Map} — cada um com pelo
 * menos as chaves {@code prazoDiasUteis} (inteiro) e {@code taxa} (número/BigDecimal/texto
 * decimal), e opcionalmente {@code prazoDiasCorridos}, {@code dataVencimento} (texto
 * ISO-8601) e {@code fatorDesconto}. O script NUNCA constrói {@link Vertice}/{@link CurvaJuros}
 * diretamente — essas classes não estão no allowlist de receptores, deliberadamente: reduz a
 * superfície do sandbox a tipos primitivos/coleções/BigDecimal, e a conversão real para o
 * domínio acontece aqui, fora do script, sob controle total do motor.
 * <p>
 * <b>Limitação real e documentada, não escondida</b>: o limite de tempo é real e efetivo
 * (a thread de execução é interrompida e o resultado descartado). Não existe limite de
 * memória por execução — a JVM não oferece isolamento de heap por thread; a contenção contra
 * uso excessivo de memória hoje é só indireta (tamanho máximo do script-fonte e do número de
 * vértices retornados). Isolamento de memória de verdade exigiria rodar o script num processo
 * separado, fora do escopo desta POC.
 */
@Component
public class GroovyModeloConstrucao implements ModeloConstrucaoPort {

    private static final int MAX_TAMANHO_SCRIPT_CHARS = 64_000;
    private static final int MAX_VERTICES_RETORNADOS = 10_000;

    private static final List<String> RECEPTORES_PERMITIDOS = List.of(
            Object.class.getName(),
            String.class.getName(),
            CharSequence.class.getName(),
            Number.class.getName(),
            Integer.class.getName(),
            Long.class.getName(),
            Short.class.getName(),
            Byte.class.getName(),
            Double.class.getName(),
            Float.class.getName(),
            Boolean.class.getName(),
            Character.class.getName(),
            BigDecimal.class.getName(),
            java.math.BigInteger.class.getName(),
            java.math.MathContext.class.getName(),
            java.math.RoundingMode.class.getName(),
            java.util.List.class.getName(),
            java.util.ArrayList.class.getName(),
            java.util.LinkedList.class.getName(),
            java.util.Map.class.getName(),
            java.util.LinkedHashMap.class.getName(),
            java.util.HashMap.class.getName(),
            java.util.Set.class.getName(),
            java.util.LinkedHashSet.class.getName(),
            java.util.HashSet.class.getName(),
            java.util.Collection.class.getName(),
            java.util.Iterator.class.getName(),
            java.util.Comparator.class.getName(),
            java.util.Arrays.class.getName(),
            java.util.Collections.class.getName(),
            java.time.LocalDate.class.getName(),
            java.time.temporal.ChronoUnit.class.getName(),
            InsumoDI1.class.getName(),
            groovy.lang.GroovyObject.class.getName(),
            groovy.lang.Closure.class.getName(),
            groovy.lang.Script.class.getName(),
            groovy.lang.Range.class.getName(),
            groovy.lang.IntRange.class.getName(),
            RuntimeException.class.getName(),
            Exception.class.getName(),
            Throwable.class.getName(),
            IllegalArgumentException.class.getName(),
            IllegalStateException.class.getName(),
            ArithmeticException.class.getName(),
            // Métodos de extensão idiomáticos do Groovy sobre coleções (.collect/.each/.findAll/
            // .sum/.sort/.inject/...) são métodos estáticos desta classe, invocados por baixo dos
            // panos mesmo com sintaxe `lista.collect { }` — sem isto, setIndirectImportCheckEnabled
            // bloqueia até a sintaxe de coleção mais básica (descoberto rodando o teste real).
            "org.codehaus.groovy.runtime.DefaultGroovyMethods",
            "org.codehaus.groovy.runtime.DefaultGroovyStaticMethods"
    );

    static final String NOME_THREAD_SANDBOX = "groovy-modelo-construcao";

    private final int timeoutSegundos;
    private final ExecutorService executor;

    public GroovyModeloConstrucao(@Value("${curve-engine.groovy.timeout-segundos:5}") int timeoutSegundos) {
        this.timeoutSegundos = timeoutSegundos;
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, NOME_THREAD_SANDBOX);
            thread.setDaemon(true);
            return thread;
        });
        instalarSecurityManagerSandboxUmaVez();
        aquecerClassesDoGroovyForaDoSandbox();
    }

    /**
     * Achado real, catastrófico até corrigido: a primeira vez que qualquer classe do runtime do
     * Groovy (ex. {@code InvokerHelper}) é carregada, seu inicializador estático faz I/O de
     * verdade (lendo recursos/config do próprio Groovy) — se essa primeira carga acontecer numa
     * thread do sandbox, {@link SandboxSecurityManager} nega o I/O, o inicializador estático
     * lança, e a classe fica PERMANENTEMENTE quebrada pra JVM inteira
     * ({@code ExceptionInInitializerError} na primeira vez vira {@code NoClassDefFoundError} em
     * toda chamada seguinte, mesmo de fora do sandbox — inicializador estático só roda uma vez).
     * Por isso este "aquecimento" roda um script Groovy trivial, sem nenhuma customização de
     * sandbox, na própria thread de construção deste bean (não numa thread do pool do sandbox),
     * forçando as classes do runtime do Groovy a inicializar ANTES de qualquer execução real
     * sob restrição.
     */
    private void aquecerClassesDoGroovyForaDoSandbox() {
        new GroovyShell().evaluate("1 + 1");
    }

    /**
     * Instala {@link SandboxSecurityManager} uma única vez para a JVM inteira (idempotente —
     * {@code System.getSecurityManager()} já não-nulo indica que uma instância anterior desta
     * classe, ou este mesmo construtor noutra chamada, já instalou). Escopado por nome de
     * thread: nunca afeta nenhuma outra thread da aplicação.
     */
    private static synchronized void instalarSecurityManagerSandboxUmaVez() {
        if (!(System.getSecurityManager() instanceof SandboxSecurityManager)) {
            System.setSecurityManager(new SandboxSecurityManager());
        }
    }

    @Override
    public boolean suporta(ModeloCurva modelo) {
        return modelo.tipo() == TipoModelo.GROOVY;
    }

    @Override
    public CurvaJuros construir(ModeloCurva modelo, List<InsumoDI1> insumos) {
        String script = modelo.codigoFonte();
        if (script == null || script.isBlank()) {
            throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.COMPILACAO, "script Groovy vazio", null);
        }
        if (script.length() > MAX_TAMANHO_SCRIPT_CHARS) {
            throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.COMPILACAO,
                    "script Groovy excede o tamanho máximo de " + MAX_TAMANHO_SCRIPT_CHARS + " caracteres", null);
        }

        // COMPILAÇÃO: fora da thread do sandbox, de propósito — achado real (ver Javadoc da
        // classe): o próprio compilador do Groovy lê jars do classpath (varredura de
        // transformação AST global), e isso é maquinaria legítima do compilador, não código do
        // script — se rodasse na thread restrita, o SandboxSecurityManager bloquearia o próprio
        // compilador. Parsing não executa nenhuma linha do script (só valida e gera bytecode),
        // então não precisa da contenção de runtime — só do SecureASTCustomizer, que já rejeita
        // construção maliciosa antes de aceitar o bytecode.
        Binding binding = new Binding();
        binding.setVariable("insumos", insumos);
        GroovyShell shell = new GroovyShell(GroovyModeloConstrucao.class.getClassLoader(), binding, compilerConfigurationSandboxed());
        groovy.lang.Script scriptCompilado;
        try {
            scriptCompilado = shell.parse(script);
        } catch (CompilationFailedException e) {
            throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.COMPILACAO,
                    "falha ao compilar script Groovy: " + e.getMessage(), e);
        }

        // EXECUÇÃO: só agora, na thread restrita e com tempo limite — é aqui que código
        // efetivamente escrito pelo autor do script roda.
        Future<Object> future = executor.submit((Callable<Object>) scriptCompilado::run);
        Object resultado;
        try {
            resultado = future.get(timeoutSegundos, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.EXECUCAO,
                    "script Groovy excedeu o tempo limite de " + timeoutSegundos + "s", e);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.EXECUCAO,
                    "falha ao executar script Groovy: " + causa.getMessage(), causa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.EXECUCAO,
                    "execução do script Groovy interrompida", e);
        }

        return converterParaCurva(resultado);
    }

    private CompilerConfiguration compilerConfigurationSandboxed() {
        SecureASTCustomizer secureASTCustomizer = new SecureASTCustomizer();
        secureASTCustomizer.setClosuresAllowed(true);
        secureASTCustomizer.setMethodDefinitionAllowed(false);
        secureASTCustomizer.setPackageAllowed(false);
        // Nenhum import explícito permitido — bloqueia o script tentando dar um nome curto a
        // uma classe fora do allowlist de receptores abaixo.
        secureASTCustomizer.setImportsWhitelist(List.of());
        secureASTCustomizer.setStarImportsWhitelist(List.of());
        secureASTCustomizer.setStaticImportsWhitelist(List.of());
        secureASTCustomizer.setStaticStarImportsWhitelist(List.of());
        // setIndirectImportCheckEnabled(true) foi tentado e descartado (não por leitura da
        // documentação, rodando contra scripts reais): ele trata a classe DECLARANTE de
        // qualquer chamada, inclusive método de extensão do Groovy sobre coleção
        // (`.collect`/`.each`), como um "import" implícito — e para essas chamadas o
        // compilador resolve a classe declarante como `java.lang.Object` de forma inconsistente
        // (bug/limitação conhecida da checagem indireta do SecureASTCustomizer), bloqueando até
        // sintaxe básica de coleção mesmo com a classe de extensão certa no whitelist. A
        // contenção real e testada abaixo (containment tests) fica só por conta do whitelist de
        // RECEPTORES — que checa o tipo do alvo de cada chamada/construção (`new File(...)`,
        // `Runtime.getRuntime()`, etc.), independente de import.
        secureASTCustomizer.setReceiversWhiteList(RECEPTORES_PERMITIDOS);

        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.addCompilationCustomizers(secureASTCustomizer);
        return compilerConfiguration;
    }

    @SuppressWarnings("unchecked")
    private CurvaJuros converterParaCurva(Object resultado) {
        if (!(resultado instanceof List<?> lista)) {
            throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.EXECUCAO,
                    "script Groovy deve retornar uma List de vértices (cada um um Map com prazoDiasUteis/taxa); retornou "
                            + (resultado == null ? "null" : resultado.getClass().getName()), null);
        }
        if (lista.size() > MAX_VERTICES_RETORNADOS) {
            throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.EXECUCAO,
                    "script Groovy retornou " + lista.size() + " vértices, acima do máximo permitido de " + MAX_VERTICES_RETORNADOS, null);
        }

        List<Vertice> vertices = new ArrayList<>();
        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> mapa)) {
                throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.EXECUCAO,
                        "cada vértice retornado pelo script deve ser um Map; encontrado "
                                + (item == null ? "null" : item.getClass().getName()), null);
            }
            vertices.add(mapearVertice((Map<Object, Object>) mapa));
        }

        try {
            return CurvaJuros.de(vertices);
        } catch (IllegalArgumentException e) {
            throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.EXECUCAO,
                    "vértices retornados pelo script são inválidos: " + e.getMessage(), e);
        }
    }

    private Vertice mapearVertice(Map<Object, Object> mapa) {
        try {
            int prazoDiasUteis = ((Number) exigirChave(mapa, "prazoDiasUteis")).intValue();
            BigDecimal taxa = paraBigDecimal(exigirChave(mapa, "taxa"));
            Object prazoDiasCorridosValor = mapa.get("prazoDiasCorridos");
            Integer prazoDiasCorridos = prazoDiasCorridosValor != null ? ((Number) prazoDiasCorridosValor).intValue() : null;
            Object dataVencimentoValor = mapa.get("dataVencimento");
            LocalDate dataVencimento = dataVencimentoValor != null ? LocalDate.parse(dataVencimentoValor.toString()) : null;
            Object fatorDescontoValor = mapa.get("fatorDesconto");
            BigDecimal fatorDesconto = fatorDescontoValor != null ? paraBigDecimal(fatorDescontoValor) : null;
            return new Vertice(prazoDiasUteis, prazoDiasCorridos, dataVencimento, taxa, fatorDesconto);
        } catch (ModeloConstrucaoException e) {
            throw e;
        } catch (Exception e) {
            throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.EXECUCAO,
                    "vértice retornado pelo script em formato inválido: " + mapa + " (" + e.getMessage() + ")", e);
        }
    }

    private Object exigirChave(Map<Object, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (valor == null) {
            throw new ModeloConstrucaoException(ModeloConstrucaoException.Fase.EXECUCAO,
                    "vértice retornado pelo script sem a chave obrigatória '" + chave + "': " + mapa, null);
        }
        return valor;
    }

    private BigDecimal paraBigDecimal(Object valor) {
        if (valor instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(valor.toString());
    }

    /**
     * Camada 2 de contenção (ver Javadoc da classe) — nega arquivo/rede/processo/exit só para
     * threads do pool desta classe ({@link #NOME_THREAD_SANDBOX}); permite tudo (retorna sem
     * lançar) para qualquer outra thread, então nunca afeta JDBC/Kafka/Redis/HTTP da própria
     * aplicação. {@code @SuppressWarnings("removal")}: {@code SecurityManager} é a única forma
     * real de bloquear {@code new File(...)}/{@code new Socket(...)} no nível da JVM — a
     * alternativa de classloader restrito não funciona para essas classes porque elas são
     * carregadas pelo classloader de bootstrap/plataforma, não pelo classloader do script.
     */
    @SuppressWarnings("removal")
    static final class SandboxSecurityManager extends SecurityManager {

        private static boolean threadEmSandbox() {
            return Thread.currentThread().getName().startsWith(NOME_THREAD_SANDBOX);
        }

        private void negarSeEmSandbox(String acao) {
            if (threadEmSandbox()) {
                throw new SecurityException("script Groovy não pode " + acao + " (contenção do sandbox)");
            }
        }

        @Override public void checkExit(int status) { negarSeEmSandbox("encerrar a JVM"); }
        @Override public void checkExec(String cmd) { negarSeEmSandbox("executar processo externo"); }
        @Override public void checkConnect(String host, int port) { negarSeEmSandbox("acessar rede"); }
        @Override public void checkConnect(String host, int port, Object context) { negarSeEmSandbox("acessar rede"); }
        @Override public void checkListen(int port) { negarSeEmSandbox("abrir socket de escuta"); }
        @Override public void checkAccept(String host, int port) { negarSeEmSandbox("aceitar conexão de rede"); }
        @Override public void checkMulticast(java.net.InetAddress maddr) { negarSeEmSandbox("acessar rede (multicast)"); }
        @Override public void checkRead(String file) { negarSeEmSandbox("ler arquivo (" + file + ")"); }
        @Override public void checkRead(String file, Object context) { negarSeEmSandbox("ler arquivo (" + file + ")"); }
        @Override public void checkWrite(String file) { negarSeEmSandbox("escrever arquivo (" + file + ")"); }
        @Override public void checkDelete(String file) { negarSeEmSandbox("apagar arquivo (" + file + ")"); }

        /**
         * Achado real, quebrando a aplicação inteira antes de corrigido (não só threads do
         * sandbox): a implementação padrão de {@code SecurityManager.checkPermission} herdada da
         * classe base consulta a {@code Policy} instalada e, sem arquivo de política nenhum
         * configurado, NEGA por padrão — inclusive coisas triviais como
         * {@code PropertyPermission("*", "read,write")} pedida pelo próprio Surefire/JUnit ao
         * reportar resultado, numa thread que nunca é do sandbox. Sobrescrever para permitir
         * sempre é o que torna esta classe "nega só o que os métodos acima nomeiam, permite todo
         * o resto" em vez de "nega tudo por padrão, permite só o que eu lembrar de listar" — a
         * postura errada para uma aplicação inteira que não sabe que este SecurityManager existe.
         */
        @Override public void checkPermission(java.security.Permission perm) { /* permitido por padrão — negação real fica nos checks específicos acima */ }
        @Override public void checkPermission(java.security.Permission perm, Object context) { /* idem */ }
    }
}
