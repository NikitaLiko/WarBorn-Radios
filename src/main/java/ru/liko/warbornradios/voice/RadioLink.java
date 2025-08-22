package ru.liko.warbornradios.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ru.liko.warbornradios.voice.WRBRadioVoicePlugin.RadioState;
import static ru.liko.warbornradios.voice.WRBRadioVoicePlugin.STATES;

public class RadioLink {

    public static List<ServerPlayer> findReceivers(VoicechatServerApi api,
                                                   VoicechatConnection sender, RadioState tx) {
        ServerPlayer sp = sender.getPlayer();
        if (sp == null) return List.of();

        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer rx : sp.getServer().getPlayerList().getPlayers()) {
            if (rx.getUUID().equals(sp.getUUID())) continue;

            var conn = api.getConnectionOf(rx.getUUID());
            if (conn == null) continue;

            RadioState state = STATES.get(rx.getUUID());
            if (state == null) continue;

            if (!sameChannel(tx, state)) continue;
            if (!passSquelch(tx, state)) continue;
            if (!hasLineOfSight(sp, rx)) continue;

            // Distance gate (super rough free-space-ish)
            double d = sp.distanceTo(rx);
            double max = rangeFromPower(tx.powerDbm);
            if (d > max) continue;

            out.add(rx);
        }
        return out;
    }

    public static double estimateSnr(ServerPlayer tx, ServerPlayer rx, RadioState ts, RadioState rs) {
        double d = tx.distanceTo(rx);
        double max = rangeFromPower(ts.powerDbm);
        if (d <= 1) return 40;
        double t = Math.max(0, Math.min(1, d / max));
        return 35 * (1 - t); // 35 dB near, ~0 dB at edge
    }

    private static double rangeFromPower(double pDbm) {
        // Not physically correct; just a handy curve: 30 dBm -> ~400m, 40 dBm -> ~1200m
        double watts = Math.pow(10, (pDbm - 30) / 10.0);
        return 300 * Math.sqrt(watts);
    }

    private static boolean sameChannel(RadioState a, RadioState b) {
        return Math.abs(a.freqMHz - b.freqMHz) < 0.0125 // 12.5 kHz step
                && a.mode == b.mode;
    }

    private static boolean passSquelch(RadioState tx, RadioState rx) {
        return rx.ctcss < 0 || rx.ctcss == tx.ctcss;
    }

    private static boolean hasLineOfSight(ServerPlayer a, ServerPlayer b) {
        // TODO: raycast through blocks; assume LOS for prototype
        return true;
    }
}
