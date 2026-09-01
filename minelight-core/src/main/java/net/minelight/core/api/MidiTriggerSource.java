package net.minelight.core.api;

/**
 * Source of MIDI-style trigger messages. Implemented by the MIDI module
 * (hardware controllers like the Midi Fighter Twister) and by the virtual
 * MIDI bridge used by the WebPanel's on-screen buttons.
 */
public interface MidiTriggerSource {

    @FunctionalInterface
    interface MidiListener {
        /**
         * @param channel MIDI channel 1-16
         * @param type    "noteon", "noteoff", "cc"
         * @param number  note or controller number 0-127
         * @param value   velocity or controller value 0-127
         */
        void onMidi(int channel, String type, int number, int value);
    }

    void addListener(MidiListener listener);

    void removeListener(MidiListener listener);
}
