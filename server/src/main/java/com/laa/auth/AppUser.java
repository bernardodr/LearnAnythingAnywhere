package com.laa.auth;

import java.util.UUID;

public record AppUser(UUID id, String email, String displayName, Role role) {}
