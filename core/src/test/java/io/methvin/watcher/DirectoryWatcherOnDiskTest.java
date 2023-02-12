package io.methvin.watcher;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;

import com.google.common.util.concurrent.UncheckedExecutionException;
import io.methvin.watcher.hashing.FileHash;
import io.methvin.watcher.hashing.FileHasher;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class DirectoryWatcherOnDiskTest {

  private Path tmpDir;
  private EventRecorder recorder;
  private DirectoryWatcher watcher;

  private boolean isWin() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  private boolean isMac() {
    return System.getProperty("os.name").toLowerCase().contains("mac");
  }

  @Before
  public void setUp() throws IOException {
    this.tmpDir = Files.createTempDirectory(null);
    this.recorder = new EventRecorder();
  }

  @After
  public void tearDown() throws IOException {
    try {
      FileUtils.deleteDirectory(this.tmpDir.toFile());
    } catch (Exception e) {
    }
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
  public void copySubDirectoryFromOutsideTwiceHashing() throws IOException, InterruptedException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(true)
            .build();
    this.watcher.watchAsync();
    List<Path> structure = createFolderStructure();
    copyAndVerifyEvents(structure);

    int atMost = 5;
    int length = 0;
    waitFileSize(atMost, length);

    // reset recorder
    ensureStill();

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

    ensureStill();

    List<Path> structure = createFolderStructure();
    copyAndVerifyEvents(structure);

    waitFileSize(5, 0);

    // reset recorder
    ensureStill();

    copyAndVerifyEvents(structure);
    this.watcher.close();
  }

  @Test
  public void deleteCreatedFilesWithHashing() throws IOException, InterruptedException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(true)
            .build();
    ensureStill();
    this.watcher.watchAsync();
    List<Path> structure = createStructure2(tmpDir);
    ensureStill();
    deleteAndVerifyEvents(structure);
    this.watcher.close();
  }

  @Test
  public void deleteCreatedFilesNoHashing() throws IOException, InterruptedException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(false)
            .build();
    this.watcher.watchAsync();
    List<Path> structure = createStructure2(tmpDir);
    ensureStill();
    deleteAndVerifyEvents(structure);
    this.watcher.close();
  }

  @Test
  public void deleteAlreadyExistingFilesWithHashing() throws IOException, InterruptedException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(true)
            .build();
    ensureStill();
    List<Path> structure = createStructure2(tmpDir);
    ensureStill();
    this.watcher.watchAsync();
    deleteAndVerifyEvents(structure);
    this.watcher.close();
  }

  @Test
  public void deleteAlreadyExistingFilesNoHashing() throws IOException, InterruptedException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(false)
            .build();
    ensureStill();
    List<Path> structure = createStructure2(tmpDir);
    this.watcher.watchAsync();
    deleteAndVerifyEvents(structure);
    this.watcher.close();
  }

  private void deleteAndVerifyEvents(List<Path> structure) throws IOException {
    // ignore initial root directory
    List<Path> files = structure.subList(1, structure.size());
    // reverse structure to delete children before parents
    Collections.reverse(files);
    for (Path file : files) {
      Files.deleteIfExists(file);
    }
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              for (Path file : files) {
                assertTrue(
                    "Delete event for the file was notified",
                    existsMatch(
                        e ->
                            e.eventType() == DirectoryChangeEvent.EventType.DELETE
                                && e.path().getFileName().equals(file.getFileName())));
              }
            });
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
      /*
       * unfortunately this deletion does not simulate 'real' deletion by the user, as
       * it deletes all the files underneath. You can stop the execution of the test
       * here and delete the folder by hand and then continue with the test.
       */
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

    ensureStill();

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

    ensureStill();

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
  public void throwWhenWatchCalledOnClosedWatcher()
      throws IOException, ExecutionException, InterruptedException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(false)
            .build();
    this.watcher.close();
    try {
      this.watcher.watch(); // should throw IllegalStateException
      fail("watcher did not throw");
    } catch (IllegalStateException e) {
      // expected
    }
  }

  @Test
  public void exitNormallyWhenWatcherClosed()
      throws IOException, ExecutionException, InterruptedException {
    this.watcher =
        DirectoryWatcher.builder()
            .path(this.tmpDir)
            .listener(this.recorder)
            .fileHashing(false)
            .build();
    final CompletableFuture<Void> future = this.watcher.watchAsync();
    Thread.sleep(100);
    this.watcher.close();
    try {
      future.get(1, TimeUnit.SECONDS); // should return normally
    } catch (TimeoutException e) {
      throw new IllegalStateException("watcher not closed", e);
    }
  }

  @Test
  public void emitCreateEventWhenFileLockedNoHashing()
      throws IOException, ExecutionException, InterruptedException {
    Assume.assumeTrue(isWin());

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
  public void emitCreateEventWhenFileLockedWithHashing()
      throws IOException, ExecutionException, InterruptedException {
    // This test confirms that on Windows we don't lose the event when the hashed
    // file is locked.
    Assume.assumeTrue(isWin());
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
  public void pathsWithContexts() throws IOException, InterruptedException {
    Path p1 = this.tmpDir.resolve("parent1");
    Path p2 = this.tmpDir.resolve("parent2");
    Path p3 = this.tmpDir.resolve("parent3");

    Files.createDirectory(p1);
    Files.createDirectory(p2);
    Files.createDirectory(p3);

    this.watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(new Path[] {p1, p2, p3}))
            .listener(this.recorder)
            .fileHashing(true)
            .build();
    this.watcher.watchAsync();
    assertFalse(this.watcher.isClosed());

    ensureStill();

    List<Path> paths1 = createStructure2(p1);
    List<Path> paths2 = createStructure2(p2);
    List<Path> paths3 = createStructure2(p3);

    waitRecorderSizeAtLeast(3, 15);

    checkEventsMatchContext(p1, p2, p3);
    this.recorder.events.clear();

    updatePaths(paths1);
    waitRecorderSize(3, 3);
    checkEventsMatchContext(p1, p2, p3);
    this.recorder.events.clear();

    updatePaths(paths2, paths3);
    waitRecorderSize(3, 6);
    checkEventsMatchContext(p1, p2, p3);
    this.recorder.events.clear();

    FileUtils.deleteDirectory(paths1.get(2).toFile());
    waitRecorderSize(3, 4);
    checkEventsMatchContext(p1, p2, p3);
    this.recorder.events.clear();

    FileUtils.deleteDirectory(paths2.get(2).toFile());
    waitRecorderSize(3, 4);
    checkEventsMatchContext(p1, p2, p3);
    this.recorder.events.clear();

    FileUtils.deleteDirectory(paths3.get(2).toFile());
    waitRecorderSize(3, 4);
    checkEventsMatchContext(p1, p2, p3);
    this.recorder.events.clear();

    if (isMac()) {
      // macOS watcher sends delete events for the parent
      FileUtils.deleteDirectory(paths1.get(0).toFile()); // deletes the p1 root
      waitRecorderSize(3, 2);
      checkEventsMatchContext(p1, p2, p3);
      this.recorder.events.clear();

      FileUtils.deleteDirectory(paths2.get(0).toFile()); // deletes the p2 root
      waitRecorderSize(3, 2);
      checkEventsMatchContext(p1, p2, p3);
      this.recorder.events.clear();

      assertFalse(this.watcher.isClosed());
      FileUtils.deleteDirectory(paths3.get(0).toFile()); // deletes the p3 root
      waitRecorderSize(3, 2);
      assertTrue(this.watcher.isClosed());
    }
  }

  @Test
  public void observeIsDirectory() throws IOException, InterruptedException {
    Path d1 = this.tmpDir.resolve("d1");
    Files.createDirectory(d1);

    this.watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(new Path[] {d1}))
            .listener(this.recorder)
            .fileHashing(true)
            .build();
    this.watcher.watchAsync();

    ensureStill();

    final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
    Files.write(f1, new byte[] {counter++});

    waitRecorderSizeAtLeast(3, 1);
    assertEquals(DirectoryChangeEvent.EventType.CREATE, this.recorder.events.get(0).eventType());
    assertFalse(this.recorder.events.get(0).isDirectory());

    this.recorder.events.clear();
    Files.write(f1, new byte[] {counter++});

    waitRecorderSizeAtLeast(3, 1);
    assertEquals(DirectoryChangeEvent.EventType.MODIFY, this.recorder.events.get(0).eventType());
    assertFalse(this.recorder.events.get(0).isDirectory());

    this.recorder.events.clear();
    Path d2 = d1.resolve("d2");
    Files.createDirectory(d2);

    waitRecorderSizeAtLeast(3, 1);
    assertEquals(DirectoryChangeEvent.EventType.CREATE, this.recorder.events.get(0).eventType());
    assertTrue(this.recorder.events.get(0).isDirectory());

    this.recorder.events.clear();
    Files.deleteIfExists(f1);
    waitRecorderSizeAtLeast(3, 1);
    Files.deleteIfExists(d2);

    waitRecorderSizeAtLeast(3, 2);

    assertEquals(DirectoryChangeEvent.EventType.DELETE, this.recorder.events.get(0).eventType());
    assertFalse(this.recorder.events.get(0).isDirectory());

    assertEquals(DirectoryChangeEvent.EventType.DELETE, this.recorder.events.get(1).eventType());
    assertTrue(this.recorder.events.get(1).isDirectory());

    this.watcher.close();
  }

  @Test
  public void observePreHashes() throws IOException, InterruptedException {
    Path d1 = this.tmpDir.resolve("d1");
    Files.createDirectory(d1);

    final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
    Files.write(f1, new byte[] {counter++});

    this.watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(new Path[] {d1}))
            .listener(this.recorder)
            .fileHashing(true)
            .build();

    this.watcher.watchAsync();

    Map<Path, FileHash> map = this.watcher.pathHashes();
    assertTrue(FileHash.DIRECTORY == map.get(d1));

    FileHash hashCode1 = FileHasher.DEFAULT_FILE_HASHER.hash(f1);
    assertEquals(hashCode1, map.get(f1));

    try {
      final Path f2 = Files.createTempFile(d1, "f2-", ".dat");
      map.put(f2, null);
      fail("Exception should be thrown");
    } catch (UnsupportedOperationException e) {
    }

    this.watcher.close();
  }

  @Test
  public void observeHashes() throws IOException, InterruptedException {
    Path d1 = this.tmpDir.resolve("d1");
    Files.createDirectory(d1);

    this.watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(new Path[] {d1}))
            .listener(this.recorder)
            .fileHashing(true)
            .build();

    this.watcher.watchAsync();

    ensureStill();

    final Path f1 = Files.createTempFile(d1, "f1-", ".dat");
    Files.write(f1, new byte[] {counter++});
    waitRecorderSizeAtLeast(3, 1);

    List<DirectoryChangeEvent> events = this.recorder.events;

    FileHash hashCode1 = FileHasher.DEFAULT_FILE_HASHER.hash(f1);
    FileHash recordedHash1 = events.get(events.size() - 1).hash();
    assertNotSame(hashCode1, recordedHash1);
    assertEquals(hashCode1, recordedHash1);

    this.recorder.events.clear();
    Files.write(f1, new byte[] {counter++});
    waitRecorderSizeAtLeast(3, 1);

    FileHash hashCode2 = FileHasher.DEFAULT_FILE_HASHER.hash(f1);
    FileHash recordedHash2 = events.get(events.size() - 1).hash();
    assertNotEquals(hashCode2, hashCode1);
    assertNotSame(hashCode2, recordedHash2);
    assertEquals(hashCode2, recordedHash2);

    this.watcher.close();
  }

  private void waitRecorderSizeAtLeast(int atMost, int untilSize) {
    await()
        .atMost(atMost, TimeUnit.SECONDS)
        .pollDelay(100, TimeUnit.MILLISECONDS)
        .until(() -> this.recorder.events.size() >= untilSize);
  }

  private void waitRecorderSize(int atMost, int untilSize) {
    await()
        .atMost(atMost, TimeUnit.SECONDS)
        .pollDelay(100, TimeUnit.MILLISECONDS)
        .until(() -> this.recorder.events.size() == untilSize);
  }

  private void waitFileSize(int atMost, int length) {
    await()
        .atMost(atMost, TimeUnit.SECONDS)
        .until(() -> tmpDir.toFile().listFiles().length == length);
  }

  private void ensureStill() {
    // I found some tests unstable with regards to counts, unless this was here
    try {
      Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      while (recorder.events.size() != 0) {
        recorder.events.clear();
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      }
    } catch (InterruptedException e) {
      throw new UncheckedExecutionException(e);
    }
  }

  private byte counter = 1;

  private void updatePaths(List<Path>... paths) throws IOException {
    Arrays.stream(paths)
        .forEach(
            path -> {
              try {
                Files.write(path.get(1), new byte[] {counter++});
                Files.write(path.get(3), new byte[] {counter++});
                Files.write(path.get(5), new byte[] {counter++});
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
  }

  private void checkEventsMatchContext(Path p1, Path p2, Path p3) {
    this.recorder.events.stream()
        .forEach(
            e -> {
              if (e.path().startsWith(p1)) {
                assertEquals(p1, e.rootPath());
              } else if (e.path().startsWith(p2)) {
                assertEquals(p2, e.rootPath());
              } else if (e.path().startsWith(p3)) {
                assertEquals(p3, e.rootPath());
              } else {
                Assert.fail("Path must match one of the p1, p2 or p3 Path subsets");
              }
            });
  }

  private List<Path> createStructure2(Path parent) throws IOException {
    final Path parentFile1 = Files.createTempFile(parent, "parent1-", ".dat");
    final Path childFolder1 = Files.createTempDirectory(parent, "child1-");
    final Path childFile1 = Files.createTempFile(childFolder1, "child1-", ".dat");
    final Path childFolder2 = Files.createTempDirectory(childFolder1, "child2-");
    final Path childFile2 = Files.createTempFile(childFolder2, "child2-", ".dat");

    return Arrays.asList(parent, parentFile1, childFolder1, childFile1, childFolder2, childFile2);
  }

  @Test
  public void testCrash() throws IOException {
    DirectoryWatcher directoryWatcher1 = DirectoryWatcher.builder().path(this.tmpDir).build();
    directoryWatcher1.watchAsync();
    directoryWatcher1.close();

    DirectoryWatcher directoryWatcher2 = DirectoryWatcher.builder().path(this.tmpDir).build();
    directoryWatcher2.watchAsync();
    directoryWatcher2.close();

    DirectoryWatcher directoryWatcher3 = DirectoryWatcher.builder().path(this.tmpDir).build();
    directoryWatcher3.watchAsync();
    directoryWatcher3.close();
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
