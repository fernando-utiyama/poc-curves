package br.com.poc.starter.srv.hex.application.validator;

import java.util.UUID;

public class UUIDValidator {

    private UUIDValidator() {
    }

    public static boolean isValid(UUID uuid) { return uuid != null && !uuid.equals(new UUID(0, 0)); }

}
