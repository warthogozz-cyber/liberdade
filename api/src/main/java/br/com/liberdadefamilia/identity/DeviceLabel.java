package br.com.liberdadefamilia.identity;

import org.jspecify.annotations.Nullable;

/**
 * What the audit trail and the session list may know about a device (LGPD): the browser and the system, never the
 * address nor the raw {@code User-Agent}. Either is {@code other} when it is not one of the usual ones.
 */
public record DeviceLabel(String browser, String os) {

    private static final String OTHER = "other";

    public static DeviceLabel parse(@Nullable String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new DeviceLabel(OTHER, OTHER);
        }
        return new DeviceLabel(browser(userAgent), os(userAgent));
    }

    // Order matters: Edge and Samsung Internet also say Chrome, and Chrome also says Safari.
    private static String browser(String ua) {
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/") || ua.contains("Edge/")) {
            return "Edge";
        }
        if (ua.contains("SamsungBrowser/")) {
            return "Samsung Internet";
        }
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/")) {
            return "Safari";
        }
        return OTHER;
    }

    // Android says Linux, and iOS says "like Mac OS X".
    private static String os(String ua) {
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            return "iOS";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Macintosh") || ua.contains("Mac OS X")) {
            return "macOS";
        }
        if (ua.contains("Linux") || ua.contains("X11")) {
            return "Linux";
        }
        return OTHER;
    }
}
