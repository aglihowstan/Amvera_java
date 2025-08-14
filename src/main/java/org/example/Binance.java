package org.example;

import java.net.URI;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class Binance {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ScheduledExecutorService controlScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final BlockingQueue<String> controlQueue = new LinkedBlockingQueue<>();
    private static WebSocketClient ws;
    private static RateTracker rateTracker = new RateTracker();
    private static ExponentialBackoff backoff = new ExponentialBackoff();

    public static void main(String[] args) throws Exception {
        URI uri = new URI("wss://stream.binance.us:9443/ws");
        ws = new WebSocketClient(uri) {
            @Override public void onOpen(ServerHandshake h) {
                System.out.println("WS Open");
                enqueue(subscribeJson("BTCUSDT"));
            }
            @Override public void onMessage(String msg) {
                handleMessage(msg);
            }
            @Override public void onClose(int code, String reason, boolean remote) {
                System.err.println("Closed: " + code + " reason=" + reason);
                scheduleReconnect();
            }
            @Override public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };
        ws.connect();
        controlScheduler.scheduleAtFixedRate(() -> {
            String cmd = controlQueue.poll();
            if (cmd != null) ws.send(cmd);
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    private static String subscribeJson(String symbol) {
        ObjectNode n = mapper.createObjectNode();
        n.put("method", "SUBSCRIBE");
        n.putArray("params").add(symbol.toLowerCase() + "@miniTicker");
        n.put("id", 1);
        n.put("returnRateLimits", true);
        return n.toString();
    }

    private static void enqueue(String json) {
        controlQueue.offer(json);
    }

    private static void handleMessage(String msg) {
        try {
            JsonNode n = mapper.readTree(msg);
            if (n.has("rateLimits")) {
                rateTracker.update(n.get("rateLimits"));
            } else if (n.has("e")) { // market data
                String symbol = n.get("s").asText();
                String price = n.has("c") ? n.get("c").asText() : n.get("p").asText();
                System.out.println(symbol + " → " + price);
            } else if (n.has("status") && n.get("status").asInt() == 429) {
                int ra = n.has("retryAfter") ? n.get("retryAfter").asInt() : 1;
                System.err.println("Got 429, retrying after " + ra + "s");
                pauseAndReconnect(ra);
            } else if (n.has("status") && n.get("status").asInt() == 418) {
                long ts = n.get("retryAfter").asLong();
                System.err.println("IP banned until " + ts);
                pauseAndReconnect((int)((ts - System.currentTimeMillis())/1000));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void pauseAndReconnect(int seconds) {
        controlQueue.clear();
        scheduleReconnectDelay(seconds);
    }

    private static void scheduleReconnect() {
        scheduleReconnectDelay(backoff.next());
    }

    private static void scheduleReconnectDelay(int delaySec) {
        System.err.println("Reconnect in " + delaySec + "s");
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.schedule(() -> {
            backoff.reset();
            ws.reconnect();
        }, delaySec, TimeUnit.SECONDS);
    }

    static class RateTracker {
        private int current = 0, limit = Integer.MAX_VALUE;
        void update(JsonNode arr) {
            arr.forEach(elem -> {
                if ("REQUEST_WEIGHT".equals(elem.get("rateLimitType").asText()) &&
                        "MINUTE".equals(elem.get("interval").asText())) {
                    current = elem.get("count").asInt();
                    limit = elem.get("limit").asInt();
                    System.out.println("Used weight/min: " + current + "/" + limit);
                }
            });
        }
        boolean wouldExceed(int cost) {
            return current + cost > limit;
        }
    }

    static class ExponentialBackoff {
        private int attempt = 0;
        int next() {
            attempt++;
            return Math.min(60, (int)Math.pow(2, attempt));
        }
        void reset() { attempt = 0; }
    }
}
