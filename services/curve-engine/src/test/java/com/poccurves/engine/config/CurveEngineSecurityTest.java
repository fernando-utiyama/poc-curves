package com.poccurves.engine.config;

import com.poccurves.engine.adapter.in.web.InterpolacaoController;
import com.poccurves.engine.adapter.in.web.ModelosController;
import com.poccurves.engine.application.CompararModelosService;
import com.poccurves.engine.application.ImportarModeloGroovyService;
import com.poccurves.engine.application.InterpolacaoService;
import com.poccurves.engine.application.ListarModelosService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mesmo padrão real e testado de `CurveApiSecurityTest`/`BffSecurityTest`: prova a regra de
 * autorização no nível HTTP, não só que a classe de config existe.
 */
@WebMvcTest(controllers = {InterpolacaoController.class, ModelosController.class})
@Import(CurveEngineSecurityConfig.class)
class CurveEngineSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterpolacaoService interpolacaoService;
    @MockitoBean
    private ListarModelosService listarModelosService;
    @MockitoBean
    private ImportarModeloGroovyService importarModeloGroovyService;
    @MockitoBean
    private CompararModelosService compararModelosService;

    @Test
    void requisicaoSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(get("/api/v1/modelos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leitorPodeListarModelos() throws Exception {
        when(listarModelosService.listarModelos()).thenReturn(new ModelosResponse(List.of()));

        mockMvc.perform(get("/api/v1/modelos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void leitorNaoPodeValidarScriptGroovy() throws Exception {
        mockMvc.perform(post("/api/v1/modelos/validar-groovy")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"X\",\"nome\":\"X\",\"scriptGroovy\":\"[]\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorNaoPodeValidarScriptGroovy() throws Exception {
        mockMvc.perform(post("/api/v1/modelos/validar-groovy")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"X\",\"nome\":\"X\",\"scriptGroovy\":\"[]\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPodeValidarScriptGroovy() throws Exception {
        when(importarModeloGroovyService.importar(any(), any()))
                .thenReturn(new ImportarModeloResponse(null, "X", "VALIDO", "checksum", "ok"));

        mockMvc.perform(post("/api/v1/modelos/validar-groovy")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"X\",\"nome\":\"X\",\"scriptGroovy\":\"[]\"}"))
                .andExpect(status().isOk());
    }
}
