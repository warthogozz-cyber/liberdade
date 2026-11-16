package br.com.liberdadefamilia.identity.internal;

import java.io.Serializable;
import java.util.UUID;

/** All the session keeps about who is signed in: the user id, nothing the provider said about them (LGPD). */
public record SessionUser(UUID userId) implements Serializable {}
