package org.example.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Transparently decompresses request bodies that arrive with {@code Content-Encoding: gzip}.
 *
 * <p>The Snowflake JDBC driver gzips its query-request payloads. The embedded servlet container
 * does not decompress request bodies automatically, so without this filter Jackson would try to
 * parse raw gzip bytes as JSON and fail.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GzipRequestFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String encoding = request.getHeader("Content-Encoding");
        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
            filterChain.doFilter(new GzipRequestWrapper(request), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private static final class GzipRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] decompressed;

        public GzipRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            try (GZIPInputStream gis = new GZIPInputStream(request.getInputStream())) {
                this.decompressed = gis.readAllBytes();
            }
        }

        @Override
        public int getContentLength() { return decompressed.length; }

        @Override
        public long getContentLengthLong() { return decompressed.length; }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(decompressed);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Synchronous reads only; no async support needed for the mock
                }

                @Override
                public int read(){
                    return source.read();
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    return source.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }


}
