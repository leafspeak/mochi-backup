package com.mochi.backup.commands.manage;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import com.mochi.backup.MochiLogger;
import com.mochi.backup.MochiClient;
import com.mochi.backup.MochiConfigHelper;

public class WhitelistCommand {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);
    private final static MochiConfigHelper config = MochiConfigHelper.INSTANCE;

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("whitelist")
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(WhitelistCommand::executeAdd)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(WhitelistCommand::executeRemove)))
                .then(Commands.literal("list")
                        .executes(ctx -> executeList(ctx.getSource())))
                .executes(ctx -> help(ctx.getSource()));
    }

    private static int help(CommandSourceStack s) {
        log.sendInfo(s, "Commands: add [player], remove [player], list");
        return 1;
    }

    private static int executeList(CommandSourceStack s) {
        StringBuilder sb = new StringBuilder("Whitelisted players: ");
        for (String name : config.get().playerWhitelist) sb.append(name).append(", ");
        log.sendInfo(s, sb.toString());
        return 1;
    }

    private static int executeAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String name = player.getScoreboardName();
        if (config.get().playerWhitelist.contains(name)) {
            log.sendInfo(ctx.getSource(), "{} is already whitelisted.", name);
        } else {
            config.get().playerWhitelist.add(name);
            config.save();
            if (config.get().playerBlacklist.contains(name)) {
                config.get().playerBlacklist.remove(name);
                config.save();
            }
            ctx.getSource().getServer().getCommands().sendCommands(player);
            log.sendInfo(ctx.getSource(), "Added {} to whitelist.", name);
        }
        return 1;
    }

    private static int executeRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String name = player.getScoreboardName();
        if (!config.get().playerWhitelist.contains(name)) {
            log.sendInfo(ctx.getSource(), "{} was not whitelisted.", name);
        } else {
            config.get().playerWhitelist.remove(name);
            config.save();
            ctx.getSource().getServer().getCommands().sendCommands(player);
            log.sendInfo(ctx.getSource(), "Removed {} from whitelist.", name);
        }
        return 1;
    }
}
