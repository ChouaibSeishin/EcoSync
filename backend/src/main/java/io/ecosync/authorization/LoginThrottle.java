package io.ecosync.authorization;
import java.time.*;
import java.util.*;
public final class LoginThrottle {
    private record Window(Instant start, int attempts) { }
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();
    public LoginThrottle(Clock clock) { this.clock = clock; }
    public synchronized boolean allow(String address, String login) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(e -> !e.getValue().start.plusSeconds(60).isAfter(now));
        String ipKey = "ip:" + address, userKey = "user:" + login;
        if (windows.size() >= 4094 && (!windows.containsKey(ipKey) || !windows.containsKey(userKey))) return false;
        Window ip = windows.getOrDefault(ipKey, new Window(now, 0));
        Window user = windows.getOrDefault(userKey, new Window(now, 0));
        if (ip.attempts >= 40 || user.attempts >= 5) return false;
        windows.put(ipKey, new Window(ip.start, ip.attempts + 1));
        windows.put(userKey, new Window(user.start, user.attempts + 1));
        return true;
    }
    public synchronized void succeeded(String login) { windows.remove("user:" + login); }
}
