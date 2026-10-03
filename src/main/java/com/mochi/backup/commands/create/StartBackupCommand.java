package com.mochi.backup.commands.create;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import com.mochi.backup.Globals;
import com.mochi.backup.MochiClient;
import com.mochi.backup.MochiLogger;
import com.mochi.backup.core.create.ExecutableBackup;

import org.jetbrains.annotations.Nullable;

public class StartBackupCommand {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("start")
                .then(Commands.argument("comment", StringArgumentType.string())
                        .executes(ctx -> execute(ctx.getSource(), StringArgumentType.getString(ctx, "comment"))))
                .executes(ctx -> execute(ctx.getSource(), null));
    }

    private static int execute(CommandSourceStack source, @Nullable String comment) {
        var executor = Globals.INSTANCE.getQueueExecutor();
        if (executor == null || executor.isShutdown()) {
            log.error("Backup executor is not available (server may be shutting down).");
            return 0;
        }
        executor.submit(
                ExecutableBackup.Builder.newBackupContextBuilder()
                        .setCommandSource(source)
                        .setComment(comment)
                        .guessInitiator()
                        .saveServer()
                        .build()
        );
        return 1;
    }
}
