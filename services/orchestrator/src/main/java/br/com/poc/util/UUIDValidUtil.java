package br.com.poc.util;

import java.util.UUID;

/**
 * Classe utilitária para validação de UUID.
 */
public final class UUIDValidUtil {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private UUIDValidUtil() { throw new IllegalStateException("Utility class"); }

    /**
     * Verifica se um UUID é válido (não nulo e diferente do UUID nulo 00000000-0000-0000-0000-000000000000).
     *
     * @param uuid UUID a ser verificado
     * @return true se o UUID for válido, false caso contrário
     */
    public static boolean isValid(UUID uuid) { return uuid != null && !uuid.equals(NIL_UUID); }
}
