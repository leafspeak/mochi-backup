package com.mochi.backup;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

public class WorldSavingState {
    private final Set<ResourceKey<Level>> data;

    private WorldSavingState(Set<ResourceKey<Level>> data) {
        this.data = data;
    }

    public static WorldSavingState disable(MinecraftServer server) {
        Set<ResourceKey<Level>> data = new HashSet<>();
        for (ServerLevel serverWorld : server.getAllLevels()) {
            if (serverWorld == null) continue;
            serverWorld.noSave = true;
            data.add(serverWorld.dimension());
        }
        return new WorldSavingState(data);
    }

    public void enable(MinecraftServer server) {
        for (ServerLevel serverWorld : server.getAllLevels()) {
            if (serverWorld == null) continue;
            try {
                ResourceKey<Level> dim = serverWorld.dimension();
                if (dim != null && data.contains(dim))
                    serverWorld.noSave = false;
            } catch (Throwable ignored) {}
        }
    }
}
