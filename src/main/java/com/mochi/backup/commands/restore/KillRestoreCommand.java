package com.mochi.backup.commands.restore;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.Commands;

import com.mochi.backup.Globals;
import com.mochi.backup.MochiLogger;
import com.mochi.backup.MochiClient;
import com.mochi.backup.Utilities;
import com.mochi.backup.core.restore.AwaitThread;

public class KillRestoreCommand {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);

    public static LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> register() {
        return Commands.literal("killR")
                .executes(ctx -> {
                    if (Globals.INSTANCE.getAwaitThread().filter(Thread::isAlive).isEmpty()) {
                        log.sendInfo(ctx.getSource(), "No restore in progress.");
                        return -1;
                    }
                    AwaitThread thread = Globals.INSTANCE.getAwaitThread().get();
                    thread.interrupt();
                    Globals.INSTANCE.globalShutdownBackupFlag.set(true);
                    Globals.INSTANCE.setLockedFile(null);
                    String who = Utilities.wasSentByPlayer(ctx.getSource()) ? "Player: " + ctx.getSource().getTextName() : "SERVER";
                    log.info("{} cancelled restore.", who);
                    log.sendInfo(ctx.getSource(), "Restore cancelled.");
                    return 1;
                });
    }
}
