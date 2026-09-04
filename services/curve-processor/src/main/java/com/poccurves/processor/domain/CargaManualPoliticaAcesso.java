package com.poccurves.processor.domain;


import java.util.Set;

/**
 * Guarda de acesso da carga manual de curva (tarefa 6.11 do backlog):
 * limite de tamanho de arquivo e perfil exigido. Função pura, sem estado —
 * quem a chama (um futuro controller HTTP) já resolveu o tamanho do
 * arquivo recebido e o perfil do usuário autenticado; nada aqui lida com
 * autenticação, porque o serviço não tem nenhum mecanismo de autenticação
 * hoje (nenhum endpoint HTTP de carga manual existe ainda — ver tasks.md).
 */
public final class CargaManualPoliticaAcesso {

    /** 10 MiB — limite provisório, não calibrado contra tamanho real de arquivo de carga (não há um "arquivo real" de referência, é carga manual). */
    public static final long TAMANHO_MAXIMO_BYTES = 10L * 1024 * 1024;

    public static final Set<String> PERFIS_PERMITIDOS = Set.of("OPERADOR", "ADMINISTRADOR");

    private CargaManualPoliticaAcesso() {
    }

    /**
     * @throws ArquivoCargaExcedeTamanhoException se tamanhoBytes exceder {@link #TAMANHO_MAXIMO_BYTES}
     * @throws PerfilNaoAutorizadoParaCargaException se perfil não for OPERADOR nem ADMINISTRADOR
     */
    public static void validar(long tamanhoBytes, String perfil) {
        if (tamanhoBytes > TAMANHO_MAXIMO_BYTES) {
            throw new ArquivoCargaExcedeTamanhoException(tamanhoBytes, TAMANHO_MAXIMO_BYTES);
        }
        if (perfil == null || !PERFIS_PERMITIDOS.contains(perfil)) {
            throw new PerfilNaoAutorizadoParaCargaException(String.valueOf(perfil));
        }
    }
}
