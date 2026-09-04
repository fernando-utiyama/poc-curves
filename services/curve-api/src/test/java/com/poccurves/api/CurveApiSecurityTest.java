package com.poccurves.api;

import com.poccurves.api.config.CurveApiSecurityConfig;
import com.poccurves.api.application.CurvaConsultaService;
import com.poccurves.api.adapter.in.web.CurvaConsultaController;
import com.poccurves.api.adapter.in.web.DefinicaoCurvaController;
import com.poccurves.api.application.DefinicaoCurvaService;
import com.poccurves.api.dto.ApiDtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fechado durante a auditoria desta sessão: não existia NENHUM teste no
 * nível HTTP (nem `@SpringBootTest`, nem `MockMvc`) — o único teste que se
 * dizia "fronteira de escrita" era um grep de string em código-fonte, que
 * nunca chamava um controller. Mesmo padrão real e testado de
 * `BffSecurityTest` (services/curve-bff).
 */
@WebMvcTest(controllers = {DefinicaoCurvaController.class, CurvaConsultaController.class})
@Import(CurveApiSecurityConfig.class)
class CurveApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DefinicaoCurvaService definicaoCurvaService;

    @MockitoBean
    private CurvaConsultaService curvaConsultaService;

    private DefinicaoCurvaResponse respostaFake() {
        return new DefinicaoCurvaResponse(
                null, "PRE", "Curva Teste", "BRL", "BOOTSTRAPPED", "ATIVA", "19:00",
                null, 1, null, "DU_252", "B3", "LINEAR", "STRICT", "TRUNCATE_8",
                "BUILTIN_PRE_DI1", 30, 30, 30, 30, 0, List.of(), List.of(), List.of(), null, null
        );
    }

    @Test
    void requisicaoSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(get("/curvas/definicoes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leitorPodeListarDefinicoes() throws Exception {
        when(definicaoCurvaService.listarDefinicoes(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new CatalogoDefinicoesResponse(Collections.emptyList(), 0, 0, 0));

        mockMvc.perform(get("/curvas/definicoes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void leitorNaoPodeCriarDefinicao() throws Exception {
        mockMvc.perform(post("/curvas/definicoes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"PRE\",\"nome\":\"Curva Teste\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorNaoPodeCriarDefinicao() throws Exception {
        mockMvc.perform(post("/curvas/definicoes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"PRE\",\"nome\":\"Curva Teste\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPodeCriarDefinicao() throws Exception {
        when(definicaoCurvaService.criarDefinicao(any(), any())).thenReturn(respostaFake());

        mockMvc.perform(post("/curvas/definicoes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"PRE\",\"nome\":\"Curva Teste\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void operadorNaoPodeAtualizarDefinicao() throws Exception {
        mockMvc.perform(put("/curvas/definicoes/PRE")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Curva Renomeada\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPodeAtualizarDefinicao() throws Exception {
        when(definicaoCurvaService.atualizarDefinicao(eq("PRE"), any())).thenReturn(respostaFake());

        mockMvc.perform(put("/curvas/definicoes/PRE")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Curva Renomeada\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void leitorPodeConsultarCurvaPublicada() throws Exception {
        mockMvc.perform(get("/curvas/PRE")
                        .param("dataReferencia", "2026-08-21")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER"))))
                .andExpect(status().isOk());
    }
}
