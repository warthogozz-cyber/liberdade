package br.com.liberdadefamilia.identity;

import java.util.UUID;

/** Published on every sign-in, whatever the method. */
public record SignedIn(UUID userId, SignInMethod method, DeviceLabel device) {}
