package br.com.poc.domain.cadastro;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.domain.calendario.Calendario;
import br.com.poc.domain.construcao.ModeloConstrucao;
import br.com.poc.domain.curva.*;
import br.com.poc.domain.interpolacao.BaseInterpolacao;
import br.com.poc.domain.interpolacao.Extrapolacao;
import br.com.poc.domain.quantlib.*;

import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Confere o cadastro da curva e monta a CurvaMercado.
 * Tipo e valores dos parâmetros já chegam garantidos pelo ParametrosCalculo; aqui ficam
 * obrigatoriedade, faixas, combinações, calendário, origens e modelo.
 */
public final class ValidadorCadastro {

    private static final Pattern HORIZONTE = Pattern.compile("^[1-9][0-9]*[DWMY]$");
    private static final Pattern CHAVE_ORIGEM = Pattern.compile("^[^/]+/[^/]+$");
    private static final Set<Frequency> FREQUENCIAS_SEM_PERIODO = Set.of(Frequency.NoFrequency, Frequency.Once, Frequency.OtherFrequency);
    private static final Set<RoundingMode> ARREDONDAMENTOS = Set.of(RoundingMode.HALF_UP, RoundingMode.HALF_EVEN, RoundingMode.DOWN);

    private ValidadorCadastro() {}

    public static CurvaMercado montar(
        CadastroCurva cadastro,
        Function<String, ModeloConstrucao> resolverModelo,
        Function<String, Calendario> resolverCalendario
    ) {
        return montar(cadastro, null, resolverModelo, resolverCalendario);
    }

    public static CurvaMercado montar(
        CadastroCurva cadastro,
        CurvaProvedor origemPedida,
        Function<String, ModeloConstrucao> resolverModelo,
        Function<String, Calendario> resolverCalendario
    ) {
        var detalhes = new ArrayList<DetalheErro>();

        if (cadastro.configuracoesVigentes() != 1) {
            detalhes.add(new DetalheErro("tConfgCurva", null, String.valueOf(cadastro.configuracoesVigentes()),
                "Configuração vigente ausente ou duplicada (encontradas: " + cadastro.configuracoesVigentes() + ")"));
        }
        if (cadastro.erroParametros() != null) {
            detalhes.add(new DetalheErro("cModDado", null, cadastro.jsonParametrosRaw(), "JSON inválido: " + cadastro.erroParametros()));
        }

        ParametrosCalculo p = cadastro.parametros();
        if (p != null) {
            validarParametros(p, detalhes);
        } else if (cadastro.erroParametros() == null) {
            detalhes.add(new DetalheErro("cModDado", null, null, "Parâmetros de cálculo ausentes"));
        }

        String tipoValorStr = aparar(cadastro.tipoValor());
        String normaDiaStr = aparar(cadastro.normaDia());
        String tipoJuroStr = aparar(cadastro.tipoJuro());
        String situacaoStr = aparar(cadastro.situacao());
        String rotinaCalc = aparar(cadastro.rotinaCalc());
        String motorCalc = aparar(cadastro.motorCalc());

        Unidade unidade = null;
        if (tipoValorStr == null || (!tipoValorStr.equals("TAXA") && !tipoValorStr.equals("PRECO") && !tipoValorStr.equals("PONTOS"))) {
            detalhes.add(new DetalheErro("cTpoVlr", null, tipoValorStr, "Unidade inválida: esperado TAXA, PRECO ou PONTOS"));
        } else {
            unidade = Unidade.valueOf(tipoValorStr);
        }

        DayCounter dayCounterCotacao = null;
        Compounding compounding = null;
        if ("TAXA".equals(tipoValorStr)) {
            if (normaDiaStr == null || normaDiaStr.isBlank()) {
                detalhes.add(new DetalheErro("cNormaDia", null, null, "Item obrigatório para unidade TAXA"));
            } else {
                try {
                    dayCounterCotacao = DayCounter.valueOf(normaDiaStr);
                } catch (IllegalArgumentException e) {
                    detalhes.add(new DetalheErro("cNormaDia", null, normaDiaStr, "DayCounter inválido: " + normaDiaStr));
                }
            }
            if (tipoJuroStr == null || tipoJuroStr.isBlank()) {
                detalhes.add(new DetalheErro("cTpoJuro", null, null, "Item obrigatório para unidade TAXA"));
            } else {
                try {
                    compounding = Compounding.valueOf(tipoJuroStr);
                    if (compounding == Compounding.SimpleThenCompounded || compounding == Compounding.CompoundedThenSimple) {
                        detalhes.add(new DetalheErro("cTpoJuro", null, tipoJuroStr, "Regime de capitalização não suportado"));
                    }
                } catch (IllegalArgumentException e) {
                    detalhes.add(new DetalheErro("cTpoJuro", null, tipoJuroStr, "Compounding inválido: " + tipoJuroStr));
                }
            }
        }

        if (p != null) {
            validarCombinacoes(p, tipoValorStr, compounding, rotinaCalc, resolverCalendario, detalhes);
        }

        List<CurvaProvedor> origens = cadastro.origens();
        if (origens == null || origens.isEmpty()) {
            detalhes.add(new DetalheErro("tCurvaPrvdr", null, null, "Curva não possui nenhuma origem cadastrada em tCurvaPrvdr"));
        }
        CurvaProvedor origemPrincipal = (origens != null && !origens.isEmpty()) ? origens.getFirst() : null;
        CurvaProvedor origemUsada = origemPrincipal;

        if (origemPedida != null) {
            var encontrada = origens != null
                ? origens.stream().filter(o -> o.fonte().equals(origemPedida.fonte()) && o.produto().equals(origemPedida.produto())).findFirst()
                : Optional.<CurvaProvedor>empty();
            if (encontrada.isEmpty()) {
                detalhes.add(new DetalheErro("origemSecundaria", null, origemPedida.fonte() + "/" + origemPedida.produto(),
                    "Origem pedida não cadastrada para a curva"));
            } else {
                origemUsada = encontrada.get();
            }
        }

        if (origemPrincipal != null && "TCEN".equals(origemPrincipal.fonte())) {
            detalhes.add(new DetalheErro("origemPrincipal", null, "TCEN", "Curvas derivadas não são suportadas nesta fase"));
        }

        String modeloNome = motorCalc;
        if (origemUsada != null && origemPedida != null && !origemUsada.equals(origemPrincipal) && p != null) {
            String mod = p.modelosPorOrigem().get(origemUsada.fonte() + "/" + origemUsada.produto());
            if (mod != null) {
                modeloNome = mod;
            }
        }

        if (modeloNome == null || modeloNome.isBlank()) {
            detalhes.add(new DetalheErro("cMotorCalc", null, null, "Modelo de construção não definido"));
        } else if (origemUsada != null) {
            try {
                ModeloConstrucao modelo = resolverModelo.apply(modeloNome);
                if (modelo == null) {
                    detalhes.add(new DetalheErro("cMotorCalc", null, modeloNome, "Modelo de construção não encontrado: " + modeloNome));
                } else if (!modelo.fonte().equals(origemUsada.fonte()) || !modelo.produto().equals(origemUsada.produto())) {
                    detalhes.add(new DetalheErro("cMotorCalc", null, modeloNome,
                        "Modelo " + modeloNome + " espera origem " + modelo.fonte() + "/" + modelo.produto()
                            + ", mas origem usada é " + origemUsada.fonte() + "/" + origemUsada.produto()));
                }
            } catch (Exception e) {
                detalhes.add(new DetalheErro("cMotorCalc", null, modeloNome, "Erro ao resolver modelo de construção: " + e.getMessage()));
            }
        }

        if (!detalhes.isEmpty()) {
            throw new BusinessException(
                CodigoErro.CADASTRO_INVALIDO,
                "Cadastro inválido para curva " + cadastro.codigo()
            ).comDetalhes(detalhes);
        }

        var config = new ConfiguracaoCurva(
            p.baseInterpolacao(),
            p.dayCounterTempo(),
            p.frequency(),
            p.calendario(),
            p.mercadoCalendario(),
            p.businessDayConvention(),
            p.inicioOuPadrao(),
            p.fimOuPadrao(),
            Periodos.parse(p.horizonte()),
            p.casasDecimais(),
            p.modoArredondamento(),
            p.versaoScriptConstrucao(),
            p.versaoScriptInterpolacao(),
            p.versaoScriptCalendario(),
            p.modelosPorOrigem()
        );

        boolean ativa = "ATIVO".equals(situacaoStr);

        return new CurvaMercado(
            cadastro.codigo(),
            cadastro.nome(),
            unidade,
            dayCounterCotacao,
            compounding,
            ativa,
            cadastro.inicioVigencia(),
            cadastro.fimVigencia(),
            cadastro.origens(),
            cadastro.componentes() != null ? cadastro.componentes() : List.of(),
            cadastro.idConfiguracao() != null ? cadastro.idConfiguracao() : 0L,
            modeloNome,
            rotinaCalc,
            config,
            cadastro.jsonParametrosRaw()
        );
    }

    /** Obrigatórios, faixas e formatos dos parâmetros. */
    private static void validarParametros(ParametrosCalculo p, List<DetalheErro> detalhes) {
        obrigatorio(detalhes, "BASE_INTERPOLACAO", p.baseInterpolacao());
        obrigatorio(detalhes, "DAY_COUNTER_TEMPO", p.dayCounterTempo());
        obrigatorio(detalhes, "CALENDARIO", aparar(p.calendario()));
        obrigatorio(detalhes, "MERCADO_CALENDARIO", aparar(p.mercadoCalendario()));
        obrigatorio(detalhes, "BUSINESS_DAY_CONVENTION", p.businessDayConvention());
        obrigatorio(detalhes, "HORIZONTE", aparar(p.horizonte()));
        obrigatorio(detalhes, "CASAS_DECIMAIS", p.casasDecimais());
        obrigatorio(detalhes, "MODO_ARREDONDAMENTO", p.modoArredondamento());

        if (aparar(p.horizonte()) != null && !HORIZONTE.matcher(p.horizonte()).matches()) {
            detalhes.add(new DetalheErro("HORIZONTE", null, p.horizonte(), "Formato inválido. Esperado padrão " + HORIZONTE.pattern()));
        }
        if (p.casasDecimais() != null && (p.casasDecimais() < 0 || p.casasDecimais() > 12)) {
            detalhes.add(new DetalheErro("CASAS_DECIMAIS", null, String.valueOf(p.casasDecimais()), "Casas decimais fora do intervalo 0..12"));
        }
        if (p.modoArredondamento() != null && !ARREDONDAMENTOS.contains(p.modoArredondamento())) {
            detalhes.add(new DetalheErro("MODO_ARREDONDAMENTO", null, p.modoArredondamento().name(),
                "Valor não aceito. Valores permitidos: " + ARREDONDAMENTOS));
        }
        versaoScript(detalhes, "VERSAO_SCRIPT_CONSTRUCAO", p.versaoScriptConstrucao());
        versaoScript(detalhes, "VERSAO_SCRIPT_INTERPOLACAO", p.versaoScriptInterpolacao());
        versaoScript(detalhes, "VERSAO_SCRIPT_CALENDARIO", p.versaoScriptCalendario());

        p.modelosPorOrigem().forEach((chave, modelo) -> {
            if (!CHAVE_ORIGEM.matcher(chave).matches() || modelo == null || modelo.isBlank()) {
                detalhes.add(new DetalheErro("MODELOS_POR_ORIGEM", null, chave + "=" + modelo,
                    "Chave fora do formato fonte/produto ou valor vazio"));
            }
        });
    }

    /** Regras que cruzam parâmetros com a curva. */
    private static void validarCombinacoes(ParametrosCalculo p, String tipoValorStr, Compounding compounding, String rotinaCalc,
                                           Function<String, Calendario> resolverCalendario, List<DetalheErro> detalhes) {
        Frequency freq = p.frequency();
        if (compounding == Compounding.Compounded) {
            if (freq == null) {
                detalhes.add(new DetalheErro("FREQUENCY", null, null, "FREQUENCY é obrigatória quando cTpoJuro = Compounded"));
            } else if (FREQUENCIAS_SEM_PERIODO.contains(freq)) {
                detalhes.add(new DetalheErro("FREQUENCY", null, freq.name(), "Frequência não aceita para Compounded"));
            }
        } else if (freq != null) {
            detalhes.add(new DetalheErro("FREQUENCY", null, freq.name(), "FREQUENCY só é permitida quando cTpoJuro = Compounded"));
        }

        BaseInterpolacao base = p.baseInterpolacao();
        if ("TAXA".equals(tipoValorStr) && base == BaseInterpolacao.Price) {
            detalhes.add(new DetalheErro("BASE_INTERPOLACAO", null, base.name(), "Base Price não permitida para unidade TAXA"));
        }
        if (("PRECO".equals(tipoValorStr) || "PONTOS".equals(tipoValorStr)) && base != null && base != BaseInterpolacao.Price) {
            detalhes.add(new DetalheErro("BASE_INTERPOLACAO", null, base.name(), "Unidades PRECO e PONTOS exigem BASE_INTERPOLACAO Price"));
        }

        boolean extrapolacaoFlatForward = p.inicioOuPadrao() == Extrapolacao.FlatForward || p.fimOuPadrao() == Extrapolacao.FlatForward;
        if (extrapolacaoFlatForward && !"Linear".equals(rotinaCalc) && !"FlatForward".equals(rotinaCalc)) {
            detalhes.add(new DetalheErro("EXTRAPOLACAO", null, "FlatForward", "FlatForward só é permitida com interpoladores Linear ou FlatForward"));
        }

        String calStr = aparar(p.calendario());
        String mercStr = aparar(p.mercadoCalendario());
        if (calStr != null && mercStr != null) {
            try {
                Calendario cal = resolverCalendario.apply(calStr);
                if (cal == null) {
                    detalhes.add(new DetalheErro("CALENDARIO", null, calStr, "Calendário não encontrado"));
                } else if (!cal.mercado().equals(mercStr)) {
                    detalhes.add(new DetalheErro("MERCADO_CALENDARIO", null, mercStr,
                        "Mercado " + mercStr + " divergente do calendário " + calStr + " (esperado: " + cal.mercado() + ")"));
                }
            } catch (Exception e) {
                detalhes.add(new DetalheErro("CALENDARIO", null, calStr, "Erro ao resolver calendário: " + e.getMessage()));
            }
        }
    }

    private static void obrigatorio(List<DetalheErro> detalhes, String chave, Object valor) {
        if (valor == null) {
            detalhes.add(new DetalheErro(chave, null, null, "Item obrigatório ausente"));
        }
    }

    private static void versaoScript(List<DetalheErro> detalhes, String chave, Integer versao) {
        if (versao != null && versao < 1) {
            detalhes.add(new DetalheErro(chave, null, String.valueOf(versao), "Versão de script deve ser maior ou igual a 1"));
        }
    }

    private static String aparar(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
