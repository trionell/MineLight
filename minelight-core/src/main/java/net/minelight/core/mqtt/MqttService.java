package net.minelight.core.mqtt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minelight.core.api.GameEvent;
import net.minelight.core.engine.ConsoleEngine;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * MQTT bridge.
 *
 * <p>Connects MineLight to home-automation and IoT ecosystems (Home
 * Assistant, Node-RED, OpenHAB). Publishes fixture/redstone state and
 * subscribes to commands.</p>
 *
 * <p>Topics (prefix {@code minelight/} by default):</p>
 * <ul>
 *   <li>{@code minelight/fixture/<id>/set}  payload {"intensity":0-255} or {"levels":[...]}</li>
 *   <li>{@code minelight/fixture/<id>/state} published on change</li>
 *   <li>{@code minelight/event} payload {"kind":"custom.x","data":{...}}</li>
 *   <li>{@code minelight/redstone/<id>} retained on/off</li>
 * </ul>
 *
 * <p>MQTT is optional: if the broker is unreachable the service logs and
 * idles; the rest of the engine keeps working.</p>
 */
public final class MqttService implements AutoCloseable {

    private static final Gson GSON = new Gson();

    private final ConsoleEngine engine;
    private final String broker;
    private final String topicPrefix;
    private MqttClient client;
    private volatile boolean connected;

    public MqttService(ConsoleEngine engine, String broker) {
        this(engine, broker, "minelight");
    }

    public MqttService(ConsoleEngine engine, String broker, String topicPrefix) {
        this.engine = engine;
        this.broker = broker;
        this.topicPrefix = topicPrefix;
    }

    public boolean isConnected() {
        return connected;
    }

    public void connect() {
        try {
            client = new MqttClient(broker, "minelight-" + System.nanoTime(), new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            client.setCallback(new Callback());
            client.connect(opts);
            client.subscribe(topicPrefix + "/fixture/+/set");
            client.subscribe(topicPrefix + "/event");
            connected = true;
            System.out.println("[MineLight] MQTT connected to " + broker);
        } catch (Exception e) {
            System.err.println("[MineLight] MQTT unavailable: " + e.getMessage());
            connected = false;
        }
    }

    public void publishFixtureState(int fixtureId, int[] levels) {
        if (!connected) {
            return;
        }
        JsonObject o = new JsonObject();
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (int v : levels) {
            arr.add(v);
        }
        o.add("levels", arr);
        publish(topicPrefix + "/fixture/" + fixtureId + "/state", o.toString(), false);
    }

    public void publishRedstone(int fixtureId, boolean on) {
        publish(topicPrefix + "/redstone/" + fixtureId, on ? "ON" : "OFF", true);
    }

    private void publish(String topic, String payload, boolean retained) {
        if (!connected) {
            return;
        }
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setRetained(retained);
            client.publish(topic, msg);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        connected = false;
        if (client != null) {
            try {
                client.disconnect();
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private final class Callback implements MqttCallback {
        @Override
        public void connectionLost(Throwable cause) {
            connected = false;
            System.err.println("[MineLight] MQTT connection lost: " + cause.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                if (topic.endsWith("/set")) {
                    // minelight/fixture/<id>/set
                    String[] parts = topic.split("/");
                    int id = Integer.parseInt(parts[2]);
                    JsonObject o = GSON.fromJson(payload, JsonObject.class);
                    if (o.has("intensity")) {
                        engine.setFixtureIntensity(id, o.get("intensity").getAsInt());
                    } else if (o.has("levels")) {
                        var arr = o.getAsJsonArray("levels");
                        int[] levels = new int[arr.size()];
                        for (int i = 0; i < levels.length; i++) {
                            levels[i] = arr.get(i).getAsInt();
                        }
                        engine.setFixtureLevels(id, levels);
                    }
                } else if (topic.equals(topicPrefix + "/event")) {
                    JsonObject o = GSON.fromJson(payload, JsonObject.class);
                    String kind = o.get("kind").getAsString();
                    Map<String, Object> data = new HashMap<>();
                    if (o.has("data")) {
                        o.getAsJsonObject("data").entrySet().forEach(e ->
                                data.put(e.getKey(), e.getValue().getAsString()));
                    }
                    engine.emit(new GameEvent(kind, data));
                }
            } catch (Exception e) {
                System.err.println("[MineLight] MQTT message error: " + e.getMessage());
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }
}
