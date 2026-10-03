package com.mochi.backup.commands.manage;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.Commands;

import com.mochi.backup.MochiLogger;
import com.mochi.backup.MochiClient;
import com.mochi.backup.core.RestoreableFile;
import com.mochi.backup.core.restore.RestoreHelper;

import java.util.*;

public class ListBackupsCommand {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);

    public static LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> register() {
        return Commands.literal("list")
                .executes(ctx -> {
                    var backups = RestoreHelper.getAvailableBackups(ctx.getSource().getServer());
                    if (backups.isEmpty()) {
                        log.sendInfo(ctx.getSource(), "No backups available for this world.");
                    } else if (backups.size() == 1) {
                        log.sendInfo(ctx.getSource(), "One backup: {}", backups.get(0).toString());
                    } else {
                        StringBuilder sb = new StringBuilder("Available backups:\n");
                        backups.sort(null);
                        for (RestoreableFile f : backups) sb.append(f.toString()).append("\n");
                        log.sendInfo(ctx.getSource(), sb.toString().trim());
                    }
                    return 1;
                });
    }
}
