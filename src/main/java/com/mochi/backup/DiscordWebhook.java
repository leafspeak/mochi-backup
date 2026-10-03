package com.mochi.backup;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sends rich embed notifications to a Discord webhook.
 * Used for backup start/done/failed and restore events.
 */
public class DiscordWebhook {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Send a rich embed notification.
     * @param url     Discord webhook URL
     * @param title   Embed title
     * @param desc    Embed description (supports \n line breaks)
     * @param color   RGB color integer (e.g. 0x00AA00 for green)
     */
    public static void send(String url, String title, String desc, int color) {
        if (url == null || url.isBlank()) return;
        new Thread(() -> {
            try {
                JsonObject embed = new JsonObject();
                embed.addProperty("title", title);
                embed.addProperty("description", desc);
                embed.addProperty("color", color);
                embed.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z");
                embed.addProperty("footer_text", "Mochi Backup");
                JsonArray arr = new JsonArray();
                arr.add(embed);
                JsonObject payload = new JsonObject();
                payload.add("embeds", arr);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();
                HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 204) {
                    MochiClient.LOGGER.warn("Discord webhook returned status {}", resp.statusCode());
                }
            } catch (Exception e) {
                MochiClient.LOGGER.warn("Discord webhook send failed", e);
            }
        }, "Mochi-Webhook").start();
    }

    /** Convenience: green success embed */
    public static void sendSuccess(String url, String title, String desc) {
        send(url, title, desc, 0x00AA00);
    }

    /** Convenience: red error embed */
    public static void sendError(String url, String title, String desc) {
        send(url, title, desc, 0xFF0000);
    }

    /** Convenience: orange warning embed */
    public static void sendWarn(String url, String title, String desc) {
        send(url, title, desc, 0xFF8800);
    }
}
