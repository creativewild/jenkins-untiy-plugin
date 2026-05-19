package io.jenkins.plugins.unity;

import hudson.console.LineTransformationOutputStream;
import io.jenkins.plugins.unity.core.LineStatus;
import io.jenkins.plugins.unity.core.LogLine;
import io.jenkins.plugins.unity.core.UnityLogProcessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

class UnityConsoleLogOutputStream extends LineTransformationOutputStream {
    private static final Logger LOGGER = Logger.getLogger(UnityConsoleLogOutputStream.class.getName());
    private final java.io.PrintStream logger;
    private final UnityLogProcessor processor;
    private final boolean suppressErrors;
    private String currentBlock;

    UnityConsoleLogOutputStream(java.io.PrintStream logger, UnityLogProcessor processor, boolean suppressErrors) {
        this.logger = logger;
        this.processor = processor;
        this.suppressErrors = suppressErrors;
    }

    @Override
    protected void eol(byte[] bytes, int length) throws IOException {
        String line = new String(bytes, 0, length, StandardCharsets.UTF_8).stripTrailing();
        logLine(line);
    }

    void logLine(String line) {
        if (line.isEmpty()) return;
        LogLine processed = processor.process(line);
        if (processed.getBlock() != null && !processed.getBlock().equals(currentBlock)) {
            currentBlock = processed.getBlock();
            logger.println("[Unity] " + currentBlock);
        }
        if (processed.getStatus() == LineStatus.ERROR && !suppressErrors) {
            logger.println("[Unity][ERROR] " + processed.getText());
        } else if (processed.getStatus() == LineStatus.WARNING || processed.getStatus() == LineStatus.ERROR) {
            logger.println("[Unity][WARNING] " + processed.getText());
        } else {
            logger.println(processed.getText());
        }
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to close Unity log stream", e);
            throw e;
        }
    }
}
