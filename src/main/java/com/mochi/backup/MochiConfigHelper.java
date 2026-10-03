package com.mochi.backup;

import me.shedaniel.autoconfig.ConfigHolder;

public class MochiConfigHelper {
    public static final MochiConfigHelper INSTANCE = new MochiConfigHelper();
    private ConfigHolder<MochiConfig> configHolder;

    public static void updateInstance(ConfigHolder<MochiConfig> ch) { INSTANCE.configHolder = ch; }

    public MochiConfig get() { return configHolder.get(); }
    public void save() { configHolder.save(); }
}
