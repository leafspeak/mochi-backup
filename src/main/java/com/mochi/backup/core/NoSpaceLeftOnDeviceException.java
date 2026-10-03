package com.mochi.backup.core;

import java.io.IOException;

public class NoSpaceLeftOnDeviceException extends IOException {
    public NoSpaceLeftOnDeviceException(Throwable cause) {
        super("The underlying filesystem has run out of available space.", cause);
    }
}
