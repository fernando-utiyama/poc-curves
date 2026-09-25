package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.UploadModeloResponse;
import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.ErrorCode;
import br.com.poc.domain.service.GroovyDynamicModelCompiler;
import br.com.poc.domain.strategy.CurveBuilderRegistry;
import br.com.poc.domain.strategy.CurveBuilderStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/modelos")
@Tag(name = "Modelos", description = "Gestão e upload de modelos de cálculo em tempo de execução")
public class ModeloUploadController {

    private static final Logger log = LoggerFactory.getLogger(ModeloUploadController.class);

    private final GenericApplicationContext applicationContext;
    private final CurveBuilderRegistry registry;
    private final GroovyDynamicModelCompiler compiler = new GroovyDynamicModelCompiler();

    public ModeloUploadController(GenericApplicationContext applicationContext, CurveBuilderRegistry registry) {
        this.applicationContext = applicationContext;
        this.registry = registry;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de script Groovy para registro dinâmico de modelo quantitativo em runtime")
    public ResponseEntity<UploadModeloResponse> uploadModelo(
        @RequestParam("file") MultipartFile file,
        @RequestParam("nomeModelo") String nomeModelo,
        @RequestParam(value = "ticker", required = false) String ticker,
        @RequestParam(value = "autor", required = false) String autor,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String corrId = (correlationId != null && !correlationId.isBlank()) ? correlationId : UUID.randomUUID().toString();

        if (file.isEmpty()) {
            throw new BusinessException(new ErrorCode() {
                @Override public String getCode() { return "CURV-EMPTY-FILE"; }
                @Override public String getMessage() { return "Arquivo de script Groovy está vazio"; }
            });
        }

        try {
            String scriptContent = new String(file.getBytes(), StandardCharsets.UTF_8);

            // 1. Compila e valida no sandbox seguro
            var resultadoCompilacao = compiler.compileAndInstantiate(scriptContent);

            // 2. Registra no Registry nativo
            registry.register(resultadoCompilacao.instance());

            // 3. Registra dinamicamente como Spring Bean gerenciado no GenericApplicationContext
            String beanName = Character.toLowerCase(nomeModelo.charAt(0)) + nomeModelo.substring(1);
            if (applicationContext.containsBean(beanName)) {
                applicationContext.removeBeanDefinition(beanName);
            }
            applicationContext.registerBean(beanName, CurveBuilderStrategy.class, resultadoCompilacao::instance);

            log.info("Modelo quantitativo Groovy registrado com sucesso. Bean: {}, Hash: {}, CorrelationId: {}",
                beanName, resultadoCompilacao.sha256Hash(), corrId);

            return ResponseEntity.status(HttpStatus.CREATED).body(new UploadModeloResponse(
                beanName,
                resultadoCompilacao.compiledClass().getName(),
                resultadoCompilacao.sha256Hash(),
                LocalDateTime.now(),
                "REGISTRADO_COM_SUCESSO",
                corrId
            ));

        } catch (SecurityException se) {
            log.error("Violação de segurança no script Groovy submetido: {}", se.getMessage());
            throw new BusinessException(new ErrorCode() {
                @Override public String getCode() { return "CURV-GROOVY-SECURITY-VIOLATION"; }
                @Override public String getMessage() { return se.getMessage(); }
            });
        } catch (Exception ex) {
            log.error("Erro na compilação do modelo Groovy: {}", ex.getMessage());
            throw new BusinessException(new ErrorCode() {
                @Override public String getCode() { return "CURV-GROOVY-COMPILATION-ERROR"; }
                @Override public String getMessage() { return ex.getMessage(); }
            });
        }
    }
}
