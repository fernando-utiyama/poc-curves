package com.poccurves.engine.application.usecase;
import com.poccurves.engine.application.exception.ModeloConstrucaoException;
import com.poccurves.engine.application.model.InsumoDI1;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.port.ModeloConstrucaoPort;
import com.poccurves.engine.application.port.ModeloCurvaRepositoryPort;

import com.poccurves.engine.dto.EngineDtos.*;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Importa e valida um modelo GROOVY (tarefa 7.4 do backlog curve-engine): compila o script e
 * executa contra um conjunto de insumos de amostra (nunca dado real de mercado — validar um
 * modelo não pode depender de haver insumo publicado para hoje) antes de aceitar. Só persiste
 * quando a validação passa; nunca grava um modelo GROOVY não-testado.
 */
public class ImportarModeloGroovyService {

    /**
     * Insumos sintéticos usados só para provar que o script compila e roda sem estourar — não
     * representam nenhum dado de mercado real. Mesmo formato de {@link InsumoDI1} que o motor
     * usa de verdade, para o script exercitar o caminho real de acesso aos campos.
     */
    private static final List<InsumoDI1> INSUMOS_AMOSTRA = List.of(
            new InsumoDI1("DI1F26", new BigDecimal("13.50"), 21, LocalDate.of(2026, 1, 2)),
            new InsumoDI1("DI1N26", new BigDecimal("13.20"), 126, LocalDate.of(2026, 7, 1)),
            new InsumoDI1("DI1F27", new BigDecimal("12.90"), 273, LocalDate.of(2027, 1, 4))
    );

    private final ModeloCurvaRepositoryPort modeloCurvaRepository;
    private final ModeloConstrucaoPort groovyModeloConstrucao;

    public ImportarModeloGroovyService(ModeloCurvaRepositoryPort modeloCurvaRepository, ModeloConstrucaoPort groovyModeloConstrucao) {
        this.modeloCurvaRepository = modeloCurvaRepository;
        this.groovyModeloConstrucao = groovyModeloConstrucao;
    }

    public ImportarModeloResponse importar(ImportarModeloGroovyRequest request, String importadoPor) {
        if (request.codigo() == null || request.codigo().isBlank()) {
            throw new IllegalArgumentException("código do modelo é obrigatório");
        }
        if (request.scriptGroovy() == null || request.scriptGroovy().isBlank()) {
            throw new IllegalArgumentException("script Groovy não pode ser vazio");
        }

        Optional<ModeloCurva> existente = modeloCurvaRepository.buscarPorCodigo(request.codigo());
        if (existente.isPresent()) {
            return new ImportarModeloResponse(null, request.codigo(), "ERRO_COMPILACAO", null,
                    "já existe um modelo cadastrado com o código " + request.codigo());
        }

        String checksum = calcularChecksumSha256(request.scriptGroovy());
        ModeloCurva modeloParaValidar = ModeloCurva.importarGroovy(
                request.codigo(), request.nome(), request.scriptGroovy(), checksum, importadoPor);

        try {
            groovyModeloConstrucao.construir(modeloParaValidar, INSUMOS_AMOSTRA);
        } catch (ModeloConstrucaoException e) {
            String status = e.fase() == ModeloConstrucaoException.Fase.COMPILACAO ? "ERRO_COMPILACAO" : "ERRO_EXECUCAO_TESTE";
            return new ImportarModeloResponse(null, request.codigo(), status, checksum, e.getMessage());
        }

        modeloCurvaRepository.inserir(modeloParaValidar);

        return new ImportarModeloResponse(modeloParaValidar.id(), request.codigo(), "VALIDO", checksum,
                "Script compilado e executado com sucesso contra insumos de amostra; modelo importado.");
    }

    private String calcularChecksumSha256(String conteudo) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(conteudo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível na JVM", e);
        }
    }
}
