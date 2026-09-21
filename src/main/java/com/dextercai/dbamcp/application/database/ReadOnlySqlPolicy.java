package com.dextercai.dbamcp.application.database;

import java.util.ArrayList;
import java.util.List;

/** Tokenizer-based single-statement policy; deliberately not a regular-expression allow-list. */
public final class ReadOnlySqlPolicy {
    public void validate(String sql) {
        if (sql == null || sql.isBlank() || sql.length() > 20_000) throw new QueryRejectedException("query is empty or too large");
        List<String> tokens = tokenize(sql); if (tokens.isEmpty()) throw new QueryRejectedException("query is empty");
        if (!(tokens.getFirst().equals("SELECT") || tokens.getFirst().equals("WITH"))) throw new QueryRejectedException("only SELECT and WITH queries are permitted");
        for (String prohibited : List.of("INSERT", "UPDATE", "DELETE", "MERGE", "ALTER", "DROP", "CREATE", "GRANT", "REVOKE", "COMMIT", "ROLLBACK", "BEGIN", "DECLARE", "CALL", "EXECUTE", "TRUNCATE", "LOCK")) if (tokens.contains(prohibited)) throw new QueryRejectedException("query contains a prohibited operation");
    }
    private static List<String> tokenize(String input) {
        List<String> result = new ArrayList<>(); boolean quoted = false, lineComment = false, blockComment = false; StringBuilder word = new StringBuilder();
        for (int i = 0; i < input.length(); i++) { char current = input.charAt(i), next = i + 1 < input.length() ? input.charAt(i + 1) : 0;
            if (lineComment) { if (current == '\n') lineComment = false; else continue; }
            if (blockComment) { if (current == '*' && next == '/') { blockComment = false; i++; } continue; }
            if (!quoted && current == '-' && next == '-') { flush(word, result); lineComment = true; i++; continue; }
            if (!quoted && current == '/' && next == '*') { flush(word, result); blockComment = true; i++; continue; }
            if (current == '\'') { quoted = !quoted; flush(word, result); continue; }
            if (quoted) continue;
            if (current == ';') throw new QueryRejectedException("multiple statements are not permitted");
            if (Character.isLetterOrDigit(current) || current == '_') word.append(Character.toUpperCase(current)); else flush(word, result);
        }
        if (quoted || blockComment) throw new QueryRejectedException("unterminated SQL literal or comment"); flush(word, result); return result;
    }
    private static void flush(StringBuilder word, List<String> tokens) { if (!word.isEmpty()) { tokens.add(word.toString()); word.setLength(0); } }
    public static class QueryRejectedException extends RuntimeException { public QueryRejectedException(String message) { super(message); } }
}
