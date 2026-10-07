package br.com.poc.domain.cadastro;

import br.com.poc.domain.CompoundingCotacao;
import br.com.poc.domain.Unidade;
import br.com.poc.domain.aviso.AvisoCurva;
import br.com.poc.domain.aviso.CodigoAvisoCurva;
import br.com.poc.domain.aviso.Detalhe;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Regras dos parâmetros de cálculo. Tipo e lista de valores já vêm garantidos pelo ParametrosCalculo
 * (o Jackson recusa na entrada); aqui ficam obrigatoriedade, faixas, combinações e avisos.
 */
public final class ValidadorParametros {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    public static final Set<String> NATIVE_CONSTRUCTION_MODELS = Set.of("TAXA_SWAP_B3", "NTNB_BOOTSTRAP_ANBIMA", "SOFR_ZERO_BLOOMBERG");
    public static final Set<String> NATIVE_INTERPOLATORS = Set.of("Linear", "FlatForward", "BackwardFlat", "ForwardFlat", "Cubic");
    public static final List<String> INTERPOLADORES_DISPONIVEIS = List.of("FlatForward", "Linear", "BackwardFlat", "ForwardFlat", "Cubic");
    public static final Set<String> NATIVE_CALENDARS = Set.of("Brazil", "UnitedStates");

    // Listas para /valores e para o Swagger, vindas dos próprios enums.
    public static final Set<String> BASES_INTERPOLACAO = nomes(BaseInterpolacao.values());
    public static final Set<String> DAY_COUNTERS = nomes(DayCounter.values());
    public static final Set<String> FREQUENCIES = nomes(Frequency.values());
    public static final Set<String> BUSINESS_DAY_CONVENTIONS = nomes(BusinessDayConvention.values());
    public static final Set<String> EXTRAPOLATION_MODES = nomes(Extrapolacao.values());
    public static final Set<String> ROUNDING_MODES = nomes(ModoArredondamento.values());

    private static final Pattern HORIZONTE_PATTERN = Pattern.compile("^[1-9][0-9]*[DWMY]$");
    private static final Pattern MODELO_ORIGEM_KEY_PATTERN = Pattern.compile("^[^/]+/[^/]+$");

    public record ValidacaoResultado(
        ParametrosCalculo parametrosNormalizados,
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
            ParametrosCalculo entrada,
            Unidade unidade,
            CompoundingCotacao compounding,
            List<CurvaProvedor> provedores) {

        List<Detalhe> erros = new ArrayList<>();
        List<AvisoCurva> avisos = new ArrayList<>();

        if (entrada == null) {
            erros.add(new Detalhe("parametros", null, null, "parametros é obrigatório"));
            return new ValidacaoResultado(null, "", erros, avisos);
        }
        ParametrosCalculo p = entrada.comPadroes();

        // Obrigatórios
        obrigatorio(erros, "BASE_INTERPOLACAO", p.baseInterpolacao());
        obrigatorio(erros, "DAY_COUNTER_TEMPO", p.dayCounterTempo());
        obrigatorio(erros, "CALENDARIO", texto(p.calendario()));
        obrigatorio(erros, "MERCADO_CALENDARIO", texto(p.mercadoCalendario()));
        obrigatorio(erros, "BUSINESS_DAY_CONVENTION", p.businessDayConvention());
        obrigatorio(erros, "HORIZONTE", texto(p.horizonte()));
        obrigatorio(erros, "CASAS_DECIMAIS", p.casasDecimais());
        obrigatorio(erros, "MODO_ARREDONDAMENTO", p.modoArredondamento());

        // Faixas e formatos
        if (p.casasDecimais() != null && (p.casasDecimais() < 0 || p.casasDecimais() > 12)) {
            erro(erros, "CASAS_DECIMAIS", p.casasDecimais(), "CASAS_DECIMAIS deve ser um inteiro de 0 a 12");
        }
        if (texto(p.horizonte()) != null && !HORIZONTE_PATTERN.matcher(p.horizonte()).matches()) {
            erro(erros, "HORIZONTE", p.horizonte(), "HORIZONTE deve seguir o padrão ^[1-9][0-9]*[DWMY]$");
        }
        versaoScript(erros, "VERSAO_SCRIPT_CONSTRUCAO", p.versaoScriptConstrucao());
        versaoScript(erros, "VERSAO_SCRIPT_INTERPOLACAO", p.versaoScriptInterpolacao());
        versaoScript(erros, "VERSAO_SCRIPT_CALENDARIO", p.versaoScriptCalendario());

        // FREQUENCY: obrigatória só com Compounded
        if (compounding == CompoundingCotacao.Compounded) {
            obrigatorio(erros, "FREQUENCY", p.frequency());
        } else if (p.frequency() != null) {
            erro(erros, "FREQUENCY", p.frequency(), "FREQUENCY não deve ser informada quando compounding não for Compounded");
        }

        // Combinações
        if (p.baseInterpolacao() != null && unidade != null) {
            boolean price = p.baseInterpolacao() == BaseInterpolacao.Price;
            if (price && unidade != Unidade.PRECO && unidade != Unidade.PONTOS) {
                erro(erros, "BASE_INTERPOLACAO", p.baseInterpolacao(), "BASE_INTERPOLACAO 'Price' só é permitida quando unidade for PRECO ou PONTOS");
            } else if (!price && unidade != Unidade.TAXA) {
                erro(erros, "BASE_INTERPOLACAO", p.baseInterpolacao(), "BASE_INTERPOLACAO '" + p.baseInterpolacao() + "' só é permitida quando unidade for TAXA");
            }
        }
        if ("Brazil".equals(p.calendario()) && p.mercadoCalendario() != null && !"Settlement".equals(p.mercadoCalendario())) {
            erro(erros, "MERCADO_CALENDARIO", p.mercadoCalendario(), "Para CALENDARIO 'Brazil', MERCADO_CALENDARIO deve ser 'Settlement'");
        } else if ("UnitedStates".equals(p.calendario()) && p.mercadoCalendario() != null && !"FederalReserve".equals(p.mercadoCalendario())) {
            erro(erros, "MERCADO_CALENDARIO", p.mercadoCalendario(), "Para CALENDARIO 'UnitedStates', MERCADO_CALENDARIO deve ser 'FederalReserve'");
        }
        boolean extrapolacaoFlatForward = p.extrapolacaoInicio() == Extrapolacao.FlatForward || p.extrapolacaoFim() == Extrapolacao.FlatForward;
        if (extrapolacaoFlatForward && interpolador != null && !interpolador.equals("Linear") && !interpolador.equals("FlatForward")) {
            erros.add(new Detalhe("parametros.EXTRAPOLACAO", null, null, "FlatForward só é permitido com interpolador Linear ou FlatForward"));
        }

        // MODELOS_POR_ORIGEM
        p.modelosPorOrigem().forEach((chave, modelo) -> {
            if (!MODELO_ORIGEM_KEY_PATTERN.matcher(chave).matches()) {
                erros.add(new Detalhe("parametros.MODELOS_POR_ORIGEM", null, chave, "Chave de MODELOS_POR_ORIGEM deve seguir o formato {provedor}/{produto}"));
            }
            if (modelo == null || modelo.isBlank() || modelo.length() > 100) {
                erros.add(new Detalhe("parametros.MODELOS_POR_ORIGEM." + chave, null, modelo, "Valor de MODELOS_POR_ORIGEM deve ser texto de 1 a 100 caracteres"));
            }
        });

        // Tamanho do JSON gravado em cModDado
        String jsonCompacto = JSON_MAPPER.writeValueAsString(p);
        if (jsonCompacto.length() > 1024) {
            erros.add(new Detalhe("parametros", null, null, "Parâmetros serializados excedem 1.024 caracteres (total: " + jsonCompacto.length() + ")"));
        }

        avisos.addAll(avisosDeModelo(modeloConstrucao, interpolador, p));
        avisos.addAll(avisosDeProvedor(modeloConstrucao, p, provedores));

        return new ValidacaoResultado(p, jsonCompacto, erros, avisos);
    }

    private static List<AvisoCurva> avisosDeModelo(String modeloConstrucao, String interpolador, ParametrosCalculo p) {
        boolean naoNativo = (modeloConstrucao != null && !NATIVE_CONSTRUCTION_MODELS.contains(modeloConstrucao))
            || (interpolador != null && !NATIVE_INTERPOLATORS.contains(interpolador))
            || (p.calendario() != null && !NATIVE_CALENDARS.contains(p.calendario()))
            || p.modelosPorOrigem().values().stream().anyMatch(m -> !NATIVE_CONSTRUCTION_MODELS.contains(m));
        return naoNativo
            ? List.of(new AvisoCurva(CodigoAvisoCurva.MODELO_NAO_NATIVO, "Modelo, interpolador ou calendário que depende de script Groovy", List.of()))
            : List.of();
    }

    private static List<AvisoCurva> avisosDeProvedor(String modeloConstrucao, ParametrosCalculo p, List<CurvaProvedor> provedores) {
        if (provedores == null || provedores.isEmpty()) {
            return List.of();
        }
        List<AvisoCurva> avisos = new ArrayList<>();
        CurvaProvedor principal = provedores.stream()
            .filter(cp -> Integer.valueOf(1).equals(cp.prioridade()))
            .findFirst()
            .orElse(null);

        if (principal != null && modeloConstrucao != null && origemIncompativel(modeloConstrucao, principal.provedor(), principal.produto())) {
            avisos.add(new AvisoCurva(CodigoAvisoCurva.ORIGEM_INCOMPATIVEL_COM_MODELO,
                "Provedor ou produto da origem principal incompatível com o modelo " + modeloConstrucao, List.of()));
        }

        String parPrincipal = principal != null ? principal.provedor() + "/" + principal.produto() : null;
        Set<String> paresExistentes = provedores.stream().map(cp -> cp.provedor() + "/" + cp.produto()).collect(Collectors.toSet());
        p.modelosPorOrigem().forEach((chave, modelo) -> {
            if (!paresExistentes.contains(chave) || chave.equalsIgnoreCase(parPrincipal)) {
                avisos.add(new AvisoCurva(CodigoAvisoCurva.MODELO_POR_ORIGEM_SEM_PROVEDOR,
                    "Chave de MODELOS_POR_ORIGEM sem provedor secundário correspondente: " + chave, List.of()));
            }
            String[] partes = chave.split("/");
            if (origemIncompativel(modelo, partes[0], partes.length > 1 ? partes[1] : "")) {
                avisos.add(new AvisoCurva(CodigoAvisoCurva.ORIGEM_INCOMPATIVEL_COM_MODELO,
                    "Modelo " + modelo + " incompatível com a origem " + chave, List.of()));
            }
        });
        return avisos;
    }

    /** Modelos nativos exigem a fonte e o produto deles; modelos Groovy aceitam qualquer origem. */
    private static boolean origemIncompativel(String modelo, String provedor, String produto) {
        return switch (modelo) {
            case "TAXA_SWAP_B3" -> !("B3".equalsIgnoreCase(provedor) && "TS".equalsIgnoreCase(produto));
            case "NTNB_BOOTSTRAP_ANBIMA" -> !("ANBIMA".equalsIgnoreCase(provedor) && "MS".equalsIgnoreCase(produto));
            case "SOFR_ZERO_BLOOMBERG" -> !("BLOOMBERG".equalsIgnoreCase(provedor) && "BLC2".equalsIgnoreCase(produto));
            default -> false;
        };
    }

    private static void obrigatorio(List<Detalhe> erros, String chave, Object valor) {
        if (valor == null) {
            erros.add(new Detalhe("parametros." + chave, null, null, chave + " é obrigatório"));
        }
    }

    private static void versaoScript(List<Detalhe> erros, String chave, Integer versao) {
        if (versao != null && versao < 1) {
            erro(erros, chave, versao, chave + " deve ser um inteiro maior ou igual a 1");
        }
    }

    private static void erro(List<Detalhe> erros, String chave, Object valor, String motivo) {
        erros.add(new Detalhe("parametros." + chave, null, String.valueOf(valor), motivo));
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static Set<String> nomes(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
