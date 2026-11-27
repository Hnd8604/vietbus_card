package com.buscard.desktop.net;

import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.LockReason;
import com.buscard.common.CardConstants.PassType;
import com.buscard.common.CardConstants.RouteMode;
import com.buscard.common.CardIds;
import com.buscard.common.CardInfo;
import com.buscard.common.IssuerAuthority;
import com.buscard.common.RsaKeys;
import com.buscard.common.TxRecord;
import com.buscard.desktop.net.OfflineTxQueue.PendingTx;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Communicates with the Spring Boot backend REST endpoints, with Store-and-Forward offline support. */
public class BackendClient implements IssuerAuthority {

    public static class BackendRejectedException extends IOException {
        private final int statusCode;

        public BackendRejectedException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    public record CardLookupResult(boolean backendOnline, boolean found, CardSummary card) {
    }

    private volatile String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json;
    private final OfflineTxQueue offlineQueue;
    private final List<Runnable> queueListeners = new CopyOnWriteArrayList<>();

    public BackendClient(String baseUrl) {
        this.baseUrl = normalizeUrl(baseUrl);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.json = new ObjectMapper().registerModule(new JavaTimeModule());
        this.offlineQueue = new OfflineTxQueue();
    }

    private static String normalizeUrl(String url) {
        return url == null ? "http://localhost:8080" : url.trim().replaceAll("/+$", "");
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String newBaseUrl) {
        this.baseUrl = normalizeUrl(newBaseUrl);
    }

    public void addQueueListener(Runnable listener) {
        queueListeners.add(listener);
    }

    private void notifyQueueChanged() {
        queueListeners.forEach(Runnable::run);
    }

    public int getPendingCount() {
        return offlineQueue.size();
    }

    public List<PendingTx> getPendingTransactions() {
        return offlineQueue.loadQueue();
    }

    public boolean isOnline() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/routes"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public RSAPublicKey getIssuerPublicKey() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/issuer/public-key"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp);
        Map<?, ?> m = json.readValue(resp.body(), Map.class);
        String b64 = (String) m.get("publicKeyBase64");
        return (RSAPublicKey) RsaKeys.fromBase64(b64);
    }

    @Override
    public byte[] sign(byte[] challenge, byte[] cardId, int ins, byte[] payload) {
        try {
            Map<String, Object> body = Map.of(
                    "cardId", CardIds.toText(cardId),
                    "ins", ins,
                    "challengeBase64", Base64.getEncoder().encodeToString(challenge),
                    "payloadBase64", payload == null ? "" : Base64.getEncoder().encodeToString(payload)
            );
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/issuer/sign"))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
            Map<?, ?> m = json.readValue(resp.body(), Map.class);
            String sigB64 = (String) m.get("signatureBase64");
            return Base64.getDecoder().decode(sigB64);
        } catch (Exception e) {
            throw new RuntimeException("Backend issuer sign failed: " + e.getMessage(), e);
        }
    }

    public void registerCard(String cardId, String publicKeyBase64, Category category, LocalDate cardExpiry)
            throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "cardId", cardId,
                "publicKeyBase64", publicKeyBase64,
                "category", category.name(),
                "cardExpiry", cardExpiry.toString()
        );
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/cards"))
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp);
        CardSummary summary = json.readValue(resp.body(), CardSummary.class);
        offlineQueue.cacheCard(summary);
    }

    public CardLookupResult lookupCard(String cardId) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/cards/" + cardId))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                return new CardLookupResult(true, false, null);
            }
            checkStatus(resp);
            CardSummary card = json.readValue(resp.body(), CardSummary.class);
            offlineQueue.cacheCard(card);
            return new CardLookupResult(true, true, card);
        } catch (Exception e) {
            CardSummary cached = offlineQueue.getCachedCard(cardId);
            return new CardLookupResult(false, cached != null, cached);
        }
    }

    public void syncCard(CardInfo info) {
        if (info == null || info.cardId() == null || info.cardId().isEmpty()) return;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("state", info.state().name());
            body.put("lockReason", info.lockReason().name());
            body.put("category", info.category().name());
            body.put("cardExpiry", info.cardExpiry().toString());
            body.put("balance", info.balance());
            body.put("passType", info.passType().name());
            body.put("passRoute", info.passRoute());
            body.put("passExpiry", info.passExpiry() != null ? info.passExpiry().toString() : null);
            body.put("lastTxCounter", info.txCounter());

            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/cards/" + info.cardId() + "/sync"))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                CardSummary card = json.readValue(resp.body(), CardSummary.class);
                offlineQueue.cacheCard(card);
            }
        } catch (Exception ignored) {
        }
    }

    public void recordTransaction(byte[] raw, byte[] signature, PassType passType, Integer passRoute, LocalDate passExpiry)
            throws IOException, InterruptedException {
        recordTransactionRaw(
                Base64.getEncoder().encodeToString(raw),
                Base64.getEncoder().encodeToString(signature),
                passType, passRoute, passExpiry);
    }

    private void recordTransactionRaw(String rawBase64, String signatureBase64,
                                      PassType passType, Integer passRoute, LocalDate passExpiry)
            throws IOException, InterruptedException {
        HashMap<String, Object> body = new HashMap<>();
        body.put("rawBase64", rawBase64);
        body.put("signatureBase64", signatureBase64);
        if (passType != null) {
            body.put("passType", passType.name());
        }
        if (passRoute != null) {
            body.put("passRoute", passRoute);
        }
        if (passExpiry != null) {
            body.put("passExpiry", passExpiry.toString());
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/transactions"))
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp);
    }

    /**
     * Attempts to send the signed transaction to Backend immediately.
     * If Backend is unreachable (offline), persists it into the local Store-and-Forward queue.
     *
     * @return {@code true} if sent online immediately; {@code false} if queued offline.
     * @throws BackendRejectedException if Backend is online and explicitly rejected the transaction (e.g. invalid RSA signature).
     */
    public boolean recordTransactionOrQueue(TxRecord tx, PassType passType, Integer passRoute, LocalDate passExpiry)
            throws BackendRejectedException {
        String rawB64 = Base64.getEncoder().encodeToString(tx.raw());
        String sigB64 = Base64.getEncoder().encodeToString(tx.signature());
        try {
            recordTransactionRaw(rawB64, sigB64, passType, passRoute, passExpiry);
            return true;
        } catch (BackendRejectedException rejected) {
            throw rejected;
        } catch (Exception networkError) {
            offlineQueue.enqueue(new PendingTx(
                    tx.cardId(),
                    tx.counter(),
                    tx.type().name(),
                    tx.amount(),
                    tx.route(),
                    tx.station(),
                    rawB64,
                    sigB64,
                    passType,
                    passRoute,
                    passExpiry,
                    LocalDateTime.now()));
            notifyQueueChanged();
            return false;
        }
    }

    /** Replays pending offline transactions to Backend in order. Returns number of transactions synced. */
    public synchronized int syncPendingQueue() {
        List<PendingTx> pending = offlineQueue.loadQueue();
        if (pending.isEmpty()) {
            return 0;
        }
        int synced = 0;
        List<PendingTx> remaining = new ArrayList<>();
        boolean networkBroken = false;

        for (PendingTx item : pending) {
            if (networkBroken) {
                remaining.add(item);
                continue;
            }
            try {
                recordTransactionRaw(item.rawBase64(), item.signatureBase64(),
                        item.passType(), item.passRoute(), item.passExpiry());
                synced++;
            } catch (BackendRejectedException rejected) {
                if (rejected.getStatusCode() == 409) {
                    // Already recorded / duplicate counter: safely treat as synced and drop from queue
                    synced++;
                } else {
                    remaining.add(item);
                }
            } catch (Exception networkError) {
                networkBroken = true;
                remaining.add(item);
            }
        }

        offlineQueue.replaceQueue(remaining);
        if (synced > 0) {
            notifyQueueChanged();
        }
        return synced;
    }

    public List<RouteInfo> getRoutes() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/routes"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                List<RouteInfo> routes = json.readerForListOf(RouteInfo.class).readValue(resp.body());
                offlineQueue.cacheRoutes(routes);
                return routes;
            }
        } catch (Exception ignored) {
        }
        return offlineQueue.loadCachedRoutes();
    }

    public List<TxSummary> getCardTransactions(String cardId) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/transactions/card/" + cardId))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return Collections.emptyList();
            }
            return json.readerForListOf(TxSummary.class).readValue(resp.body());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<TxSummary> listAllTransactions() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/transactions"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return Collections.emptyList();
            }
            return json.readerForListOf(TxSummary.class).readValue(resp.body());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<CardSummary> listAllCards() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/cards"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return Collections.emptyList();
            }
            List<CardSummary> cards = json.readerForListOf(CardSummary.class).readValue(resp.body());
            offlineQueue.cacheCards(cards);
            return cards;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void checkStatus(HttpResponse<String> resp) throws IOException {
        if (resp.statusCode() >= 400) {
            String errorMsg = resp.body();
            try {
                Map<?, ?> m = json.readValue(resp.body(), Map.class);
                if (m.containsKey("error")) {
                    errorMsg = m.get("error").toString();
                }
            } catch (Exception ignored) {
            }
            throw new BackendRejectedException(resp.statusCode(), "HTTP " + resp.statusCode() + ": " + errorMsg);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StationInfo(int stationId, int sequenceOrder, String name) {
        @Override
        public String toString() {
            return name + " (#" + stationId + ")";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteInfo(int routeId, String code, String name, RouteMode mode,
                            long baseFare, long farePerStation, long maxFare,
                            long monthlyPassPrice, List<StationInfo> stations) {
        @Override
        public String toString() {
            return name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CardSummary(String cardId, String publicKeyX509, CardState state, LockReason lockReason,
                              Category category, LocalDate cardExpiry, long balance,
                              PassType passType, int passRoute, LocalDate passExpiry, long lastTxCounter) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TxSummary(Long id, String cardId, long txCounter, String txType,
                            long amount, long balanceAfter, int routeId, int stationId,
                            String txTime, int flags, boolean verified) {
    }
}
