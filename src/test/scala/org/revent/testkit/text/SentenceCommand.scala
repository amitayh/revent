package org.revent.testkit.text

import scala.collection.immutable.Seq
import scala.util.{Success, Try}

sealed trait SentenceCommand extends Function[Seq[String], Try[Seq[SentenceEvent]]]

case class AddWord(word: String) extends SentenceCommand {
  override def apply(sentence: Seq[String]): Try[Seq[SentenceEvent]] = Success(WordAdded(word) :: Nil)
}
