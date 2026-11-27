package com.buscard.desktop.net;

import com.buscard.common.CardConstants.PassType;
import com.buscard.desktop.card.CardSlotManager;
import com.buscard.desktop.net.BackendClient.CardSummary;
import com.buscard.desktop.net.BackendClient.RouteInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local Store-and-Forward persistence for offline bus terminal operation:
 * stores pending card-signed transactions, cached bus routes, and cached card registry keys.
 */
public class OfflineTxQueue {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PendingTx(
            String cardId,
            long txCounter,
            String txType,
            long amount,
            int routeId,
            int stationId,
            String rawBase64,
            String signatureBase64,
            PassType passType,
            Integer passRoute,
            LocalDate passExpiry,
            LocalDateTime queuedAt) {
    }

    private final Path queueFile;
    private final Path routesCacheFile;
    private final Path cardsCacheFile;
    private final ObjectMapper json;

    public OfflineTxQueue() {
        Path dir = CardSlotManager.getCardsDir();
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        this.queueFile = dir.resolve("pending_tx_queue.json");
        this.routesCacheFile = dir.resolve("routes_cache.json");
        this.cardsCacheFile = dir.resolve("cards_cache.json");
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public synchronized void enqueue(PendingTx tx) {
        List<PendingTx> list = new ArrayList<>(loadQueue());
        boolean exists = list.stream().anyMatch(p ->
                p.cardId().equals(tx.cardId()) && p.txCounter() == tx.txCounter());
        if (!exists) {
            list.add(tx);
            saveJson(queueFile, list);
        }
    }

    public synchronized List<PendingTx> loadQueue() {
        if (!Files.exists(queueFile)) {
            return Collections.emptyList();
        }
        try {
            return json.readerForListOf(PendingTx.class).readValue(queueFile.toFile());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public synchronized void replaceQueue(List<PendingTx> remaining) {
        saveJson(queueFile, remaining);
    }

    public synchronized int size() {
        return loadQueue().size();
    }

    public synchronized void cacheRoutes(List<RouteInfo> routes) {
        if (routes != null && !routes.isEmpty()) {
            saveJson(routesCacheFile, routes);
        }
    }

    public synchronized List<RouteInfo> loadCachedRoutes() {
        if (!Files.exists(routesCacheFile)) {
            return Collections.emptyList();
        }
        try {
            return json.readerForListOf(RouteInfo.class).readValue(routesCacheFile.toFile());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public synchronized void cacheCard(CardSummary card) {
        if (card == null || card.cardId() == null) return;
        Map<String, CardSummary> map = new HashMap<>(loadCachedCards());
        map.put(card.cardId(), card);
        saveJson(cardsCacheFile, map);
    }

    public synchronized void cacheCards(List<CardSummary> cards) {
        if (cards == null) return;
        Map<String, CardSummary> map = new HashMap<>(loadCachedCards());
        for (CardSummary c : cards) {
            if (c.cardId() != null) {
                map.put(c.cardId(), c);
            }
        }
        saveJson(cardsCacheFile, map);
    }

    public synchronized CardSummary getCachedCard(String cardId) {
        return loadCachedCards().get(cardId);
    }

    private Map<String, CardSummary> loadCachedCards() {
        if (!Files.exists(cardsCacheFile)) {
            return Collections.emptyMap();
        }
        try {
            return json.readValue(cardsCacheFile.toFile(),
                    json.getTypeFactory().constructMapType(HashMap.class, String.class, CardSummary.class));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private void saveJson(Path path, Object value) {
        try {
            json.writeValue(path.toFile(), value);
        } catch (Exception ignored) {
        }
    }
}
