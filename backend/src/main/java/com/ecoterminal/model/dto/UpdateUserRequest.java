package com.ecoterminal.model.dto;

/**
 * PATCH /api/admin/users/{id} isteği — rol ve aktiflik güncelleme.
 */
public record UpdateUserRequest(
        String role,      // ADMIN | USER — null ise değişmez
        Boolean isActive  // true | false — null ise değişmez
) {}
