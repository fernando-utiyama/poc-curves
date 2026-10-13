package br.com.poc.application.service;

import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.port.out.ProvedorRepositoryPort;
import br.com.poc.domain.*;
import br.com.poc.domain.cadastro.ValidadorParametros;
import br.com.poc.domain.catalogo.ItemCatalogo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ValoresService {

    private static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

    private final ProvedorRepositoryPort provedorRepositoryPort;
    private final MessageSource messageSource;

    public record RespostaValores(Map<String, Object> valores) {}

    public RespostaValores obterValores() {
        Map<String, Object> resultado = new LinkedHashMap<>(obterCopiaEmbutida());

        // Provedores cadastrados
        List<Map<String, String>> provedores = provedorRepositoryPort.findAll().stream()
            .map(p -> Map.of(
                "nome", Objects.requireNonNullElse(p.getNomeProvedor(), ""),
                "descricao", Objects.requireNonNullElse(p.getDescricao(), "")
            ))
            .toList();
        resultado.put("provedores", provedores);

        // Catálogos do serviço com rótulos e descrições em pt-BR
        resultado.put("unidades", getUnidades());
        resultado.put("dayCountersCotacao", getDayCounters());
        resultado.put("compoundings", getCompoundings());
        resultado.put("situacoesCurva", getSituacoesCurva());
        resultado.put("situacoesPainel", getSituacoesPainel());
        resultado.put("motivosPainel", getMotivosPainel());
        resultado.put("tiposAlteracao", getTiposAlteracao());
        resultado.put("operacoesAlteracao", getOperacoesAlteracao());
        resultado.put("operacoesPontos", getOperacoesPontos());
        resultado.put("origensPontos", getOrigensPontos());
        resultado.put("modosImportacao", getModosImportacao());
        resultado.put("resultadosPonto", getResultadosPonto());
        resultado.put("erros", itensErros());

        return new RespostaValores(resultado);
    }

    public ItemCatalogo item(Class<? extends Enum<?>> enumClass, Enum<?> valor) {
        String rotuloKey = "curvas.valores." + enumClass.getSimpleName() + "." + valor.name() + ".rotulo";
        String descKey = "curvas.valores." + enumClass.getSimpleName() + "." + valor.name() + ".descricao";
        String rotulo = messageSource.getMessage(rotuloKey, null, valor.name(), LOCALE_PT_BR);
        String desc = messageSource.getMessage(descKey, null, "", LOCALE_PT_BR);
        return new ItemCatalogo(valor.name(), rotulo, desc);
    }

    public List<ItemCatalogo> itens(Class<? extends Enum<?>> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
            .map(e -> item(enumClass, e))
            .toList();
    }

    public List<ItemCatalogo> itensErros() {
        return Arrays.stream(CadastroErrorCode.values())
            .map(e -> new ItemCatalogo(e.getCode(), e.getCode(), e.getMessage()))
            .toList();
    }

    public List<ItemCatalogo> getUnidades() { return itens(Unidade.class); }

    public List<ItemCatalogo> getDayCounters() { return itens(DayCounterCotacao.class); }

    public List<ItemCatalogo> getCompoundings() { return itens(CompoundingCotacao.class); }

    public List<ItemCatalogo> getSituacoesCurva() { return itens(SituacaoCurva.class); }

    public List<ItemCatalogo> getSituacoesPainel() { return itens(SituacaoPainel.class); }

    public List<ItemCatalogo> getMotivosPainel() { return itens(MotivoPainel.class); }

    public List<ItemCatalogo> getTiposAlteracao() { return itens(TipoAlteracao.class); }

    public List<ItemCatalogo> getOperacoesAlteracao() { return itens(OperacaoAlteracao.class); }

    public List<ItemCatalogo> getOperacoesPontos() { return itens(OperacaoVertices.class); }

    public List<ItemCatalogo> getOrigensPontos() { return itens(OrigemVertices.class); }

    public List<ItemCatalogo> getModosImportacao() { return itens(ModoImportacao.class); }

    public List<ItemCatalogo> getResultadosPonto() { return itens(ResultadoVertice.class); }

    private Map<String, Object> obterCopiaEmbutida() {
        Map<String, Object> copia = new LinkedHashMap<>();

        copia.put("modelosConstrucao", ValidadorParametros.NATIVE_CONSTRUCTION_MODELS.stream().sorted().toList());
        copia.put("interpoladores", ValidadorParametros.INTERPOLADORES_DISPONIVEIS);
        copia.put("calendarios", ValidadorParametros.NATIVE_CALENDARS.stream().sorted().toList());
        copia.put("basesInterpolacao", ValidadorParametros.BASES_INTERPOLACAO.stream().sorted().toList());
        copia.put("dayCounters", ValidadorParametros.DAY_COUNTERS.stream().sorted().toList());
        copia.put("frequencies", ValidadorParametros.FREQUENCIES.stream().toList());
        copia.put("businessDayConventions", ValidadorParametros.BUSINESS_DAY_CONVENTIONS.stream().sorted().toList());
        copia.put("extrapolations", ValidadorParametros.EXTRAPOLATION_MODES.stream().sorted().toList());
        copia.put("modosArredondamento", ValidadorParametros.ROUNDING_MODES.stream().sorted().toList());

        Map<String, List<String>> mercadosPorCalendario = new LinkedHashMap<>();
        mercadosPorCalendario.put("Brazil", List.of("Settlement"));
        mercadosPorCalendario.put("UnitedStates", List.of("FederalReserve"));
        copia.put("mercadosCalendario", mercadosPorCalendario);

        return copia;
    }
}
