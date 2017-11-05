package org.revent

import scala.language.implicitConversions

case class Version(version: Int) extends AnyVal {
  def nextVersion: Version = Version(next)
  def nextVersions: Stream[Version] = Stream.from(next).map(Version.apply)
  def isFirst: Boolean = this == Version.First
  private def next: Int = version + 1
}

object Version {
  val First: Version = Version(0)
  implicit def versionToInt(version: Version): Int = version.version
  implicit def intToVersion(version: Int): Version = Version(version)
}
