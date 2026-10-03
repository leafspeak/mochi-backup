package com.mochi.backup;

import net.minecraft.commands.CommandSourceStack;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.apache.logging.log4j.util.StackLocatorUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class MochiLogger {
    private final MessageFactory messageFactory;
    private final Logger logger;
    private final String prefix;

    public MochiLogger(String prefix) {
        this.messageFactory = ParameterizedMessageFactory.INSTANCE;
        this.logger = LogManager.getLogger(StackLocatorUtil.getCallerClass(2), messageFactory);
        this.prefix = "[" + prefix + "] ";
    }

    public void log(Level level, String msg, Object... data) {
        logger.log(level, prefix + msg, data);
    }

    public void trace(String msg, Object... data) { log(Level.TRACE, msg, data); }
    public void debug(String msg, Object... data) { log(Level.DEBUG, msg, data); }
    public void info(String msg, Object... data) { log(Level.INFO, msg, data); }
    public void warn(String msg, Object... data) { log(Level.WARN, msg, data); }
    public void error(String msg, Object... data) { log(Level.ERROR, msg, data); }

    void error(String message, Throwable throwable) {
        logger.error(prefix + message, throwable);
    }

    public void sendInfo(CommandSourceStack source, String msg, Object... args) {
        if (source != null && source.isPlayer()) {
            MutableComponent text = Component.literal(
                    messageFactory.newMessage(msg, args).getFormattedMessage());
            source.sendSuccess(() -> text, false);
        } else {
            log(Level.INFO, msg, args);
        }
    }

    public void sendError(CommandSourceStack source, String msg, Object... args) {
        if (source != null && source.isPlayer()) {
            MutableComponent text = Component.literal(
                    messageFactory.newMessage(msg, args).getFormattedMessage());
            source.sendSuccess(() -> text, false);
        } else {
            log(Level.ERROR, msg, args);
        }
    }

    public void sendHint(CommandSourceStack source, String msg, Object... args) {
        if (source != null && source.isPlayer()) {
            MutableComponent text = Component.literal(
                    messageFactory.newMessage(msg, args).getFormattedMessage());
            source.sendSuccess(() -> text, false);
        } else {
            log(Level.TRACE, msg, args);
        }
    }
}