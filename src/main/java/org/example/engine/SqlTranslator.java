package org.example.engine;

import java.util.List;
import java.util.regex.Pattern;

public class SqlTranslator {
    private static final List<Rewrite> TYPE_REWRITES = List.of(
            rewrite("\\bNUMBER\\s*\\(", "DECIMAL("),
            rewrite("\\bNUMERIC\\s*\\(", "DECIMAL("),
            rewrite("\\bNUMBER\\b", "DECIMAL(38,0)"),
            rewrite("\\bNUMERIC\\b", "DECIMAL(38,0)"),
            rewrite("\\bDECIMAL\\b(?!\\s*\\()", "DECIMAL(38,0)"),
            rewrite("\\bSTRING\\b", "VARCHAR"),
            rewrite("\\bNVARCHAR2?\\b", "VARCHAR"),
            rewrite("\\bNCHAR\\b", "VARCHAR"),
            rewrite("\\bVARIANT\\b", "JSON"),
            rewrite("\\bOBJECT\\b", "JSON"),
            rewrite("\\bARRAY\\b", "JSON"),
            rewrite("\\bGEOGRAPHY\\b", "VARCHAR"),
            rewrite("\\bGEOMETRY\\b", "VARCHAR"),
            rewrite("\\bFLOAT4\\b", "FLOAT"),
            rewrite("\\bFLOAT8\\b", "DOUBLE"),
            rewrite("\\bFLOAT\\b", "DOUBLE"),
            rewrite("\\bBINARY\\b", "BLOB"),
            rewrite("\\bVARBINARY\\b", "BLOB"),
            rewrite("\\bDATETIME\\b", "TIMESTAMP"),
            rewrite("\\bTIMESTAMP_NTZ\\b", "TIMESTAMP"),
            rewrite("\\bTIMESTAMPNTZ\\b", "TIMESTAMP"),
            rewrite("\\bTIMESTAMP_LTZ\\b", "TIMESTAMPZ"),
            rewrite("\\bTIMESTAMPLTZ\\b", "TIMESTAMPZ"),
            rewrite("\\bTIMESTAMP_TZ\\b", "TIMESTAMPZ"),
            rewrite("\\bTIMESTAMPTZ_TZ\\b", "TIMESTAMPZ")
    );

    private SqlTranslator() {}

    public static String translate(String sql, StatementCategory category) {
        if (sql == null) {
            return null;
        }
        return applyTypeRewrites(sql);
    }

    private static String applyTypeRewrites(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int n = sql.length();
        int segmentStart = 0;
        char quote = 0;
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (quote == 0) {
                if (c == '\'' || c == '"') {
                    out.append(rewriteSegment(sql.substring(segmentStart, i)));
                    quote = c;
                    segmentStart = i;
                }
                i++;
            } else if (c == quote) {
                if (i + 1 < n && sql.charAt(i + 1) == quote){
                    i+=2;
                } else {
                    out.append(sql, segmentStart, i + 1);
                    quote = 0;
                    segmentStart = i + 1;
                    i++;
                }
            } else {
                i++;
            }
        }
        if (quote == 0) {
            out.append(rewriteSegment(sql.substring(segmentStart)));
        } else {
            out.append(sql, segmentStart, n);
        }
        return out.toString();
    }

    private static String rewriteSegment(String segment) {
        String result = segment;
        for (Rewrite r: TYPE_REWRITES) {
            result = r.pattern().matcher(result).replaceAll(r.replacement());
        }
        return result;
    }

    private static Rewrite rewrite(String regex, String replacement) {
        return new Rewrite(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), replacement);
    }
    private record Rewrite(Pattern pattern, String replacement) {}
}

