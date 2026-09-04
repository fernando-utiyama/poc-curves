package com.poccurves.engine.adapter.in.web;
import com.poccurves.engine.application.usecase.CompararModelosService;
import com.poccurves.engine.application.usecase.ImportarModeloGroovyService;
import com.poccurves.engine.application.usecase.ListarModelosService;

import tools.jackson.databind.ObjectMapper;
import com.poccurves.engine.config.CurveEngineSecurityConfig;
import com.poccurves.engine.dto.EngineDtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ModelosController.class)
@Import(CurveEngineSecurityConfig.class)
class ModelosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListarModelosService listarModelosService;
    @MockitoBean
    private ImportarModeloGroovyService importarModeloGroovyService;
    @MockitoBean
    private CompararModelosService compararModelosService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listarRetornaOsModelosDoServico() throws Exception {
        when(listarModelosService.listarModelos()).thenReturn(new ModelosResponse(List.of(
                new ModeloDTO(UUID.randomUUID(), "PRE_DI1_B3", "Modelo Padrão", "BUILTIN", "ATIVO", null, null)
        )));

        mockMvc.perform(get("/api/v1/modelos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelos[0].codigo").value("PRE_DI1_B3"))
                .andExpect(jsonPath("$.modelos[0].tipo").value("BUILTIN"));
    }

    @Test
    void validarGroovyRetornaOResultadoDoServico() throws Exception {
        when(importarModeloGroovyService.importar(any(), any())).thenReturn(
                new ImportarModeloResponse(UUID.randomUUID(), "MODELO_X", "VALIDO", "abc123", "Script compilado e executado com sucesso."));

        ImportarModeloGroovyRequest request = new ImportarModeloGroovyRequest("MODELO_X", "Modelo X", "[]");

        mockMvc.perform(post("/api/v1/modelos/validar-groovy")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDO"))
                .andExpect(jsonPath("$.checksum").value("abc123"));
    }

    @Test
    void compararRetornaAsDiferencasDoServico() throws Exception {
        when(compararModelosService.comparar(any())).thenReturn(new ComparacaoResponse(
                LocalDate.of(2026, 8, 21), "MODELO_A", "MODELO_B",
                List.of(new ItemComparacaoDTO(21, new BigDecimal("13.50"), new BigDecimal("13.60"),
                        new BigDecimal("10.00"), null, null, "COINCIDENTE"))
        ));

        ComparacaoModelosRequest request = new ComparacaoModelosRequest("PRE_DI1_B3", LocalDate.of(2026, 8, 21), "FECHAMENTO", "MODELO_A", "MODELO_B");

        mockMvc.perform(post("/api/v1/modelos/comparar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diferencas[0].status").value("COINCIDENTE"))
                .andExpect(jsonPath("$.diferencas[0].diferencaTaxaBps").value(10.00));
    }
}
