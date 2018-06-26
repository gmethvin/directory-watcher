package io.methvin.watchservice;

import com.typesafe.config.ConfigFactory;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import io.methvin.watcher.DirectoryWatcher;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

public class DirectoryWatcherOnDiskTest {

  private Path tmpDir;
  private EventRecorder recorder;
  private DirectoryWatcher watcher;

  @Before
  public void setUp() throws IOException {
    this.tmpDir = Files.createTempDirectory(null);
    this.recorder = new EventRecorder();
    this.watcher = DirectoryWatcher.create(this.tmpDir, this.recorder);
    System.setProperty("io.methvin.prevent-file-hashing", "true");
    ConfigFactory.invalidateCaches();
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

  @Test
  public void emitCreateEventWhenFileLocked() throws IOException, ExecutionException, InterruptedException {
    final CompletableFuture future = this.watcher.watchAsync();
    final Path child = Files.createTempFile(tmpDir, "child-", ".dat");
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

      assertTrue(
          "Create event for the child file was notified",
          this.recorder.events.stream().anyMatch(
              e -> e.eventType() == DirectoryChangeEvent.EventType.CREATE && e.path().getFileName().equals(child.getFileName())));

    } finally {
      if(lock != null && channel != null && channel.isOpen()){
        lock.release();
        channel.close();
      }
    }
  }

  @Test
  public void emitModifyEventWhenFileLocked() throws IOException, ExecutionException, InterruptedException {
    final CompletableFuture future = this.watcher.watchAsync();
    final Path child = Files.createTempFile(tmpDir, "child-", ".dat");
//    Files.createFile(child);
    FileChannel channel = null;
    FileLock lock = null;
    try {

      File file = child.toFile();
      channel = new RandomAccessFile(file, "rw").getChannel();
      lock = channel.lock();

      if(lock != null){
        ByteBuffer bytes = ByteBuffer.allocate(8);
        bytes.putLong(System.currentTimeMillis() + 10000).flip();
        channel.write(bytes);
        channel.force(false);
      } else {
        fail("No lock found");
      }

      try {
        future.get(5, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        // Expected exception.
      }

      assertTrue(
          "Create event for the child file was notified",
          this.recorder.events.stream().anyMatch(
              e -> e.eventType() == DirectoryChangeEvent.EventType.CREATE && e.path().getFileName().equals(child.getFileName())));
      assertTrue(
          "Modify event for the child file was notified",
          this.recorder.events.stream().anyMatch(
              e -> e.eventType() == DirectoryChangeEvent.EventType.MODIFY && e.path().getFileName().equals(child.getFileName())));
//      lock.release();
//      channel.close();
//      Files.delete(child);
    } finally {
      if(lock != null && channel != null && channel.isOpen()){
        lock.release();
        channel.close();
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
