package dev.lotguard.app;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

final class Detector {
    record Profile(String login, String server, String symbol, String preset) {}
    record Result(String state, String warning) {}
    static final String OPEN = "Open the MT5 side menu to verify the active account.";
    static final String DEC = "(?:0|[1-9][0-9]{0,4})[.,][0-9]{1,4}";
    private final List<Profile> profiles;
    private String account = "", candidate = "", lastAlert = "";
    private int count;
    Detector(List<Profile> profiles) { this.profiles = profiles; }
    private boolean stable(String key) {
        count = key.equals(candidate) ? count + 1 : 1;
        candidate = key;
        return count >= 2;
    }
    private static boolean token(String text, String value) {
        return Pattern.compile("(?<![A-Za-z0-9_.#-])" + Pattern.quote(value) +
                "(?![A-Za-z0-9_.#-])", Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
    private Result status(String text) { return new Result(text, null); }
    private Result forget() { account = ""; lastAlert = ""; stable(""); return status(OPEN); }
    Result scan(String raw) {
        String text = raw.toLowerCase(Locale.ROOT);
        boolean drawer = text.contains("manage accounts") && (text.contains("messages") || text.contains("settings"));
        if (drawer) {
            Set<String> ids = new HashSet<>();
            for (Profile p : profiles) if (token(text, p.login()) && token(text, p.server()))
                ids.add(p.server() + "/" + p.login());
            if (ids.size() != 1) return forget();
            String id = ids.iterator().next();
            if (!id.equals(account)) { account = ""; lastAlert = ""; }
            if (!stable("drawer:" + id)) return status("Checking active account…");
            account = id;
            return status("Account verified. Open its order ticket.");
        }
        if (Pattern.compile("(?mi)^\\s*(accounts|manage accounts|new account|login to an account)\\s*$").matcher(text).find())
            return forget();
        if (account.isEmpty()) return status(OPEN);
        boolean ticket = text.contains("market execution") || text.contains("instant execution") ||
                text.contains("new order") || (text.contains("buy") && text.contains("sell"));
        if (!ticket) { stable(""); lastAlert = ""; return status("Waiting for the order ticket."); }
        List<Profile> matches = new ArrayList<>();
        for (Profile p : profiles) if (account.equals(p.server()+"/"+p.login()) && token(text,p.symbol())) matches.add(p);
        if (matches.size()!=1) { stable(""); return status("Symbol or preset unverified."); }
        Profile p = matches.get(0);
        Matcher m = Pattern.compile("(?im)(?:\\b(?:volume|lots?)\\s*[:=]?\\s*("+DEC+")(?=\\s|$)|(?<![\\w.,+-])("+DEC+")\\s+lots?\\b)").matcher(text);
        Set<BigDecimal> amounts = new HashSet<>();
        while (m.find()) amounts.add(new BigDecimal((m.group(1)!=null?m.group(1):m.group(2)).replace(',','.')).stripTrailingZeros());
        // Classic MT5 volume stepper: negative steps, unsigned value, positive steps.
        Matcher step = Pattern.compile("(?m)^\\s*[-−]0[.,][0-9]+\\s+[-−]0[.,][0-9]+\\s+("+DEC+")\\s*\\+0[.,][0-9]+\\s*\\+0[.,][0-9]+\\s*$").matcher(text);
        while (step.find()) amounts.add(new BigDecimal(step.group(1).replace(',','.')).stripTrailingZeros());
        if (amounts.size()!=1) { stable(""); return status("Volume unreadable. Check the MT5 ticket manually."); }
        BigDecimal volume = amounts.iterator().next();
        if (volume.signum()<=0) { stable(""); return status("Volume unverified."); }
        String key = account + "/" + p.symbol() + "/" + volume;
        if (!stable(key)) return status("Checking volume…");
        if (volume.compareTo(new BigDecimal(p.preset())) <= 0) {
            lastAlert = ""; return status(p.symbol()+" · "+volume.toPlainString()+" lots · within saved preset");
        }
        String msg = p.symbol()+" · "+volume.toPlainString()+" lots entered · preset "+p.preset()+
                " · account ending "+p.login().substring(Math.max(0,p.login().length()-4));
        if (key.equals(lastAlert)) return status(msg);
        lastAlert = key;
        return new Result(msg,msg);
    }
}
