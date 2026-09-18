package com.poccurves.api;

import com.poccurves.api.config.CurveApiSecurityConfig;
import com.poccurves.api.application.CurvaDadosService;
import com.poccurves.api.application.CurvaMercadoService;
import com.poccurves.api.adapter.in.web.CurvaMercadoController;
import com.poccurves.api.dto.ApiDtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fechado durante a auditoria desta sessão: não existia NENHUM teste no
 * nível HTTP (nem `@SpringBootTest`, nem `MockMvc`) — o único teste que se
 * dizia "fronteira de escrita" era um grep de string em código-fonte, que
 * nunca chamava um controller. Mesmo padrão real e testado de
 * `BffSecurityTest` (services/curve-bff).
 * <p>
 * Após a migração para o schema legado, curve-api não tem mais rota
 * admin-only: não há mais o que testar além de "sem token -> 401" e
 * "qualquer perfil autenticado consegue ler".
 */
@WebMvcTest(controllers = CurvaMercadoController.class)
@Import(CurveApiSecurityConfig.class)
class CurveApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurvaMercadoService curvaMercadoService;

    @MockitoBean
    private CurvaDadosService curvaDadosService;

    @Test
    void requisicaoSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(get("/curvas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leitorPodeListarCurvas() throws Exception {
        when(curvaMercadoService.listarTodas()).thenReturn(new CatalogoCurvasResponse(Collections.emptyList()));

        mockMvc.perform(get("/curvas")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void operadorPodeListarCurvas() throws Exception {
        when(curvaMercadoService.listarTodas()).thenReturn(new CatalogoCurvasResponse(Collections.emptyList()));

        mockMvc.perform(get("/curvas")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_OPERATOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void administradorPodeListarCurvas() throws Exception {
        when(curvaMercadoService.listarTodas()).thenReturn(new CatalogoCurvasResponse(Collections.emptyList()));

        mockMvc.perform(get("/curvas")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
