package net.minelight.core.midi;

import net.minelight.core.api.GameEvent;
import net.minelight.core.api.MidiTriggerSource;
import net.minelight.core.engine.ConsoleEngine;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MIDI trigger source.
 *
 * <p>Connects to any MIDI controller visible to the JVM — USB devices like
 * the DJ TechTools Midi Fighter Twister, virtual ports, loopMIDI, etc. —
 * and translates Note On / Note Off / CC messages into game events and
 * direct fixture control.</p>
 *
 * <p>Mapping convention (customizable per profile):</p>
 * <ul>
 *   <li>Note On  -> {@code midi.noteon} event, plus momentary fixture bump</li>
 *   <li>Note Off -> {@code midi.noteoff} event</li>
 *   <li>CC       -> {@code midi.cc} event, plus mapped to fixture intensity
 *                  when a binding exists</li>
 * </ul>
 */
public final class MidiService implements MidiTriggerSource, AutoCloseable {

    private final ConsoleEngine engine;
    private final List<MidiListener> listeners = new CopyOnWriteArrayList<>();
    private final List<MidiDevice> openDevices = new ArrayList<>();

    /** CC bindings: (channel, ccNumber) -> fixtureId. */
    private final java.util.Map<String, Integer> ccBindings = new java.util.concurrent.ConcurrentHashMap<>();
    /** Note bindings: (channel, note) -> fixtureId. */
    private final java.util.Map<String, Integer> noteBindings = new java.util.concurrent.ConcurrentHashMap<>();

    public MidiService(ConsoleEngine engine) {
        this.engine = engine;
    }

    @Override
    public void addListener(MidiListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(MidiListener listener) {
        listeners.remove(listener);
    }

    /** Bind a CC to a fixture intensity. */
    public void bindCc(int channel, int cc, int fixtureId) {
        ccBindings.put(channel + ":" + cc, fixtureId);
    }

    /** Bind a note to a fixture (note-on bumps to velocity, note-off to 0). */
    public void bindNote(int channel, int note, int fixtureId) {
        noteBindings.put(channel + ":" + note, fixtureId);
    }

    /** List available MIDI input device names. */
    public static List<String> availableInputs() {
        List<String> out = new ArrayList<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                if (dev.getMaxTransmitters() != 0) {
                    out.add(info.getName());
                }
            } catch (MidiUnavailableException ignored) {
            }
        }
        return out;
    }

    /** Open all available MIDI input devices. */
    public void openAll() {
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                if (dev.getMaxTransmitters() == 0) {
                    continue;
                }
                if (!dev.isOpen()) {
                    dev.open();
                }
                Transmitter t = dev.getTransmitter();
                t.setReceiver(new Router());
                openDevices.add(dev);
                System.out.println("[MineLight] MIDI connected: " + info.getName());
            } catch (MidiUnavailableException e) {
                System.err.println("[MineLight] MIDI open failed for " + info.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Open a specific device by name substring. */
    public boolean open(String nameSubstring) {
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            if (!info.getName().toLowerCase().contains(nameSubstring.toLowerCase())) {
                continue;
            }
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                if (dev.getMaxTransmitters() == 0) {
                    continue;
                }
                if (!dev.isOpen()) {
                    dev.open();
                }
                dev.getTransmitter().setReceiver(new Router());
                openDevices.add(dev);
                System.out.println("[MineLight] MIDI connected: " + info.getName());
                return true;
            } catch (MidiUnavailableException ignored) {
            }
        }
        return false;
    }

    @Override
    public void close() {
        for (MidiDevice d : openDevices) {
            try {
                d.close();
            } catch (Exception ignored) {
            }
        }
        openDevices.clear();
    }

    private final class Router implements Receiver {
        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (!(message instanceof ShortMessage sm)) {
                return;
            }
            int channel = sm.getChannel() + 1; // 1-16 for humans
            int cmd = sm.getCommand();
            int number = sm.getData1();
            int value = sm.getData2();

            String type = switch (cmd) {
                case ShortMessage.NOTE_ON -> value == 0 ? "noteoff" : "noteon";
                case ShortMessage.NOTE_OFF -> "noteoff";
                case ShortMessage.CONTROL_CHANGE -> "cc";
                default -> null;
            };
            if (type == null) {
                return;
            }

            // notify listeners
            for (MidiListener l : listeners) {
                try {
                    l.onMidi(channel, type, number, value);
                } catch (Exception ignored) {
                }
            }

            // emit game event
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("channel", channel);
            data.put("number", number);
            data.put("value", value);
            engine.emit(new GameEvent("midi." + type, data));

            // direct bindings
            if ("cc".equals(type)) {
                Integer fid = ccBindings.get(channel + ":" + number);
                if (fid != null) {
                    engine.setFixtureIntensity(fid, value * 2); // 0-127 -> 0-254
                }
            } else if ("noteon".equals(type)) {
                Integer fid = noteBindings.get(channel + ":" + number);
                if (fid != null) {
                    engine.setFixtureIntensity(fid, value * 2);
                }
            } else if ("noteoff".equals(type)) {
                Integer fid = noteBindings.get(channel + ":" + number);
                if (fid != null) {
                    engine.setFixtureIntensity(fid, 0);
                }
            }
        }

        @Override
        public void close() {
        }
    }
}
