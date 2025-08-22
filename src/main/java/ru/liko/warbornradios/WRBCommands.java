package ru.liko.warbornradios;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import ru.liko.warbornradios.voice.WRBRadioVoicePlugin;

import java.util.Locale;

public class WRBCommands {

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("wrb")
            .then(Commands.literal("freq")
                .then(Commands.argument("mhz", DoubleArgumentType.doubleArg(30.0, 520.0))
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        double mhz = DoubleArgumentType.getDouble(ctx, "mhz");
                        var st = WRBRadioVoicePlugin.STATES.computeIfAbsent(sp.getUUID(), u -> new WRBRadioVoicePlugin.RadioState());
                        st.freqMHz = mhz;
                        ctx.getSource().sendSuccess(() -> Component.literal("WRB: frequency set to %.4f MHz".formatted(mhz)), false);
                        return 1;
                    })))
            .then(Commands.literal("ctcss")
                .then(Commands.argument("code", IntegerArgumentType.integer(-1, 250))
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        int code = IntegerArgumentType.getInteger(ctx, "code");
                        var st = WRBRadioVoicePlugin.STATES.computeIfAbsent(sp.getUUID(), u -> new WRBRadioVoicePlugin.RadioState());
                        st.ctcss = code;
                        String txt = (code < 0) ? "off" : Integer.toString(code);
                        ctx.getSource().sendSuccess(() -> Component.literal("WRB: CTCSS set to " + txt), false);
                        return 1;
                    })))
            .then(Commands.literal("mode")
                .then(Commands.argument("mode", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        String m = StringArgumentType.getString(ctx, "mode").toUpperCase(Locale.ROOT);
                        WRBRadioVoicePlugin.Mode mode = WRBRadioVoicePlugin.Mode.valueOf(m);
                        var st = WRBRadioVoicePlugin.STATES.computeIfAbsent(sp.getUUID(), u -> new WRBRadioVoicePlugin.RadioState());
                        st.mode = mode;
                        ctx.getSource().sendSuccess(() -> Component.literal("WRB: mode set to " + mode), false);
                        return 1;
                    })))
        );
    }
}
