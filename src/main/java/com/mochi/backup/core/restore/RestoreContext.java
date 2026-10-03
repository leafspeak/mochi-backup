package com.mochi.backup.core.restore;

import com.mochi.backup.*;
import com.mochi.backup.core.RestoreableFile;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;

public record RestoreContext(
        RestoreableFile restoreableFile,
        MinecraftServer server,
        @Nullable String comment,
        ActionInitiator initiator,
        CommandSourceStack commandSource
) {
    public static class Builder {
        private RestoreableFile file;
        private MinecraftServer server;
        private String comment;
        private CommandSourceStack commandSource;

        private Builder() {}
        public static Builder newRestoreContextBuilder() { return new Builder(); }
        public Builder setFile(RestoreableFile f) { file = f; return this; }
        public Builder setServer(MinecraftServer s) { server = s; return this; }
        public Builder setComment(@Nullable String c) { comment = c; return this; }
        public Builder setCommandSource(CommandSourceStack s) { commandSource = s; return this; }

        public RestoreContext build() {
            if (server == null) server = commandSource.getServer();
            ActionInitiator init = commandSource.getEntity() instanceof ServerPlayer
                    ? ActionInitiator.Player : ActionInitiator.ServerConsole;
            return new RestoreContext(file, server, comment, init, commandSource);
        }
    }
}
