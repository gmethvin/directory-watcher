package io.methvin.watchservice;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import io.methvin.watcher.DirectoryWatcher;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.awaitility.Awaitility.await;

public class DirectoryWatcherOnDiskTest {

  private Path tmpDir;
  private EventRecorder recorder;
  private DirectoryWatcher watcher;

  @Before
  public void setUp() throws IOException {
    this.tmpDir = Files.createTempDirectory(null);
    this.recorder = new EventRecorder();
  }

  @After
  public void tearDown() throws IOException {
    FileUtils.deleteDirectory(this.tmpDir.toFile());
  }

  @Test
  public void copySubDirectoryFromOutsideNoHashing()
      throws IOException, ExecutionException, InterruptedException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(false)
            .build();
    copySubDirectoryFromOutside();
    this.watcher.close();
  }

  @Test
  public void copySubDirectoryFromOutsideWithHashing()
      throws IOException, ExecutionException, InterruptedException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(true)
            .build();
    copySubDirectoryFromOutside();
    this.watcher.close();
  }

  @Test
  public void copySubDirectoryFromOutsideTwiceHashing() throws IOException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(true)
            .build();
    this.watcher.watchAsync();
    List<Path> structure = createFolderStructure();
    copyAndVerifyEvents(structure);

    await().atMost(5, TimeUnit.SECONDS).until(() -> tmpDir.toFile().listFiles().length == 0);
    // reset recorder
    this.recorder.events.clear();
    copyAndVerifyEvents(structure);
    this.watcher.close();
  }

  @Test
  public void copySubDirectoryFromOutsideTwiceNoHashing() throws IOException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(false)
            .build();
    this.watcher.watchAsync();
    List<Path> structure = createFolderStructure();
    copyAndVerifyEvents(structure);

    await().atMost(5, TimeUnit.SECONDS).until(() -> tmpDir.toFile().listFiles().length == 0);
    // reset recorder
    this.recorder.events.clear();
    copyAndVerifyEvents(structure);
    this.watcher.close();
  }

  private void copyAndVerifyEvents(List<Path> structure) throws IOException {
    try {
      FileUtils.copyDirectoryToDirectory(structure.get(0).toFile(), tmpDir.toFile());
      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  assertTrue(
                      "Create event for the parent directory was notified",
                      existsMatch(
                          e ->
                              e.eventType() == DirectoryChangeEvent.EventType.CREATE
                                  && e.path()
                                      .getFileName()
                                      .equals(structure.get(0).getFileName()))));

      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  assertTrue(
                      "Create event for the child file was notified",
                      existsMatch(
                          e ->
                              e.eventType() == DirectoryChangeEvent.EventType.CREATE
                                  && e.path()
                                      .getFileName()
                                      .equals(structure.get(1).getFileName()))));

      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  assertTrue(
                      "Create event for the child file was notified",
                      existsMatch(
                          e ->
                              e.eventType() == DirectoryChangeEvent.EventType.CREATE
                                  && e.path()
                                      .getFileName()
                                      .equals(structure.get(2).getFileName()))));
    } finally {
      // unfortunately this deletion does not simulate 'real' deletion by user, as it delete all the
      // files underneath
      // you can stop the execution of the test here and delete the folder by hand and then continue
      // with the test
      FileUtils.deleteDirectory(tmpDir.toFile().listFiles()[0]);
    }
  }

  private List<Path> createFolderStructure() throws IOException {
    final Path parent = Files.createTempDirectory("parent-");
    final Path child = Files.createTempFile(parent, "child-", ".dat");
    final Path childFolder = Files.createTempDirectory(parent, "child-");

    return Arrays.asList(parent, child, childFolder);
  }

  private void copySubDirectoryFromOutside()
      throws IOException, InterruptedException, ExecutionException {
    final CompletableFuture<Void> future = this.watcher.watchAsync();
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
          existsMatch(
              e ->
                  e.eventType() == DirectoryChangeEvent.EventType.CREATE
                      && e.path().getFileName().equals(parent.getFileName())));
      assertTrue(
          "Create event for the child file was notified",
          existsMatch(
              e ->
                  e.eventType() == DirectoryChangeEvent.EventType.CREATE
                      && e.path().getFileName().equals(child.getFileName())));

    } finally {
      if (Files.exists(parent)) {
        FileUtils.deleteDirectory(parent.toFile());
      }
    }
  }

  @Test
  public void moveSubDirectoryNoHashing()
      throws IOException, ExecutionException, InterruptedException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(false)
            .build();
    moveSubDirectory();
    this.watcher.close();
  }

  @Test
  public void moveSubDirectoryWithHashing()
      throws IOException, ExecutionException, InterruptedException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(true)
            .build();
    moveSubDirectory();
    this.watcher.close();
  }

  private void moveSubDirectory() throws IOException, InterruptedException, ExecutionException {
    final CompletableFuture<Void> future = this.watcher.watchAsync();
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
          existsMatch(
              e ->
                  e.eventType() == DirectoryChangeEvent.EventType.CREATE
                      && e.path()
                          .toString()
                          .endsWith(newParent.resolve(this.tmpDir.relativize(parent)).toString())));
      assertTrue(
          "Create event for the child file was notified",
          existsMatch(
              e ->
                  e.eventType() == DirectoryChangeEvent.EventType.CREATE
                      && e.path()
                          .toString()
                          .endsWith(newParent.resolve(this.tmpDir.relativize(child)).toString())));

    } finally {
      if (Files.exists(parent)) {
        FileUtils.deleteDirectory(parent.toFile());
      }
      if (Files.exists(newParent)) {
        FileUtils.deleteDirectory(newParent.toFile());
      }
    }
  }

  @Test
  public void emitCreateEventWhenFileLockedNoHashing()
      throws IOException, ExecutionException, InterruptedException {
    Assume.assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"));

    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(false)
            .build();
    final CompletableFuture<Void> future = this.watcher.watchAsync();
    Random random = new Random();
    int i = random.nextInt(100_000);
    final Path child = tmpDir.resolve("child-" + i + ".dat");
    FileChannel channel = null;
    FileLock lock = null;
    try {

      File file = child.toFile();
      channel = new RandomAccessFile(file, "rw").getChannel();
      lock = channel.lock();

      try {
        future.get(5, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        // Expected exception.
      }

      assertEquals("Create event for the child file was notified", 1, this.recorder.events.size());

      assertTrue(
          "Create event for the child file was notified",
          existsMatch(
              e ->
                  e.eventType() == DirectoryChangeEvent.EventType.CREATE
                      && e.path().getFileName().equals(child.getFileName())));

    } finally {
      if (lock != null && channel != null && channel.isOpen()) {
        lock.release();
        channel.close();
      }
      if (this.watcher != null) {
        this.watcher.close();
      }
    }
  }

  @Test
  public void doNotEmitCreateEventWhenFileLockedWithHashing()
      throws IOException, ExecutionException, InterruptedException {
    // This test confirms that on Windows we lose the event when the hashed file is locked.
    Assume.assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"));
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(true)
            .build();
    final CompletableFuture<Void> future = this.watcher.watchAsync();
    Random random = new Random();
    int i = random.nextInt(100_000);
    final Path child = tmpDir.resolve("child-" + i + ".dat");
    FileChannel channel = null;
    FileLock lock = null;
    try {

      File file = child.toFile();
      channel = new RandomAccessFile(file, "rw").getChannel();
      lock = channel.lock();

      try {
        future.get(5, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        // Expected exception.
      }

      assertEquals("No event for the file creation expected", 0, this.recorder.events.size());

    } finally {
      if (lock != null && channel != null && channel.isOpen()) {
        lock.release();
        channel.close();
      }
      if (this.watcher != null) {
        this.watcher.close();
      }
    }
  }

  private boolean existsMatch(Predicate<DirectoryChangeEvent> predicate) {
    return this.recorder.events.stream().anyMatch(predicate);
  }

  class EventRecorder implements DirectoryChangeListener {

    private List<DirectoryChangeEvent> events = new ArrayList<>();

    @Override
    public void onEvent(DirectoryChangeEvent event) {
      this.events.add(event);
    }
  }
}
