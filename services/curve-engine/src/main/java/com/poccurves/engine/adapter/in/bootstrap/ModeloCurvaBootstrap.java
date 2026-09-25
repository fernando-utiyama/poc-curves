package com.poccurves.engine.adapter.in.bootstrap;
import com.poccurves.engine.adapter.out.construcao.BuiltinModeloConstrucao;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.port.ModeloCurvaRepositoryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Garante que o modelo embutido padrão das curvas TS B3 (openspec/changes/b3-additional-curves)
 * exista no banco na subida do serviço — mesmo papel que este bootstrap tinha para PRE_DI1_B3
 * antes da limpeza da curva DI1/BOOTSTRAPPED (curva-processor/curve-engine).
 */
@Component
public class ModeloCurvaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModeloCurvaBootstrap.class);

    private final ModeloCurvaRepositoryPort modeloCurvaRepository;

    public ModeloCurvaBootstrap(ModeloCurvaRepositoryPort modeloCurvaRepository) {
        this.modeloCurvaRepository = modeloCurvaRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        String codigo = BuiltinModeloConstrucao.CODIGO_TAXA_SWAP_TRANSCRICAO_B3;
        modeloCurvaRepository.buscarPorCodigo(codigo).ifPresentOrElse(
                modelo -> log.info("Modelo de curva embutido {} já existe no banco.", codigo),
                () -> {
                    ModeloCurva modelo = ModeloCurva.builtin(codigo,
                            "Transcrição das curvas TS B3 (montagem direta dos vértices já calculados pela B3, sem recálculo)");
                    modeloCurvaRepository.inserir(modelo);
                    log.info("Modelo de curva embutido {} criado e inserido com sucesso.", codigo);
                }
        );
    }
}
