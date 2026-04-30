package org.slf4j;

/**
 * Minimal SLF4J LoggerFactory stub - Returns NOP loggers.
 * Prevents HikariCP from printing SLF4J provider errors.
 */
public final class LoggerFactory {

    private static final NOPLogger NOP_LOGGER = new NOPLogger();

    private LoggerFactory() {
    }

    public static Logger getLogger(String name) {
        return NOP_LOGGER;
    }

    public static Logger getLogger(Class<?> clazz) {
        return NOP_LOGGER;
    }

    /**
     * No-Operation Logger implementation.
     * All methods are empty - no logging is performed.
     */
    private static final class NOPLogger implements Logger {
        @Override public String getName() { return "NOP"; }

        @Override public boolean isTraceEnabled() { return false; }
        @Override public void trace(String msg) {}
        @Override public void trace(String format, Object arg) {}
        @Override public void trace(String format, Object arg1, Object arg2) {}
        @Override public void trace(String format, Object... arguments) {}
        @Override public void trace(String msg, Throwable t) {}

        @Override public boolean isDebugEnabled() { return false; }
        @Override public void debug(String msg) {}
        @Override public void debug(String format, Object arg) {}
        @Override public void debug(String format, Object arg1, Object arg2) {}
        @Override public void debug(String format, Object... arguments) {}
        @Override public void debug(String msg, Throwable t) {}

        @Override public boolean isInfoEnabled() { return false; }
        @Override public void info(String msg) {}
        @Override public void info(String format, Object arg) {}
        @Override public void info(String format, Object arg1, Object arg2) {}
        @Override public void info(String format, Object... arguments) {}
        @Override public void info(String msg, Throwable t) {}

        @Override public boolean isWarnEnabled() { return false; }
        @Override public void warn(String msg) {}
        @Override public void warn(String format, Object arg) {}
        @Override public void warn(String format, Object arg1, Object arg2) {}
        @Override public void warn(String format, Object... arguments) {}
        @Override public void warn(String msg, Throwable t) {}

        @Override public boolean isErrorEnabled() { return false; }
        @Override public void error(String msg) {}
        @Override public void error(String format, Object arg) {}
        @Override public void error(String format, Object arg1, Object arg2) {}
        @Override public void error(String format, Object... arguments) {}
        @Override public void error(String msg, Throwable t) {}
    }
}
