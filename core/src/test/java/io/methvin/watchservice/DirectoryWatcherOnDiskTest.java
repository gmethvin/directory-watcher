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
  public void copySubDirectoryFromOutside() throws IOException, ExecutionException, InterruptedException {

    final CompletableFuture future = this.watcher.watchAsync();
    final Path parent = Files.createTempDirectory("parent-");
    final Path child = Files.createTempFile(parent, "child-", ".dat");
    try {

      FileUtils.moveToDirectory(parent.toFile(), this.tmpDir.toFile(), false);

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

    } finally {
      if (Files.exists(parent)) {
        FileUtils.deleteDirectory(parent.toFile());
      }
    }

  }

  @Test
  public void moveSubDirectory() throws IOException, ExecutionException, InterruptedException {

    final CompletableFuture future = this.watcher.watchAsync();
    final Path parent = Files.createTempDirectory(this.tmpDir, "parent-");
    final Path child = Files.createTempFile(parent, "child-", ".dat");
    final Path newParent = Files.createTempDirectory(this.tmpDir, "new-");
    try {

      FileUtils.moveToDirectory(parent.toFile(), newParent.toFile(), false);

      try {
        future.get(10, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        // Expected exception.
      }

      assertTrue(
          "Create event for the parent directory was notified",
          this.recorder.events.stream().anyMatch(e ->
              e.eventType() == DirectoryChangeEvent.EventType.CREATE
                  && e.path().toString().endsWith(newParent.resolve(this.tmpDir.relativize(parent)).toString())));
      assertTrue(
          "Create event for the child file was notified",
          this.recorder.events.stream().anyMatch(
              e -> e.eventType() == DirectoryChangeEvent.EventType.CREATE
                  && e.path().toString().endsWith(newParent.resolve(this.tmpDir.relativize(child)).toString())));

    } finally {
      if (Files.exists(parent)) {
        FileUtils.deleteDirectory(parent.toFile());
      }
      if (Files.exists(newParent)) {
        FileUtils.deleteDirectory(newParent.toFile());
      }
    }

  }

  class EventRecorder implements DirectoryChangeListener {

    private List<DirectoryChangeEvent> events = new ArrayList<>();

    @Override
    public void onEvent(DirectoryChangeEvent event) {
      this.events.add(event);
    }

  }

}
