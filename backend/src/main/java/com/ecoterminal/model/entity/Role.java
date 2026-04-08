package com.ecoterminal.model.entity;

/**
 * Sistemdeki iki kullanıcı rolü.
 * @Enumerated(EnumType.STRING) ile DB'de "ADMIN" / "USER" olarak saklanır.
 */
public enum Role {
    ADMIN,
    USER
}
