package com.poccurves.engine.application.usecase;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.port.ModeloCurvaRepositoryPort;

import com.poccurves.engine.dto.EngineDtos.*;

/** Tarefa 7.3/7.9 do backlog curve-engine — catálogo de modelos (BUILTIN + GROOVY). */
public class ListarModelosService {

    private final ModeloCurvaRepositoryPort modeloCurvaRepository;

    public ListarModelosService(ModeloCurvaRepositoryPort modeloCurvaRepository) {
        this.modeloCurvaRepository = modeloCurvaRepository;
    }

    public ModelosResponse listarModelos() {
        var modelos = modeloCurvaRepository.listarTodos().stream()
                .map(this::paraDTO)
                .toList();
        return new ModelosResponse(modelos);
    }

    private ModeloDTO paraDTO(ModeloCurva modelo) {
        return new ModeloDTO(
                modelo.id(),
                modelo.codigo(),
                modelo.nome(),
                modelo.tipo().name(),
                modelo.estado().name(),
                modelo.checksum(),
                modelo.importadoEm()
        );
    }
}
