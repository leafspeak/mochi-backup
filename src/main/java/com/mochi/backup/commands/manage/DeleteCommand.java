package com.mochi.backup.commands.manage;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import com.mochi.backup.Globals;
import com.mochi.backup.MochiConfigHelper;
import com.mochi.backup.MochiLogger;
import com.mochi.backup.MochiClient;
import com.mochi.backup.commands.CommandExceptions;
import com.mochi.backup.commands.FileSuggestionProvider;
import com.mochi.backup.core.RestoreableFile;
import com.mochi.backup.Utilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

public class DeleteCommand {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("delete")
                .then(Commands.argument("file", StringArgumentType.word())
                        .suggests(FileSuggestionProvider.Instance())
                        .executes(ctx -> execute(ctx.getSource(), StringArgumentType.getString(ctx, "file"))));
    }

    private static int execute(CommandSourceStack source, String fileName) throws CommandSyntaxException {
        LocalDateTime dateTime;
        try { dateTime = LocalDateTime.from(Globals.defaultDateTimeFormatter.parse(fileName)); }
        catch (DateTimeParseException e) { throw CommandExceptions.DATE_TIME_PARSE_ERROR.create(e); }

        Path root = Utilities.getBackupRootPath(MochiConfigHelper.INSTANCE.get(), Utilities.getLevelName(source.getServer()));

        Path foundFile = RestoreableFile.applyOnFiles(root, (Path) null,
                e -> log.sendError(source, "Error deleting file", e),
                stream -> stream.filter(f -> f.getCreationTime().equals(dateTime))
                        .map(RestoreableFile::getFile).findFirst().orElse(null));

        if (foundFile != null) {
            if (Globals.INSTANCE.getLockedFile().filter(p -> p == foundFile).isEmpty()) {
                try {
                    Files.delete(foundFile);
                    log.sendInfo(source, "Deleted: {}", foundFile.getFileName());
                } catch (IOException e) {
                    log.sendError(source, "Failed to delete file!");
                }
            } else {
                log.sendError(source, "Cannot delete file - being restored. Use /mochi killR to cancel.");
            }
        } else {
            log.sendInfo(source, "File not found. Try /mochi list");
        }
        return 0;
    }
}