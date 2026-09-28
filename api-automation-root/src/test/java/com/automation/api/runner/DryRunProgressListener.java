package com.automation.api.runner;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestCaseFinished;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

// Registered as a Cucumber plugin (see ApiTestRunner's @CucumberOptions) purely to give the
// portal's dry-run progress bar an honest, LIVE signal. The MCP server used to estimate
// progress by tailing Maven's captured console log for "Scenario:" lines, but a forked
// Surefire JVM's stdout is buffered by Maven and often doesn't reach that log file until the
// whole run finishes -- the bar looked static, then jumped to 100%. TestCaseFinished is a
// real Cucumber event fired the instant each scenario completes, and this writes straight to
// a small file (bypassing Maven's stdout buffering entirely) that ApiAutomationTools polls.
public class DryRunProgressListener implements ConcurrentEventListener {
    private static final String PROGRESS_FILE = "dryrun-reports/progress.count";
    private final AtomicInteger completed = new AtomicInteger(0);

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseFinished.class, event -> {
            if (!event.getTestCase().getTags().contains("@dryrun")) return;
            int current = completed.incrementAndGet();
            try (FileWriter writer = new FileWriter(PROGRESS_FILE, false)) {
                writer.write(String.valueOf(current));
            } catch (IOException ignored) {
                // best-effort -- a missed tick just means one poll cycle under-reports;
                // the next TestCaseFinished event corrects it
            }
        });
    }
}
