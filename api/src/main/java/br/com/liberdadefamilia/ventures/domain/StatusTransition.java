package br.com.liberdadefamilia.ventures.domain;

/** The status changes a project may make. A closed project can only be reopened as paused. */
public final class StatusTransition {

    private StatusTransition() {}

    public static boolean allowed(VentureStatus from, VentureStatus to) {
        return switch (from) {
            case PLANNING -> to == VentureStatus.ACTIVE || to == VentureStatus.CLOSED;
            case ACTIVE -> to == VentureStatus.PAUSED || to == VentureStatus.CLOSED;
            case PAUSED -> to == VentureStatus.ACTIVE || to == VentureStatus.CLOSED;
            case CLOSED -> to == VentureStatus.PAUSED;
        };
    }
}
