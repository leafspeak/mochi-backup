package com.mochi.backup.commands;

import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.format.DateTimeParseException;

public class CommandExceptions {
    public static final DynamicCommandExceptionType DATE_TIME_PARSE_ERROR = new DynamicCommandExceptionType(o -> {
        DateTimeParseException e = (DateTimeParseException) o;
        MutableComponent msg = Component.literal("Invalid date format:\n")
                .append(Component.literal(e.getParsedString()))
                .append(Component.literal("\n"));
        for (int i = 0; i < e.getErrorIndex(); i++) msg.append(Component.literal(" "));
        return msg.append(Component.literal("^"));
    });
}
