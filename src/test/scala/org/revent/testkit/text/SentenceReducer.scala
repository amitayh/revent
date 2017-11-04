package org.revent.testkit.text

import org.revent.Reducer.AggregateReducer

import scala.collection.immutable.Seq

object SentenceReducer extends AggregateReducer[SentenceProtocol] {

  override def empty: Seq[String] = Nil

  override def handle(sentence: Seq[String], event: SentenceEvent): Seq[String] = event match {
    case WordAdded(word: String) => sentence :+ word
  }

}
