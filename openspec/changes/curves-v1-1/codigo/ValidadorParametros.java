package br.com.poc.domain.cadastro;

import br.com.poc.domain.CompoundingCotacao;
import br.com.poc.domain.Unidade;
import br.com.poc.domain.aviso.AvisoCurva;
import br.com.poc.domain.aviso.CodigoAvisoCurva;
import br.com.poc.domain.aviso.Detalhe;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.regex.Pattern;

public final class ValidadorParametros {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    public static final Set<String> NATIVE_CONSTRUCTION_MODELS = Set.of(
        "TAXA_SWAP_B3", "NTNB_BOOTSTRAP_ANBIMA", "SOFR_ZERO_BLOOMBERG"
    );

    public static final Set<String> NATIVE_INTERPOLATORS = Set.of(
        "Linear", "FlatForward", "BackwardFlat", "ForwardFlat", "Cubic"
    );

    public static final List<String> INTERPOLADORES_DISPONIVEIS = List.of(
        "FlatForward", "Linear", "BackwardFlat", "ForwardFlat", "Cubic"
    );

    public static final Set<String> NATIVE_CALENDARS = Set.of(
        "Brazil", "UnitedStates"
    );

    public static final Set<String> BASES_INTERPOLACAO = Set.of(
        "Discount", "CompoundFactor", "ZeroYield", "Price"
    );

    public static final Set<String> DAY_COUNTERS = Set.of(
        "Business252", "Actual360", "Actual365Fixed", "Thirty360"
    );

    public static final Set<String> FREQUENCIES = Set.of(
        "Annual", "Semiannual", "EveryFourthMonth", "Quarterly", "Bimonthly",
        "Monthly", "EveryFourthWeek", "Biweekly", "Weekly", "Daily"
    );

    public static final Set<String> BUSINESS_DAY_CONVENTIONS = Set.of(
        "Following", "ModifiedFollowing", "Preceding", "ModifiedPreceding",
        "Unadjusted", "HalfMonthModifiedFollowing", "Nearest"
    );

    public static final Set<String> EXTRAPOLATION_MODES = Set.of(
        "Disabled", "FlatForward", "FlatValue"
    );

    public static final Set<String> ROUNDING_MODES = Set.of(
        "HALF_UP", "HALF_EVEN", "DOWN"
    );

    private static final Set<String> CHAVES_CONHECIDAS = Set.of(
        "BASE_INTERPOLACAO", "DAY_COUNTER_TEMPO", "FREQUENCY", "CALENDARIO",
        "MERCADO_CALENDARIO", "BUSINESS_DAY_CONVENTION", "EXTRAPOLACAO_INICIO",
        "EXTRAPOLACAO_FIM", "HORIZONTE", "CASAS_DECIMAIS", "MODO_ARREDONDAMENTO",
        "VERSAO_SCRIPT_CONSTRUCAO", "VERSAO_SCRIPT_INTERPOLACAO", "VERSAO_SCRIPT_CALENDARIO",
        "MODELOS_POR_ORIGEM"
    );

    private static final Pattern HORIZONTE_PATTERN = Pattern.compile("^[1-9][0-9]*[DWMY]$");
    private static final Pattern MODELO_ORIGEM_KEY_PATTERN = Pattern.compile("^[^/]+/[^/]+$");

    public record ValidacaoResultado(
        Map<String, Object> parametrosNormalizados,
        String jsonCompacto,
        List<Detalhe> erros,
        List<AvisoCurva> avisos
    ) {
        public boolean isValido() {
            return erros.isEmpty();
        }
    }

    private ValidadorParametros() {}

    public static ValidacaoResultado validar(
            String modeloConstrucao,
            String interpolador,
            Map<String, Object> rawParams,
            Unidade unidade,
            CompoundingCotacao compounding,
            List<CurvaProvedor> provedores) {

        List<Detalhe> erros = new ArrayList<>();
        List<AvisoCurva> avisos = new ArrayList<>();
        Map<String, Object> input = rawParams != null ? new HashMap<>(rawParams) : new HashMap<>();

        // 1. Chaves desconhecidas
        for (String k : input.keySet()) {
            if (!CHAVES_CONHECIDAS.contains(k)) {
                erros.add(new Detalhe("parametros." + k, null, String.valueOf(input.get(k)), "Chave de parâmetro desconhecida: " + k));
            }
        }

        // 2 a 12. Chaves simples
        String baseInterpolacao = texto(input, "BASE_INTERPOLACAO", BASES_INTERPOLACAO, true, erros);
        String dayCounterTempo = texto(input, "DAY_COUNTER_TEMPO", DAY_COUNTERS, true, erros);
        String calendario = texto(input, "CALENDARIO", null, true, erros);
        String mercadoCalendario = texto(input, "MERCADO_CALENDARIO", null, true, erros);
        String bdc = texto(input, "BUSINESS_DAY_CONVENTION", BUSINESS_DAY_CONVENTIONS, true, erros);
        String extrapolacaoInicio = Objects.requireNonNullElse(texto(input, "EXTRAPOLACAO_INICIO", EXTRAPOLATION_MODES, false, erros), "Disabled");
        String extrapolacaoFim = Objects.requireNonNullElse(texto(input, "EXTRAPOLACAO_FIM", EXTRAPOLATION_MODES, false, erros), "Disabled");
        String horizonte = texto(input, "HORIZONTE", null, true, erros);
        Integer casasDecimais = inteiro(input, "CASAS_DECIMAIS", 0, 12, true, erros);
        String modoArredondamento = texto(input, "MODO_ARREDONDAMENTO", ROUNDING_MODES, true, erros);

        // FREQUENCY: obrigatória só com Compounded
        String frequency = null;
        if (compounding == CompoundingCotacao.Compounded) {
            frequency = texto(input, "FREQUENCY", FREQUENCIES, true, erros);
        } else if (input.get("FREQUENCY") != null) {
            erro(erros, "FREQUENCY", input.get("FREQUENCY"), "FREQUENCY não deve ser informada quando compounding não for Compounded");
        }

        // Combinações
        if (baseInterpolacao != null && unidade != null) {
            boolean price = "Price".equals(baseInterpolacao);
            if (price && unidade != Unidade.PRECO && unidade != Unidade.PONTOS) {
                erro(erros, "BASE_INTERPOLACAO", baseInterpolacao, "BASE_INTERPOLACAO 'Price' só é permitida quando unidade for PRECO ou PONTOS");
            } else if (!price && unidade != Unidade.TAXA) {
                erro(erros, "BASE_INTERPOLACAO", baseInterpolacao, "BASE_INTERPOLACAO '" + baseInterpolacao + "' só é permitida quando unidade for TAXA");
            }
        }
        if ("Brazil".equals(calendario) && mercadoCalendario != null && !"Settlement".equals(mercadoCalendario)) {
            erro(erros, "MERCADO_CALENDARIO", mercadoCalendario, "Para CALENDARIO 'Brazil', MERCADO_CALENDARIO deve ser 'Settlement'");
        } else if ("UnitedStates".equals(calendario) && mercadoCalendario != null && !"FederalReserve".equals(mercadoCalendario)) {
            erro(erros, "MERCADO_CALENDARIO", mercadoCalendario, "Para CALENDARIO 'UnitedStates', MERCADO_CALENDARIO deve ser 'FederalReserve'");
        }
        if (("FlatForward".equals(extrapolacaoInicio) || "FlatForward".equals(extrapolacaoFim))
                && interpolador != null && !interpolador.equals("Linear") && !interpolador.equals("FlatForward")) {
            erros.add(new Detalhe("parametros.EXTRAPOLACAO", null, null, "FlatForward só é permitido com interpolador Linear ou FlatForward"));
        }
        if (horizonte != null && !HORIZONTE_PATTERN.matcher(horizonte).matches()) {
            erro(erros, "HORIZONTE", horizonte, "HORIZONTE deve seguir o padrão ^[1-9][0-9]*[DWMY]$");
            horizonte = null;
        }

        // 13. Versões de script (opcionais, inteiro >= 1)
        Integer versaoScriptConstrucao = inteiro(input, "VERSAO_SCRIPT_CONSTRUCAO", 1, Integer.MAX_VALUE, false, erros);
        Integer versaoScriptInterpolacao = inteiro(input, "VERSAO_SCRIPT_INTERPOLACAO", 1, Integer.MAX_VALUE, false, erros);
        Integer versaoScriptCalendario = inteiro(input, "VERSAO_SCRIPT_CALENDARIO", 1, Integer.MAX_VALUE, false, erros);

        // 14. MODELOS_POR_ORIGEM
        Map<String, String> modelosPorOrigem = null;
        Object mpoObj = input.get("MODELOS_POR_ORIGEM");
        if (mpoObj != null) {
            if (!(mpoObj instanceof Map<?, ?> m)) {
                erros.add(new Detalhe("parametros.MODELOS_POR_ORIGEM", null, String.valueOf(mpoObj), "MODELOS_POR_ORIGEM deve ser um objeto JSON"));
            } else {
                modelosPorOrigem = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : m.entrySet()) {
                    String chave = String.valueOf(entry.getKey());
                    Object val = entry.getValue();
                    if (!MODELO_ORIGEM_KEY_PATTERN.matcher(chave).matches()) {
                        erros.add(new Detalhe("parametros.MODELOS_POR_ORIGEM", null, chave, "Chave de MODELOS_POR_ORIGEM deve seguir o formato {provedor}/{produto}"));
                    }
                    if (!(val instanceof String v) || v.isEmpty() || v.length() > 100) {
                        erros.add(new Detalhe("parametros.MODELOS_POR_ORIGEM." + chave, null, String.valueOf(val), "Valor de MODELOS_POR_ORIGEM deve ser texto de 1 a 100 caracteres"));
                    } else {
                        modelosPorOrigem.put(chave, v);
                    }
                }
            }
        }

        // Montar mapa normalizado na ordem exata da tabela
        Map<String, Object> paramsOrdenados = new LinkedHashMap<>();
        if (baseInterpolacao != null) paramsOrdenados.put("BASE_INTERPOLACAO", baseInterpolacao);
        if (dayCounterTempo != null) paramsOrdenados.put("DAY_COUNTER_TEMPO", dayCounterTempo);
        if (frequency != null) paramsOrdenados.put("FREQUENCY", frequency);
        if (calendario != null) paramsOrdenados.put("CALENDARIO", calendario);
        if (mercadoCalendario != null) paramsOrdenados.put("MERCADO_CALENDARIO", mercadoCalendario);
        if (bdc != null) paramsOrdenados.put("BUSINESS_DAY_CONVENTION", bdc);
        paramsOrdenados.put("EXTRAPOLACAO_INICIO", extrapolacaoInicio);
        paramsOrdenados.put("EXTRAPOLACAO_FIM", extrapolacaoFim);
        if (horizonte != null) paramsOrdenados.put("HORIZONTE", horizonte);
        if (casasDecimais != null) paramsOrdenados.put("CASAS_DECIMAIS", casasDecimais);
        if (modoArredondamento != null) paramsOrdenados.put("MODO_ARREDONDAMENTO", modoArredondamento);
        if (versaoScriptConstrucao != null) paramsOrdenados.put("VERSAO_SCRIPT_CONSTRUCAO", versaoScriptConstrucao);
        if (versaoScriptInterpolacao != null) paramsOrdenados.put("VERSAO_SCRIPT_INTERPOLACAO", versaoScriptInterpolacao);
        if (versaoScriptCalendario != null) paramsOrdenados.put("VERSAO_SCRIPT_CALENDARIO", versaoScriptCalendario);
        if (modelosPorOrigem != null && !modelosPorOrigem.isEmpty()) paramsOrdenados.put("MODELOS_POR_ORIGEM", modelosPorOrigem);

        String jsonCompacto = "";
        try {
            jsonCompacto = JSON_MAPPER.writeValueAsString(paramsOrdenados);
            if (jsonCompacto.length() > 1024) {
                erros.add(new Detalhe("parametros", null, null, "Parâmetros serializados excedem 1.024 caracteres (total: " + jsonCompacto.length() + ")"));
            }
        } catch (Exception e) {
            erros.add(new Detalhe("parametros", null, null, "Erro ao serializar parâmetros: " + e.getMessage()));
        }

        // Avisos: MODELO_NAO_NATIVO
        boolean modeloNaoNativo = (modeloConstrucao != null && !NATIVE_CONSTRUCTION_MODELS.contains(modeloConstrucao))
            || (interpolador != null && !NATIVE_INTERPOLATORS.contains(interpolador))
            || (calendario != null && !NATIVE_CALENDARS.contains(calendario));
        if (!modeloNaoNativo && modelosPorOrigem != null) {
            for (String mod : modelosPorOrigem.values()) {
                if (!NATIVE_CONSTRUCTION_MODELS.contains(mod)) {
                    modeloNaoNativo = true;
                    break;
                }
            }
        }
        if (modeloNaoNativo) {
            avisos.add(new AvisoCurva(CodigoAvisoCurva.MODELO_NAO_NATIVO, "Modelo, interpolador ou calendário que depende de script Groovy", List.of()));
        }

        // Avisos de provedor / compatibilidade
        if (provedores != null && !provedores.isEmpty()) {
            CurvaProvedor principal = provedores.stream()
                .filter(p -> Integer.valueOf(1).equals(p.prioridade()))
                .findFirst()
                .orElse(null);

            if (principal != null && modeloConstrucao != null && NATIVE_CONSTRUCTION_MODELS.contains(modeloConstrucao)) {
                if ("TAXA_SWAP_B3".equals(modeloConstrucao) && (!"B3".equalsIgnoreCase(principal.provedor()) || !"TS".equalsIgnoreCase(principal.produto()))) {
                    avisos.add(new AvisoCurva(CodigoAvisoCurva.ORIGEM_INCOMPATIVEL_COM_MODELO, "Provedor ou produto da origem principal incompatível com o modelo TAXA_SWAP_B3 (esperado B3/TS)", List.of()));
                } else if ("NTNB_BOOTSTRAP_ANBIMA".equals(modeloConstrucao) && (!"ANBIMA".equalsIgnoreCase(principal.provedor()) || !"MS".equalsIgnoreCase(principal.produto()))) {
                    avisos.add(new AvisoCurva(CodigoAvisoCurva.ORIGEM_INCOMPATIVEL_COM_MODELO, "Provedor ou produto da origem principal incompatível com o modelo NTNB_BOOTSTRAP_ANBIMA (esperado ANBIMA/MS)", List.of()));
                } else if ("SOFR_ZERO_BLOOMBERG".equals(modeloConstrucao) && (!"BLOOMBERG".equalsIgnoreCase(principal.provedor()) || !"BLC2".equalsIgnoreCase(principal.produto()))) {
                    avisos.add(new AvisoCurva(CodigoAvisoCurva.ORIGEM_INCOMPATIVEL_COM_MODELO, "Provedor ou produto da origem principal incompatível com o modelo SOFR_ZERO_BLOOMBERG (esperado BLOOMBERG/BLC2)", List.of()));
                }
            }

            if (modelosPorOrigem != null) {
                Set<String> paresExistentes = new HashSet<>();
                String parPrincipal = principal != null ? principal.provedor() + "/" + principal.produto() : null;
                for (CurvaProvedor cp : provedores) {
                    paresExistentes.add(cp.provedor() + "/" + cp.produto());
                }

                for (Map.Entry<String, String> entry : modelosPorOrigem.entrySet()) {
                    String chave = entry.getKey();
                    String mod = entry.getValue();
                    if (!paresExistentes.contains(chave) || (parPrincipal != null && parPrincipal.equalsIgnoreCase(chave))) {
                        avisos.add(new AvisoCurva(CodigoAvisoCurva.MODELO_POR_ORIGEM_SEM_PROVEDOR, "Chave de MODELOS_POR_ORIGEM sem provedor secundário correspondente: " + chave, List.of()));
                    }
                    if (NATIVE_CONSTRUCTION_MODELS.contains(mod)) {
                        String[] parts = chave.split("/");
                        String prov = parts[0];
                        String prod = parts.length > 1 ? parts[1] : "";
                        if ("TAXA_SWAP_B3".equals(mod) && (!"B3".equalsIgnoreCase(prov) || !"TS".equalsIgnoreCase(prod))) {
                            avisos.add(new AvisoCurva(CodigoAvisoCurva.ORIGEM_INCOMPATIVEL_COM_MODELO, "Modelo " + mod + " incompatível com a origem " + chave, List.of()));
                        } else if ("NTNB_BOOTSTRAP_ANBIMA".equals(mod) && (!"ANBIMA".equalsIgnoreCase(prov) || !"MS".equalsIgnoreCase(prod))) {
                            avisos.add(new AvisoCurva(CodigoAvisoCurva.ORIGEM_INCOMPATIVEL_COM_MODELO, "Modelo " + mod + " incompatível com a origem " + chave, List.of()));
                        } else if ("SOFR_ZERO_BLOOMBERG".equals(mod) && (!"BLOOMBERG".equalsIgnoreCase(prov) || !"BLC2".equalsIgnoreCase(prod))) {
                            avisos.add(new AvisoCurva(CodigoAvisoCurva.ORIGEM_INCOMPATIVEL_COM_MODELO, "Modelo " + mod + " incompatível com a origem " + chave, List.of()));
                        }
                    }
                }
            }
        }

        return new ValidacaoResultado(paramsOrdenados, jsonCompacto, erros, avisos);
    }

    private static String texto(Map<String, Object> in, String chave, Set<String> aceitos, boolean obrigatorio, List<Detalhe> erros) {
        Object v = in.get(chave);
        if (v == null) {
            if (obrigatorio) {
                erros.add(new Detalhe("parametros." + chave, null, null, chave + " é obrigatório"));
            }
            return null;
        }
        if (!(v instanceof String s) || s.isBlank()) {
            erro(erros, chave, v, chave + " deve ser texto");
            return null;
        }
        if (aceitos != null && !aceitos.contains(s)) {
            erro(erros, chave, v, "Valores aceitos para " + chave + ": " + aceitos);
            return null;
        }
        return s;
    }

    private static Integer inteiro(Map<String, Object> in, String chave, int min, int max, boolean obrigatorio, List<Detalhe> erros) {
        Object v = in.get(chave);
        if (v == null) {
            if (obrigatorio) {
                erros.add(new Detalhe("parametros." + chave, null, null, chave + " é obrigatório"));
            }
            return null;
        }
        if (!(v instanceof Integer n) || n < min || n > max) {
            erro(erros, chave, v, chave + " deve ser um inteiro de " + min + " a " + max);
            return null;
        }
        return n;
    }

    private static void erro(List<Detalhe> erros, String chave, Object valor, String motivo) {
        erros.add(new Detalhe("parametros." + chave, null, String.valueOf(valor), motivo));
    }
}
