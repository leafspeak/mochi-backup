package com.mochi.backup.commands.restore;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.Commands;

import com.mochi.backup.Globals;
import com.mochi.backup.MochiLogger;
import com.mochi.backup.MochiClient;
import com.mochi.backup.commands.CommandExceptions;
import com.mochi.backup.commands.FileSuggestionProvider;
import com.mochi.backup.core.RestoreableFile;
import com.mochi.backup.core.restore.RestoreContext;
import com.mochi.backup.core.restore.RestoreHelper;

import org.jetbrains.annotations.Nullable;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

public class RestoreBackupCommand {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);

    public static LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> register() {
        return Commands.literal("restore")
                .then(Commands.argument("file", StringArgumentType.word())
                        .suggests(FileSuggestionProvider.Instance())
                        .executes(ctx -> execute(StringArgumentType.getString(ctx, "file"), null, ctx.getSource())))
                .then(Commands.argument("file", StringArgumentType.word())
                        .suggests(FileSuggestionProvider.Instance())
                        .then(Commands.argument("comment", StringArgumentType.word())
                                .executes(ctx -> execute(
                                        StringArgumentType.getString(ctx, "file"),
                                        StringArgumentType.getString(ctx, "comment"),
                                        ctx.getSource()))))
                .executes(ctx -> {
                    net.minecraft.commands.CommandSourceStack s = ctx.getSource();
                    log.sendInfo(s, "Usage: /mochi restore <timestamp> [comment]");
                    log.sendInfo(s, "Format: YYYY-MM-DD_HH-mm-ss (e.g. 2024-01-15_14-30-00)");
                    log.sendInfo(s, "Or type /mochi restore latest for the most recent backup");
                    return 1;
                });
    }

    private static int execute(String file, @Nullable String comment, net.minecraft.commands.CommandSourceStack source) throws CommandSyntaxException {
        if (Globals.INSTANCE.getAwaitThread().filter(Thread::isAlive).isPresent()) {
            log.sendInfo(source, "A restore is already in progress. Use /mochi killR to cancel.");
            return -1;
        }

        LocalDateTime dateTime;
        Optional<RestoreableFile> backupFile;

        if (Objects.equals(file, "latest")) {
            backupFile = RestoreHelper.getLatestAndLockIfPresent(source.getServer());
            dateTime = backupFile.map(RestoreableFile::getCreationTime).orElse(LocalDateTime.now());
        } else {
            try { dateTime = LocalDateTime.from(Globals.defaultDateTimeFormatter.parse(file)); }
            catch (DateTimeParseException e) { throw CommandExceptions.DATE_TIME_PARSE_ERROR.create(e); }
            backupFile = RestoreHelper.findFileAndLockIfPresent(dateTime, source.getServer());
        }

        if (backupFile.isEmpty()) {
            log.sendInfo(source, "No backup found for: {}", dateTime.format(Globals.defaultDateTimeFormatter));
            return -1;
        }

        log.info("Found backup to restore: {}", backupFile.get().getFile().getFileName());
        // Disable shutdown backup NOW — during the countdown the executor is about to be
        // killed by the restore, so any save+quit would fail silently.
        Globals.INSTANCE.globalShutdownBackupFlag.set(false);
        Globals.INSTANCE.resetShutdownBackupTriggered();
        Globals.INSTANCE.setAwaitThread(RestoreHelper.create(
                RestoreContext.Builder.newRestoreContextBuilder()
                        .setCommandSource(source)
                        .setFile(backupFile.get())
                        .setComment(comment)
                        .build()
        ));
        Globals.INSTANCE.getAwaitThread().get().start();
        return 1;
    }
}
