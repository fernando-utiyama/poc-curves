package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

public class CatalogoService {

    private final CurveApiPort curveApiClient;

    public CatalogoService(CurveApiPort curveApiClient) {
        this.curveApiClient = curveApiClient;
    }

    public CatalogoResponse getCatalogo(String codigo, String modoOrigem, String estado, int pagina, int tamanho) {
        return curveApiClient.getCatalogo(codigo, modoOrigem, estado, pagina, tamanho);
    }

    public DefinicaoCurvaDTO getDefinicao(String codigo) {
        return curveApiClient.getDefinicaoCurva(codigo);
    }

    public DefinicaoCurvaDTO criarDefinicao(String codigo, CriarOuAtualizarDefinicaoCurvaRequest req) {
        return curveApiClient.criarDefinicaoCurva(codigo, req);
    }

    public DefinicaoCurvaDTO atualizarDefinicao(String codigo, CriarOuAtualizarDefinicaoCurvaRequest req) {
        return curveApiClient.atualizarDefinicaoCurva(codigo, req);
    }

    public byte[] downloadModeloCarga(String codigo, String formato) {
        return curveApiClient.downloadModeloCarga(codigo, formato);
    }
}
