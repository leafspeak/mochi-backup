package com.mochi.backup.commands;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;

import com.mochi.backup.Globals;
import com.mochi.backup.core.RestoreableFile;
import com.mochi.backup.Utilities;
import com.mochi.backup.core.restore.RestoreHelper;

import java.util.concurrent.CompletableFuture;

public final class FileSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    private static final FileSuggestionProvider INSTANCE = new FileSuggestionProvider();

    public static FileSuggestionProvider Instance() { return INSTANCE; }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        var files = RestoreHelper.getAvailableBackups(ctx.getSource().getServer());

        for (RestoreableFile file : files) {
            String formatted = file.getCreationTime().format(Globals.defaultDateTimeFormatter);
            if (formatted.startsWith(remaining)) {
                if (Utilities.wasSentByPlayer(ctx.getSource())) {
                    file.getComment().ifPresent(c -> builder.suggest(formatted, new LiteralMessage("Comment: " + c)));
                    if (!file.getComment().isPresent()) builder.suggest(formatted);
                } else {
                    file.getComment().ifPresent(c -> builder.suggest(formatted + "#" + c));
                    if (!file.getComment().isPresent()) builder.suggest(formatted);
                }
            }
        }

        if ("latest".startsWith(remaining) && !files.isEmpty()) {
            var latest = files.getLast();
            builder.suggest("latest", new LiteralMessage(
                    latest.getCreationTime().format(Globals.defaultDateTimeFormatter) +
                            latest.getComment().map(c -> "#" + c).orElse("")));
        }
        return builder.buildFuture();
    }
}
