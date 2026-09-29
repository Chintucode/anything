package com.chintu.anything.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the connection string a database host hands you into what the JDBC driver wants.
 *
 * <p>Neon, Render and Supabase all show a URL like
 * {@code postgresql://user:secret@ep-cool-name.ap-southeast-1.aws.neon.tech/neondb?sslmode=require}.
 * Java's driver wants {@code jdbc:postgresql://host/db} with the user and password passed
 * separately, and it doesn't know libpq-only options such as {@code channel_binding}.
 * Rather than asking you to take the string apart by hand, paste it whole and this does it.
 *
 * @param jdbcUrl  {@code jdbc:postgresql://host[:port]/db?options}
 * @param username null if the URL had none
 * @param password null if the URL had none
 */
public record DatabaseUrl(String jdbcUrl, String username, String password) {

    /** Accepts {@code postgres://}, {@code postgresql://} or an already-JDBC URL. */
    public static DatabaseUrl parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "DATABASE_URL is not set. Paste your database's connection string into it.");
        }
        String url = raw.trim();
        if (url.startsWith("jdbc:")) {
            url = url.substring("jdbc:".length());
        }
        if (!url.startsWith("postgres://") && !url.startsWith("postgresql://")) {
            throw new IllegalArgumentException(
                    "DATABASE_URL should start with postgresql:// (it starts with \""
                            + url.substring(0, Math.min(12, url.length())) + "\").");
        }

        URI uri = URI.create(url.replaceFirst("^postgres(ql)?://", "http://"));
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL has no host name in it.");
        }
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        if (database.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL has no database name after the host.");
        }

        String username = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            username = decode(parts[0]);
            password = parts.length > 1 ? decode(parts[1]) : null;
        }

        List<String> options = new ArrayList<>();
        boolean hasSslMode = false;
        if (uri.getRawQuery() != null) {
            for (String option : uri.getRawQuery().split("&")) {
                if (option.isBlank()) {
                    continue;
                }
                String key = option.split("=", 2)[0];
                switch (key) {
                    case "sslmode" -> {
                        hasSslMode = true;
                        options.add(option);
                    }
                    // libpq's spelling; the Java driver calls it channelBinding.
                    case "channel_binding" -> options.add(option.replace("channel_binding", "channelBinding"));
                    // Other libpq-only options would only produce driver warnings.
                    case "options", "application_name" -> { }
                    default -> options.add(option);
                }
            }
        }
        boolean local = host.equals("localhost") || host.equals("127.0.0.1");
        if (!hasSslMode && !local) {
            // A database on the internet should never be spoken to in plain text.
            options.add("sslmode=require");
        }
        if (host.contains("-pooler.")) {
            // Neon's pooled endpoint runs PgBouncer in transaction mode, which can't
            // keep server-side prepared statements between transactions.
            options.add("prepareThreshold=0");
        }

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(host);
        if (uri.getPort() != -1) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append('/').append(database);
        if (!options.isEmpty()) {
            jdbc.append('?').append(String.join("&", options));
        }
        return new DatabaseUrl(jdbc.toString(), username, password);
    }

    private static String decode(String s) {
        // In a URL's user part a '+' is a plus, not a space.
        return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    /** For logs: never print the password. */
    @Override
    public String toString() {
        return jdbcUrl + " as " + username;
    }
}
