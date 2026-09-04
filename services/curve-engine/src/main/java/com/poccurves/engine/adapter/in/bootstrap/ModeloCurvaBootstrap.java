package com.poccurves.engine.adapter.in.bootstrap;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.port.ModeloCurvaRepositoryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ModeloCurvaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModeloCurvaBootstrap.class);

    private final ModeloCurvaRepositoryPort modeloCurvaRepository;

    public ModeloCurvaBootstrap(ModeloCurvaRepositoryPort modeloCurvaRepository) {
        this.modeloCurvaRepository = modeloCurvaRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        String codigo = "PRE_DI1_B3";
        modeloCurvaRepository.buscarPorCodigo(codigo).ifPresentOrElse(
                modelo -> log.info("Modelo de curva embutido {} já existe no banco.", codigo),
                () -> {
                    ModeloCurva modelo = ModeloCurva.builtin(codigo, "Curva PRE de DI1 B3 (bootstrap embutido, montagem direta a partir dos contratos DI1)");
                    modeloCurvaRepository.inserir(modelo);
                    log.info("Modelo de curva embutido {} criado e inserido com sucesso.", codigo);
                }
        );
    }
}
