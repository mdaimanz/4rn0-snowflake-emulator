package org.example.engine;

import java.util.Locale;

/**
 * Classifies a SQL statement into a {@link StatementCategory} by inspecting its leading keyword
 */
public final class StatementClassifier {

    private StatementClassifier() {}

    public static StatementCategory classify(String sql) {
        String s = stripLeading(sql);
        if(s.isEmpty()) {
            return StatementCategory.UNKNOWN;
        }
        String upper = s.toUpperCase(Locale.ROOT);
        String first = firstWord(upper);

        return switch(first) {
            case "SELECT", "WITH", "VALUES", "TABLE", "SHOW", "DESC", "DESCRIBE",
                    "EXPLAIN", "PRAGMA", "CALL", "LIST", "GET", "FROM" -> StatementCategory.SELECT;
            case "INSERT" -> StatementCategory.INSERT;
            case "UPDATE" -> StatementCategory.UPDATE;
            case "DELETE" -> StatementCategory.DELETE;
            case "MERGE" -> StatementCategory.MERGE;
            case "USE", "SET", "UNSET" -> StatementCategory.SCL;
            case "BEGIN" , "START", "COMMIT", "ROLLBACK" -> StatementCategory.TCL;
            case "ALTER" -> upper.startsWith("ALTER SESSION") ? StatementCategory.SCL : StatementCategory.DDL;
            case "CREATE", "DROP", "GRANT", "REVOKE", "COMMENT", "TRUNCATE",
                    "UNDROP", "RENAME" -> StatementCategory.DDL;
            default -> StatementCategory.UNKNOWN;
        };
    }

    private static String firstWord(String s) {
        int i = 0;
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')){
            i++;
        }
        return s.substring(0, i);
    }

    /**
     * removes leading whitespace and SQL comments so the first real keyword can be read
     * @param sql
     * @return
     */
    static String stripLeading(String sql) {
        if (sql == null) {
            return "";
        }
        int i = 0;
        int n = sql.length();
        while (i < n){
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '-' && i + 1 < n && sql.charAt(i+1)== '-') {
                int nl = sql.indexOf('\n', i+2);
                if (nl < 0){
                    return "";
                }
                i = nl + 1;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) {
                    return "";
                }
                i = end + 2;
            } else {
                break;
            }
        }
        return sql.substring(i);
    }
}
