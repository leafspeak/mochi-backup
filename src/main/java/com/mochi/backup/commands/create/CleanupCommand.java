package com.mochi.backup.commands.create;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.Commands;

import com.mochi.backup.MochiLogger;
import com.mochi.backup.MochiClient;
import com.mochi.backup.core.Cleanup;
import com.mochi.backup.Utilities;

public class CleanupCommand {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);

    public static LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> register() {
        return Commands.literal("cleanup")
                .executes(ctx -> {
                    int n = new Cleanup(ctx.getSource(), Utilities.getLevelName(ctx.getSource().getServer())).call();
                    log.sendInfo(ctx.getSource(), "Cleaned {} backup(s).", n);
                    return 1;
                });
    }
}
