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
import org.junit.Assume;

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
    Assume.assumeTrue(!isMac());

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
            .listener(new CompositeListener(100, consumer))
            .fileHashing(true)
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
    Assume.assumeTrue(!isMac());

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
            .listener(new CompositeListener(1000, consumer))
            .fileHashing(true)
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
    Assume.assumeTrue(!isMac());

    Path d1 = this.tmpDir.resolve("idle3");
    Files.createDirectory(d1);

    AtomicLong started = new AtomicLong();
    AtomicLong updated = new AtomicLong();
    AtomicInteger counter = new AtomicInteger(-1);
    AtomicInteger onIdleCalled = new AtomicInteger(0);

    CompositeListener composite =
        new CompositeListener(
            100,
            value -> {
              updated.set(System.currentTimeMillis() - started.get());
              counter.set(value);
              onIdleCalled.incrementAndGet();
            });

    watcher =
        DirectoryWatcher.builder()
            .paths(Arrays.asList(d1))
            .listener(composite)
            .fileHashing(true)
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

    Map<Path, ChangeSet> changeSet = composite.getChangeSet();
    assertTrue(!changeSet.isEmpty());
    assertEquals(7, changeSet.get(d1).created().size());

    watcher.close();
  }

  private class CompositeListener implements DirectoryChangeListener {

    private final ChangeSetListener changeSetListener = new ChangeSetListener();
    private final OnTimeoutListener onTimeoutListener;

    private CompositeListener(int timeout, Consumer<Integer> consumer) {
      this.onTimeoutListener = new OnTimeoutListener(timeout).onTimeout(consumer::accept);
    }

    @Override
    public void onIdle(int count) {
      onTimeoutListener.onIdle(count);
    }

    @Override
    public void onEvent(DirectoryChangeEvent event) throws IOException {
      changeSetListener.onEvent(event);
      onTimeoutListener.onEvent(event);
    }

    public Map<Path, ChangeSet> getChangeSet() {
      return changeSetListener.getChangeSet();
    }
  }
}
