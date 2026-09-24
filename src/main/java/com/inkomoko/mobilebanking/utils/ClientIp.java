package com.inkomoko.mobilebanking.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;

/** Address of the current HTTP caller. Null outside a request (jobs, startup). */
public final class ClientIp {

    private ClientIp() {
    }

    public static String current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        HttpServletRequest req = attrs.getRequest();
        String remote = req.getRemoteAddr();
        if (isLocalOrPrivate(remote)) {
            String forwarded = firstForwarded(req);
            if (forwarded != null) return forwarded;
        }
        return remote;
    }

    /** Left-most X-Forwarded-For / X-Real-IP, used only when the socket peer is a local proxy. */
    private static String firstForwarded(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isBlank()) return first;
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return null;
    }

    public static boolean isLocalOrPrivate(String ip) {
        if (ip == null || ip.isBlank()) return true;
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return true;
            }
            byte[] raw = address.getAddress();
            return raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
        } catch (Exception e) {
            return true;
        }
    }
}
