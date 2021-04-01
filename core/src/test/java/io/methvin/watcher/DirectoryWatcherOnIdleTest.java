package io.methvin.watcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.google.common.util.concurrent.UncheckedExecutionException;
import io.methvin.watcher.changeset.ChangeSet;
import io.methvin.watcher.changeset.ChangeSetListener;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectoryWatcherOnIdleTest {

  private Path tmpDir;
  private DirectoryWatcher watcher;

  @Before
  public void setUp() throws IOException {
    this.tmpDir = Files.createTempDirectory(null);
  }

  @After
  public void tearDown() {
    try {
      FileUtils.deleteDirectory(this.tmpDir.toFile());
    } catch (Exception e) {
    }
  }

  @Test
  public void testOnIdle() throws IOException {
    Path d1 = this.tmpDir.resolve("idle1");
    Files.createDirectory(d1);

    AtomicLong started = new AtomicLong();
    AtomicLong updated = new AtomicLong();
    AtomicInteger counter = new AtomicInteger(-1);

    Consumer<Integer> consumer =
        value -> {
          updated.set(System.currentTimeMillis() - started.get());
          counter.set(value);

          try {
            watcher.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        };

    watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(d1))
            .listener(new Composite(consumer))
            .fileHashing(true)
            .onIdleTimeout(100)
            .build();

    started.set(System.currentTimeMillis());
    watcher.watchAsync();

    doDelay(400);
    assertTrue(updated.get() > 0 && updated.get() < 200);
    assertEquals(0, counter.get());
  }

  private void doDelay(long timeout) {
    try {
      Thread.sleep(timeout);
    } catch (InterruptedException e) {
      throw new UncheckedExecutionException(e);
    }
  }

  @Test
  public void testOnIdleFilesCreationAdded() throws IOException {
    Path d1 = this.tmpDir.resolve("idle2");
    Files.createDirectory(d1);

    AtomicLong started = new AtomicLong();
    AtomicLong updated = new AtomicLong();
    AtomicInteger counter = new AtomicInteger(-1);
    AtomicInteger onIdleCalled = new AtomicInteger(0);

    Consumer<Integer> consumer =
        value -> {
          updated.set(System.currentTimeMillis() - started.get());
          counter.set(value);
          onIdleCalled.incrementAndGet();
        };

    watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(d1))
            .listener(new Composite(consumer))
            .fileHashing(true)
            .onIdleTimeout(1000)
            .build();

    started.set(System.currentTimeMillis());
    watcher.watchAsync();

    Files.createTempFile(d1, "parent1-", ".dat");
    doDelay(800);
    Files.createTempFile(d1, "parent2-", ".dat");
    Files.createTempFile(d1, "parent3-", ".dat");
    doDelay(1200);
    Files.createTempFile(d1, "parent4-", ".dat");
    doDelay(100);
    Files.createTempFile(d1, "parent5-", ".dat");
    doDelay(2000);
    Files.createTempFile(d1, "parent6-", ".dat");
    doDelay(800);
    Files.createTempFile(d1, "parent7-", ".dat");
    doDelay(1100);

    assertEquals(3, onIdleCalled.get());
    assertEquals(isMac() ? 15 : 7, counter.get());
    watcher.close();
  }

  private boolean isMac() {
    return System.getProperty("os.name").toLowerCase().contains("mac");
  }

  @Test
  public void testOnIdleChangeSet() throws IOException {
    Path d1 = this.tmpDir.resolve("idle3");
    Files.createDirectory(d1);

    ChangeSetListener changeSetListener = new ChangeSetListener();

    AtomicLong started = new AtomicLong();
    AtomicLong updated = new AtomicLong();
    AtomicInteger counter = new AtomicInteger(-1);
    AtomicInteger onIdleCalled = new AtomicInteger(0);

    Composite composite =
        new Composite(
            value -> {
              updated.set(System.currentTimeMillis() - started.get());
              counter.set(value);
              onIdleCalled.incrementAndGet();
              try {
                watcher.close();
              } catch (IOException e) {
                e.printStackTrace();
              }
            },
            changeSetListener);

    watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(d1))
            .listener(composite)
            .fileHashing(true)
            .onIdleTimeout(100)
            .build();

    started.set(System.currentTimeMillis());
    watcher.watchAsync();

    Files.createTempFile(d1, "parent1-", ".dat");
    Files.createTempFile(d1, "parent2-", ".dat");
    Files.createTempFile(d1, "parent3-", ".dat");
    Files.createTempFile(d1, "parent4-", ".dat");
    Files.createTempFile(d1, "parent5-", ".dat");
    Files.createTempFile(d1, "parent6-", ".dat");
    Files.createTempFile(d1, "parent7-", ".dat");
    doDelay(200);

    assertEquals(1, onIdleCalled.get());

    Map<Path, ChangeSet> changeSet = changeSetListener.getChangeSet();
    assertTrue(!changeSet.isEmpty());
    assertEquals(7, changeSet.get(d1).created().size());
  }

  public static class Composite implements DirectoryChangeListener {

    public ChangeSetListener changeSetListener;

    private Consumer<Integer> counter;

    public Composite(Consumer<Integer> counter) {
      this(counter, null);
    }

    public Composite(Consumer<Integer> counter, ChangeSetListener changeSetListener) {
      this.counter = counter;
      this.changeSetListener = changeSetListener;
    }

    @Override
    public void onEvent(DirectoryChangeEvent event) {
      if (changeSetListener != null) {
        changeSetListener.onEvent(event);
      }
    }

    @Override
    public void onIdle(int count) {
      counter.accept(count);
    }
  }
}
