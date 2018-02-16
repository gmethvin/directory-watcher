package io.methvin.better.files

import java.nio.file.{Path, WatchEvent}

import better.files._
import io.methvin.watcher.DirectoryChangeEvent.EventType
import io.methvin.watcher.{DirectoryChangeEvent, DirectoryChangeListener, DirectoryWatcher}

import scala.concurrent.ExecutionContext

/**
  * An implementation of the better-files `File.Monitor` interface using directory-watcher.
  */
abstract class RecursiveFileMonitor(val root: File) extends File.Monitor {

  protected[this] val pathToWatch: Option[Path] =
    if (root.exists) Some(if (root.isDirectory) root.path else root.parent.path) else None

  protected[this] def reactTo(path: Path): Boolean =
    path == null || root.isDirectory || root.isSamePathAs(path)

  protected[this] val watcher: DirectoryWatcher = DirectoryWatcher.create(
    pathToWatch.fold(java.util.Collections.emptyList[Path])(java.util.Collections.singletonList),
    new DirectoryChangeListener {
      override def onEvent(event: DirectoryChangeEvent): Unit = {
        if (reactTo(event.path)) {
          val et = event.eventType
          et match {
            case EventType.OVERFLOW =>
              onUnknownEvent(new WatchEvent[AnyRef] {
                override def kind = et.getWatchEventKind.asInstanceOf[WatchEvent.Kind[AnyRef]]
                override def count = event.count
                override def context = null
              })
            case _ =>
              RecursiveFileMonitor.this.onEvent(
                et.getWatchEventKind.asInstanceOf[WatchEvent.Kind[Path]],
                File(event.path),
                event.count
              )
          }
        }
      }

      override def onException(e: Exception): Unit = {
        RecursiveFileMonitor.this.onException(e)
      }
    }
  )

  override def start()(implicit executionContext: ExecutionContext): Unit = {
    executionContext.execute(() => watcher.watch())
  }

  override def close(): Unit = {
    watcher.close()
  }

  // override these so it works like the better-files monitor
  override def onCreate(file: File, count: Int): Unit = {}
  override def onModify(file: File, count: Int): Unit = {}
  override def onDelete(file: File, count: Int): Unit = {}
  override def onUnknownEvent(event: WatchEvent[_]): Unit = {}
  override def onException(exception: Throwable): Unit = {}
}
