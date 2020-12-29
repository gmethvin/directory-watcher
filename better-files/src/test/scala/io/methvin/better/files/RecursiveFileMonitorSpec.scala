package io.methvin.better.files

import better.files._
import io.methvin.watcher.hashing.FileHasher
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class RecursiveFileMonitorSpec extends AnyWordSpec with Matchers {

  "RecursiveFileMonitor" should {
    "watch single files" in {
      val directory = File("target/file-test/").createDirectories()
      directory.clear()
      val file = directory.createChild("file.txt").writeText("Hello world!")

      var log = List.empty[String]
      def output(msg: String) = synchronized {
        println(msg)
        log = msg :: log
      }

      val watcher = new RecursiveFileMonitor(file) {
        override def onCreate(file: File, count: Int) = output(s"$file got created $count time(s)")
        override def onModify(file: File, count: Int) = output(s"$file got modified $count time(s)")
        override def onDelete(file: File, count: Int) = output(s"$file got deleted $count time(s)")

        override def onException(exception: Throwable): Unit = {
          exception.printStackTrace()
        }
      }
      watcher.start()

      sleep()
      file.writeText("hello world"); sleep()
      file.clear(); sleep()
      file.writeText("howdy"); sleep()
      file.delete(); sleep()
      sleep()
      val sibling = (file.parent / "t1.txt").createIfNotExists(); sleep()
      sibling.writeText("hello world"); sleep()
      sleep()

      log.size should be >= 2
      log.exists(_ contains sibling.name) shouldBe false
      log.forall(_ contains file.name) shouldBe true
    }

    "watch entire directories" in {
      val directory = File("target/recursive-test/").createDirectories()
      directory.clear()

      var log = List.empty[String]
      def output(msg: String) = synchronized {
        println(msg)
        log = msg :: log
      }

      val watcher = new RecursiveFileMonitor(directory) {
        override def onCreate(file: File, count: Int) = output(s"$file got created $count time(s)")
        override def onModify(file: File, count: Int) = output(s"$file got modified $count time(s)")
        override def onDelete(file: File, count: Int) = output(s"$file got deleted $count time(s)")

        override def onException(exception: Throwable): Unit = {
          exception.printStackTrace()
        }
      }
      watcher.start()

      sleep()
      val f1 = (directory / "f1.txt").createIfNotExists()
      f1.writeText("hello world"); sleep()
      f1.clear(); sleep()
      f1.writeText("howdy"); sleep()
      f1.delete(); sleep()
      val f2 = (directory / "f2.txt").createIfNotExists(); sleep()
      f2.writeText("hello world"); sleep()
      val f3 = (directory / "f3.txt").createIfNotExists(); sleep()
      f3.writeText("hello world"); sleep()
      val d1 = (directory / "d1").createDirectories(); sleep()
      val df1 = (d1 / "df1.txt").createIfNotExists(); sleep()
      df1.writeText("goodbye world"); sleep()
      d1.delete()
      sleep(5.seconds)

      log.size should be >= 12
      log.exists(_ contains f1.name) shouldBe true
      log.exists(_ contains f2.name) shouldBe true
      log.exists(_ contains f3.name) shouldBe true
      log.exists(_ contains d1.name) shouldBe true
      log.exists(_ contains df1.name) shouldBe true
    }
  }

  def sleep(t: FiniteDuration = 1.second): Unit = Thread.sleep(t.toMillis)
}
