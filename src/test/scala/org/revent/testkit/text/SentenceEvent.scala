package org.revent.testkit.text

sealed trait SentenceEvent

case class WordAdded(word: String) extends SentenceEvent
