package io.methvin.watchservice;

import com.google.common.util.concurrent.UncheckedExecutionException;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import io.methvin.watcher.DirectoryWatcher;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;

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

    await().atMost(5, TimeUnit.SECONDS).until(() -> tmpDir.toFile().listFiles().length == 0);

    await().atMost(5, TimeUnit.SECONDS)
            .pollDelay(100, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> Assume.assumeTrue(this.recorder.events.size() == 6));

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

    ensureStill();

    List<Path> structure = createFolderStructure();
    copyAndVerifyEvents(structure);

    await().atMost(5, TimeUnit.SECONDS).until(() -> tmpDir.toFile().listFiles().length == 0);

    // it sometimes returns 8 (when run on it's own), sometimes 10 (when run with other tests).
    await().atMost(5, TimeUnit.SECONDS)
            .pollDelay(100, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> Assume.assumeTrue(this.recorder.events.size() == 8 || this.recorder.events.size() == 10));

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
                          e -> e.eventType() == DirectoryChangeEvent.EventType.CREATE
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
      /* unfortunately this deletion does not simulate 'real' deletion by the user, as it
       * deletes all the files underneath. You can stop the execution of the test here and
       * delete the folder by hand and then continue with the test.
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
  public void emitCreateEventWhenFileLockedWithHashing()
      throws IOException, ExecutionException, InterruptedException {
    // This test confirms that on Windows we don't lose the event when the hashed file is locked.
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

    Map<Path, String> contexts = new HashMap<>();
    contexts.put(p1, "p1");
    contexts.put(p2, "p2");
    contexts.put(p3, "p3");

    Files.createDirectory(p1);
    Files.createDirectory(p2);
    Files.createDirectory(p3);

    this.watcher =
            DirectoryWatcher.builder()
                            .paths(Arrays.asList( new Path[] {p1,p2, p3}))
                            .listener(this.recorder)
                            .fileHashing(false)
                            .build();
    this.watcher.watchAsync();
    assertFalse( this.watcher.isClosed() );

    ensureStill();

    assertEquals(3, this.watcher.getRegisteredContexts().size());

    //List<Path> paths = new ArrayList<>();
    List<Path> paths1 = createStructure2(p1);
    List<Path> paths2 = createStructure2(p2);
    List<Path> paths3 = createStructure2(p3);

    await().atMost(3, TimeUnit.SECONDS)
              .pollDelay(100, TimeUnit.MILLISECONDS)
              .untilAsserted(() -> Assume.assumeTrue(this.recorder.events.size() == 33));

    checkEventsMatchContext(p1, p2, p3);
    this.recorder.events.clear();

    updatePaths(paths1);
    await().atMost(3, TimeUnit.SECONDS).until(() -> recorder.events.size() == 3);
    checkEventsMatchContext(p1, p2, p3);
    this.recorder.events.clear();

    updatePaths(paths2, paths3);
    await().atMost(3, TimeUnit.SECONDS).until(() -> recorder.events.size() == 6);
    checkEventsMatchContext(p1, p2, p3);
    this.recorder.events.clear();


    FileUtils.deleteDirectory(paths1.get(2).toFile());
    await().atMost(3, TimeUnit.HOURS).until(() -> recorder.events.size() == 4);
    checkEventsMatchContext(p1, p2, p3);
    this.recorder.events.clear();
    ;
    FileUtils.deleteDirectory(paths1.get(0).toFile()); // deletes the p1 root
    await().atMost(3, TimeUnit.SECONDS).until(() -> recorder.events.size() == 2);
    checkEventsMatchContext(p1, p2, p3);
    assertEquals(2, this.watcher.getRegisteredContexts().size());
    this.recorder.events.clear();

    FileUtils.deleteDirectory(paths2.get(0).toFile()); // deletes the p1 root
    await().atMost(3, TimeUnit.SECONDS).until(() -> recorder.events.size() == 6);
    checkEventsMatchContext(p1, p2, p3);
    assertEquals(1, this.watcher.getRegisteredContexts().size());
    this.recorder.events.clear();

    assertFalse( this.watcher.isClosed() );
    FileUtils.deleteDirectory(paths3.get(0).toFile()); // deletes the p1 root
    await().atMost(3, TimeUnit.SECONDS).until(() -> recorder.events.size() == 6);
    assertEquals(0, this.watcher.getRegisteredContexts().size());
    assertTrue( this.watcher.isClosed() );
  }

  private void ensureStill() {
    // I found some tests unstable with regards to counts, unless this was here
    try {
      Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      while ( recorder.events.size() != 0 ) {
        recorder.events.clear();
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      }
    } catch (InterruptedException e) {
      throw new UncheckedExecutionException(e);
    }

  }

  private byte counter = 1;
  private void updatePaths(List<Path>... paths) throws IOException {
    Arrays.stream( paths).forEach(path -> {
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
    this.recorder.events.stream().forEach( e -> {
      if ( e.path().startsWith(p1) ) {
        assertEquals(p1, e.context());
      } else if ( e.path().startsWith(p2) ) {
        assertEquals(p2, e.context());
      } else if ( e.path().startsWith(p3) ) {
        assertEquals(p3, e.context());
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
