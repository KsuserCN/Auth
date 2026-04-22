package cn.ksuser.api.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class Oauth2ScopeUtil {

    private Oauth2ScopeUtil() {
    }

    static List<String> parseScopeList(String scopeValue) {
        return List.copyOf(parseScopeSet(scopeValue));
    }

    static Set<String> parseScopeSet(String scopeValue) {
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        if (scopeValue == null || scopeValue.isBlank()) {
            return scopes;
        }

        String[] rawParts = scopeValue.trim().split("[\\s,]+");
        for (String rawPart : rawParts) {
            if (rawPart == null || rawPart.isBlank()) {
                continue;
            }
            scopes.add(rawPart.trim().toLowerCase(Locale.ROOT));
        }
        return scopes;
    }
}
