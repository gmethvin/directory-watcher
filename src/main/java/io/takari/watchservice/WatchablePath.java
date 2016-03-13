package io.takari.watchservice;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.Arrays;

public class WatchablePath implements Watchable {

  private final Path file;

  public WatchablePath(Path file) {
    if (file == null) {
      throw new NullPointerException("file must not be null");
    }
    this.file = file;
  }

  public Path getFile() {
    return file;
  }

  @Override
  public WatchKey register(WatchService watcher,
    WatchEvent.Kind<?>[] events,
    WatchEvent.Modifier... modifiers)
      throws IOException {
    if (watcher == null)
      throw new NullPointerException();
    if (!(watcher instanceof AbstractWatchService))
      throw new ProviderMismatchException();
    return ((AbstractWatchService) watcher).register(this, Arrays.asList(events));
  }

  private static final WatchEvent.Modifier[] NO_MODIFIERS = new WatchEvent.Modifier[0];

  @Override
  public final WatchKey register(WatchService watcher,
    WatchEvent.Kind<?>... events)
      throws IOException {
    if (!file.toFile().exists()) {
      throw new RuntimeException("Directory to watch doesn't exist: " + file);
    }
    return register(watcher, events, NO_MODIFIERS);
  }

  @Override
  public String toString() {
    return "Path{" +
      "file=" + file +
      '}';
  }
}
