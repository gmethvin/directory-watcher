/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.methvin.watcher;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class OnTimeoutListener implements DirectoryChangeListener {

  private final int timeoutMillis;
  private final AtomicReference<ScheduledFuture> currentTaskRef = new AtomicReference<>();
  private ScheduledExecutorService service;
  private Consumer<Integer> consumer;

  public OnTimeoutListener(int timeoutMillis) {
    this.timeoutMillis = timeoutMillis;

    if (timeoutMillis >= 0) {
      service = Executors.newSingleThreadScheduledExecutor();
    }
  }

  @Override
  public void onEvent(DirectoryChangeEvent event) {
    ScheduledFuture taskToCancel = currentTaskRef.getAndSet(null);
    if (taskToCancel != null) {
      taskToCancel.cancel(false);
    }
  }

  @Override
  public void onIdle(int count) {
    if (timeoutMillis >= 0) {
      currentTaskRef.getAndUpdate(
          oldTask -> {
            if (oldTask != null) {
              oldTask.cancel(false);
            }
            return service.schedule(
                () -> {
                  if (consumer != null) {
                    consumer.accept(count);
                  }
                },
                timeoutMillis,
                TimeUnit.MILLISECONDS);
          });
    }
  }

  public OnTimeoutListener onTimeout(Consumer<Integer> consumer) {
    this.consumer = consumer;
    return this;
  }
}
