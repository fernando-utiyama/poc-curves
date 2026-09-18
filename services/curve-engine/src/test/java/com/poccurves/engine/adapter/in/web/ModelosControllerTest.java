package com.poccurves.engine.adapter.in.web;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listarRetornaOsModelosDoServico() throws Exception {
        when(listarModelosService.listarModelos()).thenReturn(new ModelosResponse(List.of(
                new ModeloDTO(UUID.randomUUID(), "TAXA_SWAP_TRANSCRICAO_B3", "Modelo Padrão", "BUILTIN", "ATIVO", null, null)
        )));

        mockMvc.perform(get("/api/v1/modelos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelos[0].codigo").value("TAXA_SWAP_TRANSCRICAO_B3"))
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
}
