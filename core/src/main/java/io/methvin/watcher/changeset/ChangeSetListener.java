package io.methvin.watcher.changeset;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class ChangeSetListener implements DirectoryChangeListener {

  private final Object lock = new Object() {};

  private final AtomicLong onIdleTimeout = new AtomicLong(-1);

  private final Timer timer = new Timer();

  private Map<Path, ChangeSetBuilder> changeBuilders = new HashMap<>();

  private DelayedPoll delayedPoll;

  private boolean active;

  public ChangeSetListener() {}

  /**
   * Flushes and returns a shallow copy the current ChangeSet.
   * This copies over all the entries and resets the internal collector map.
   *
   * @return
   */
  public Map<Path, ChangeSet> getChangeSet() {
    Map<Path, ChangeSetBuilder> returnBuilders;
    synchronized (lock) {
      returnBuilders = changeBuilders;
      changeBuilders = new HashMap<>();
    }
    return returnBuilders.entrySet().stream()
                         .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toChangeSet()));
  }

  /**
   * Blocking method that returns the ChangeSet after DirectoryWatcher has been Idle for more the specified timout.
   * Note this method may only block a single caller, and will throw an exception if a second caller is added
   * before the existing has returned.
   * @param timeout
   * @return
   */
  public Map<Path, ChangeSet> takeChange(int timeout) { // TODO add optional filter, will go back into waiting if it is not true
    synchronized (lock) {
      if (onIdleTimeout.compareAndSet(-1, timeout)) { // make sure there is not an existing poller
        if (!active && !changeBuilders.keySet().isEmpty()) {
          // It's inactive, but there are changes, so schedule the delay.
          // This is needed as onIdle will not be called to trigger this, unless another file is received.
          delayedPoll = new DelayedPoll(timeout); // this cannot exist before,
                                                  // as compareAndSet ensures no previous poller exists
          timer.schedule(delayedPoll, timeout);
        }
        try {
          wait(); // will be woken up by the DelayedPoll (either scheduled by above or by the onIdle method)
          return getChangeSet();
        } catch (InterruptedException e) {
          throw new RuntimeException("Polling wait was interrupted.", e);
        }
      } else {
        throw new IllegalStateException("Only a single blocking getChangeSetOnIdle may called at any one time.");
      }
    }
  }

  @Override
  public void onIdle(int count) {
    synchronized (lock) {
      active = false;
      long timeout = onIdleTimeout.get(); // if the value is >= 0 there is a waiting poller
      if ( timeout == 0) {
        notify(); // notify instantly
      } else if (timeout > 0) {
        // onIdle has to follow a call to onEvent, so any existing timer must have been cancelled and nulled
        delayedPoll = new DelayedPoll(timeout);
        timer.schedule(delayedPoll, timeout);
      } // else do nothing, as there is no polling waiting for a notify
    }
  }

  @Override
  public void onEvent(DirectoryChangeEvent event) {
    Path rootPath = event.rootPath();
    Path path = event.path();

    synchronized (lock) {
      active = true;
      // Maintain a ChangeSet per rootPath
      ChangeSetBuilder builder = changeBuilders.get(rootPath);
      if (builder == null) {
        builder = new ChangeSetBuilder();
        changeBuilders.put(rootPath, builder);
      }

      ChangeSetEntry entry =
          new ChangeSetEntry(path, event.isDirectory(), event.hash(), event.rootPath());

      switch (event.eventType()) {
        case CREATE:
          builder.addCreated(entry);
          break;
        case MODIFY:
          builder.addModified(entry);
          break;
        case DELETE:
          builder.addDeleted(entry);
          break;
        case OVERFLOW:
          throw new IllegalStateException("OVERFLOW not yet handled");
      }

      if (delayedPoll != null) {
        // there is an onIdle timeout waiting, so cancel it
        delayedPoll.cancel();
        delayedPoll = null;
      }
    }
  }

  class DelayedPoll extends TimerTask {
    private final long timeout;

    public DelayedPoll(long timeout) {
      this.timeout = timeout;
    }

    @Override public void run() {
      synchronized (lock) {
        delayedPoll = null;
        notify();
      }
    }
  }

}
