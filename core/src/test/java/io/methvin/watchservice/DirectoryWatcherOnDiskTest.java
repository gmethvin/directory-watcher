package io.methvin.watchservice;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import io.methvin.watcher.DirectoryWatcher;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static junit.framework.TestCase.assertTrue;

public class DirectoryWatcherOnDiskTest {

  private Path tmpDir;
  private EventRecorder recorder;
  private DirectoryWatcher watcher;

  @Before
  public void setUp() throws IOException {
    this.tmpDir = Files.createTempDirectory(null);
    this.recorder = new EventRecorder();
    this.watcher = DirectoryWatcher.create(this.tmpDir, this.recorder);
  }

  @After
  public void tearDown() throws IOException {
    this.watcher.close();
    FileUtils.deleteDirectory(this.tmpDir.toFile());
  }

  @Test
  public void copySubDirectory() throws IOException, ExecutionException, InterruptedException {

    final CompletableFuture future = this.watcher.watchAsync();
    final Path parent = Files.createTempDirectory("parent-");
    final Path child = Files.createTempFile(parent, "child-", ".dat");
    FileUtils.moveDirectory(parent.toFile(), this.tmpDir.resolve(parent.getFileName()).toFile());

    try {
      future.get(5, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      // Expected exception.
    }

    assertTrue(
        "Create event for the parent directory was notified",
        this.recorder.events.stream().anyMatch(
            e -> e.eventType() == DirectoryChangeEvent.EventType.CREATE && e.path().getFileName().equals(parent.getFileName())));
    assertTrue(
        "Create event for the child file was notified",
        this.recorder.events.stream().anyMatch(
            e -> e.eventType() == DirectoryChangeEvent.EventType.CREATE && e.path().getFileName().equals(child.getFileName())));

  }

  class EventRecorder implements DirectoryChangeListener {

    private List<DirectoryChangeEvent> events = new ArrayList<>();

    @Override
    public void onEvent(DirectoryChangeEvent event) {
      this.events.add(event);
    }

  }

}
