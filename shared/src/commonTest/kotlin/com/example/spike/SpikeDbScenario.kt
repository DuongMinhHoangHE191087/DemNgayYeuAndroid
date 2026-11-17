package com.example.spike

import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals

/** Dung chung cho moi target: chen dung mot dong va doc lai nguyen van. */
suspend fun runSpikeScenario(db: SpikeDatabase) {
  val text = "tôi yêu em"
  db.spikeDao().insert(SpikeRow(text = text))
  val rows = db.spikeDao().all().first()
  assertEquals(1, rows.size)
  assertEquals(text, rows.single().text)
}
