package ru.liko.warbornradios.voice;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audio.AudioConverter;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ForgeVoicechatPlugin
public class WRBRadioVoicePlugin implements VoicechatPlugin {

    public static final String PLUGIN_ID = "warbornradios";
    public static final String CATEGORY_ID = "wrb_radio";

    public static VoicechatApi API;
    public static VoicechatServerApi SERVER;

    // Per-player radio state (very small PoC)
    public static final Map<UUID, RadioState> STATES = new ConcurrentHashMap<>();
    // Stable channel IDs per sender to keep packets in one stream
    private static final Map<UUID, UUID> STREAM_IDS = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        API = api; // Keep reference to access converters/encoders later
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onServerStopped);
        registration.registerEvent(PlayerConnectedEvent.class, this::onPlayerConnected);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::onPlayerDisconnected);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket, 100);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        SERVER = event.getVoicechat();
        // Optional: register a volume category so clients can control radio volume
        VolumeCategory cat = API.volumeCategoryBuilder()
                .setId(CATEGORY_ID)
                .setName("Radios")
                .setDescription("WarBorn Radios")
                .build();
        SERVER.registerVolumeCategory(cat);
    }

    private void onServerStopped(VoicechatServerStoppedEvent event) {
        SERVER = null;
        STATES.clear();
        STREAM_IDS.clear();
    }

    private void onPlayerConnected(PlayerConnectedEvent event) {
        ServerPlayer sp = event.getConnection().getPlayer();
        RadioState st = new RadioState();
        st.modelId = "handheld";
        st.freqMHz = 145.500;
        st.mode = Mode.FM;
        st.ctcss = -1;
        st.key = "";
        st.powerDbm = 37.0;
        STATES.put(sp.getUUID(), st);
    }

    private void onPlayerDisconnected(PlayerDisconnectedEvent event) {
        ServerPlayer sp = event.getConnection().getPlayer();
        STATES.remove(sp.getUUID());
        STREAM_IDS.remove(sp.getUUID());
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        if (SERVER == null) return;

        VoicechatConnection senderConn = event.getSenderConnection();
        if (senderConn == null) return;

        ServerPlayer sender = senderConn.getPlayer();
        if (sender == null) return;

        RadioState tx = STATES.get(sender.getUUID());
        if (tx == null) return;

        // Who should receive this transmission?
        List<ServerPlayer> receivers = RadioLink.findReceivers(SERVER, senderConn, tx);
        if (receivers.isEmpty()) {
            // Prevent proximity voice from also playing - this is a radio only mod
            event.cancel();
            return;
        }

        MicrophonePacket mic = event.getPacket();

        // Build a static sound packet based on the microphone packet
        UUID stream = STREAM_IDS.computeIfAbsent(sender.getUUID(), u -> UUID.randomUUID());
        StaticSoundPacket.Builder<?> builder = mic.staticSoundPacketBuilder()
                .channelId(stream)
                .category(CATEGORY_ID);

        byte[] data = mic.getOpusEncodedData();

        // (Optional) DSP path: decode -> process -> encode
        // Disabled by default for simplicity; uncomment to enable
        /*
        try (var decoder = API.createDecoder(); var encoder = API.createEncoder()) {
            short[] pcm = decoder.decode(data);
            if (pcm != null && pcm.length > 0) {
                // Very rough SNR estimate; can be improved using terrain/LOS
                for (ServerPlayer rxPlayer : receivers) {
                    RadioState rx = STATES.getOrDefault(rxPlayer.getUUID(), tx);
                    double snr = RadioLink.estimateSnr(sender, rxPlayer, tx, rx);
                    short[] processed = RadioDSP.process(pcm, tx, rx, snr);
                    byte[] encoded = encoder.encode(processed);
                    sendTo(rxPlayer, builder.opusEncodedData(encoded).build());
                }
                event.cancel();
                return;
            }
        } catch (Exception ignored) {}
        */

        // Fallback: just relay the original opus data to all receivers
        for (ServerPlayer rxPlayer : receivers) {
            sendTo(rxPlayer, builder.opusEncodedData(data).build());
        }

        // Cancel default proximity delivery
        event.cancel();
    }

    private void sendTo(ServerPlayer player, StaticSoundPacket packet) {
        VoicechatConnection c = SERVER.getConnectionOf(player.getUUID());
        if (c != null) {
            SERVER.sendStaticSoundPacketTo(c, packet);
        }
    }

    // --- State types ---

    public static class RadioState {
        public String modelId;
        public double freqMHz;
        public Mode mode; // FM, AM, DMR (DMR not implemented here)
        public boolean transmitting;
        public int ctcss; // -1 = off
        public String key; // simple "crypto" placeholder
        public double powerDbm; // 30..40 approx
    }

    public enum Mode { FM, AM, DMR }
}
