package br.com.poc.application.service;

import br.com.poc.domain.cadastro.Chave;
import br.com.poc.domain.cadastro.TabelaParametros;
import br.com.poc.domain.curva.CodigoAvisoCurva;
import br.com.poc.domain.curva.CodigoErro;
import br.com.poc.domain.curva.Unidade;
import br.com.poc.domain.interpolacao.BaseInterpolacao;
import br.com.poc.domain.interpolacao.Classificacao;
import br.com.poc.domain.interpolacao.Extrapolacao;
import br.com.poc.domain.quantlib.BusinessDayConvention;
import br.com.poc.domain.quantlib.Compounding;
import br.com.poc.domain.quantlib.DayCounter;
import br.com.poc.domain.quantlib.Frequency;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class ConsultarValoresCadastroService {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public record ItemCatalogo(String valor, String rotulo, String descricao) {}

    public record ModeloInfo(String tipo, String nome, String origem, String versao) {}

    public record ValoresCadastroResultado(
        List<Chave> parametros,
        Map<String, List<ItemCatalogo>> catalogos,
        List<ModeloInfo> modelos,
        String versaoValores
    ) {}

    public ConsultarValoresCadastroService(MessageSource messageSource, ObjectMapper objectMapper) {
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    public ValoresCadastroResultado obterValores() {
        var catalogos = new LinkedHashMap<String, List<ItemCatalogo>>();
        catalogos.put("unidade", itens(Unidade.class));
        catalogos.put("dayCounter", itens(DayCounter.class));
        catalogos.put("compounding", itens(Compounding.class));
        catalogos.put("frequency", itens(Frequency.class));
        catalogos.put("businessDayConvention", itens(BusinessDayConvention.class));
        catalogos.put("baseInterpolacao", itens(BaseInterpolacao.class));
        catalogos.put("extrapolacao", itens(Extrapolacao.class));
        catalogos.put("modoArredondamento", itens(RoundingMode.class));
        catalogos.put("codigoErro", itens(CodigoErro.class));
        catalogos.put("codigoAvisoCurva", itens(CodigoAvisoCurva.class));
        catalogos.put("classificacao", itens(Classificacao.class));

        var modelos = List.of(
            new ModeloInfo("construcao", "TAXA_SWAP_B3", "JAVA", "1.0"),
            new ModeloInfo("construcao", "NTNB_BOOTSTRAP_ANBIMA", "JAVA", "1.0"),
            new ModeloInfo("construcao", "SOFR_ZERO_BLOOMBERG", "JAVA", "1.0"),
            new ModeloInfo("interpolacao", "Linear", "JAVA", "1.0"),
            new ModeloInfo("interpolacao", "FlatForward", "JAVA", "1.0"),
            new ModeloInfo("interpolacao", "BackwardFlat", "JAVA", "1.0"),
            new ModeloInfo("interpolacao", "ForwardFlat", "JAVA", "1.0"),
            new ModeloInfo("interpolacao", "Cubic", "JAVA", "1.0"),
            new ModeloInfo("calendario", "Brazil", "JAVA", "1.0"),
            new ModeloInfo("calendario", "UnitedStates", "JAVA", "1.0")
        );

        var parametros = TabelaParametros.todas();

        return new ValoresCadastroResultado(parametros, catalogos, modelos, versao(parametros, catalogos, modelos));
    }

    /** Hash do conteúdo: muda quando qualquer valor muda, para o front saber quando recarregar. */
    private String versao(List<Chave> parametros, Map<String, List<ItemCatalogo>> catalogos, List<ModeloInfo> modelos) {
        var ordenado = new TreeMap<String, Object>();
        ordenado.put("parametros", parametros);
        ordenado.put("catalogos", catalogos);
        ordenado.put("modelos", modelos);
        String json = objectMapper.writeValueAsString(ordenado);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é obrigatório em toda JVM; não acontece.
            throw new IllegalStateException(e);
        }
    }

    private <E extends Enum<E>> List<ItemCatalogo> itens(Class<E> tipo) {
        return Arrays.stream(tipo.getEnumConstants()).map(v -> {
            String rotuloKey = "poc.valores." + tipo.getSimpleName() + "." + v.name() + ".rotulo";
            String descKey = "poc.valores." + tipo.getSimpleName() + "." + v.name() + ".descricao";
            String rotulo = messageSource.getMessage(rotuloKey, null, v.name(), PT_BR);
            String descricao = messageSource.getMessage(descKey, null, v.name(), PT_BR);
            return new ItemCatalogo(v.name(), rotulo, descricao);
        }).toList();
    }
}
