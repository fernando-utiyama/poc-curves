package com.poccurves.bff;

import com.poccurves.bff.config.BffSecurityConfig;
import com.poccurves.bff.adapter.in.web.CatalogoController;
import com.poccurves.bff.adapter.in.web.DisparoManualController;
import com.poccurves.bff.dto.BffDtos.*;
import com.poccurves.bff.application.CatalogoService;
import com.poccurves.bff.application.DisparoManualService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CatalogoController.class, DisparoManualController.class})
@Import(BffSecurityConfig.class)
class BffSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogoService catalogoService;

    @MockitoBean
    private DisparoManualService disparoService;

    @Test
    void requisicaoSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(get("/api/v1/catalogo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leitorPodeConsultarCatalogo() throws Exception {
        when(catalogoService.getCatalogo(any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new CatalogoResponse(Collections.emptyList(), 0, 0, 0));

        mockMvc.perform(get("/api/v1/catalogo")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void leitorNaoPodeDispararIngestaoManual() throws Exception {
        mockMvc.perform(post("/api/v1/acoes/disparo-manual")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conjuntosInsumo\":[\"BVBG_086\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorPodeDispararIngestaoManual() throws Exception {
        when(disparoService.disparoManual(any()))
                .thenReturn(new DisparoManualResponse("corr-123", "DISPARADO", "ok", List.of()));

        mockMvc.perform(post("/api/v1/acoes/disparo-manual")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conjuntosInsumo\":[\"BVBG_086\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void operadorNaoPodeCriarDefinicaoDeCurva() throws Exception {
        mockMvc.perform(post("/api/v1/curvas/PRE/definicao")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Curva Teste\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPodeCriarDefinicaoDeCurva() throws Exception {
        when(catalogoService.criarDefinicao(eq("PRE"), any()))
                .thenReturn(new DefinicaoCurvaDTO(
                        null, "PRE", "Curva Teste", "BRL", "BOOTSTRAPPED", "ATIVA", "19:00",
                        null, 1, null, "DU_252", "B3", "LINEAR", "STRICT", "TRUNCATE_8",
                        "BUILTIN_PRE_DI1", 30, 30, 30, 30, 0, List.of(), List.of(), List.of(), null, null
                ));

        mockMvc.perform(post("/api/v1/curvas/PRE/definicao")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CURVE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Curva Teste\"}"))
                .andExpect(status().isCreated());
    }
}
