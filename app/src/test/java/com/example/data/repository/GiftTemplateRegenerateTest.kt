package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.InLoveDao
import com.example.data.model.GiftIdeaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GiftTemplateRegenerateTest {

  private val vietnameseLetters = Regex("[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]", RegexOption.IGNORE_CASE)

  private fun newDb(): AppDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
  }

  private fun repo(dao: InLoveDao) = InLoveRepository(dao, ApplicationProvider.getApplicationContext<Context>())

  private fun row(remoteId: String) =
    GiftIdeaEntity(title = "t", category = "c", badgeText = "", tag = "", description = "", imageUrl = "", remoteId = remoteId)

  @Test
  fun regenerate_removesOldNonFavoritesAndKeepsFavorites() = runBlocking {
    val db = newDb()
    val dao = db.inLoveDao()
    val repository = repo(dao)

    repository.generateAiGiftSuggestions("An", setOf("coffee", "travel"), "Anniversary")
    val travel = dao.getAllGiftIdeas().first().first { it.remoteId == "tpl_travel_Anniversary_An" }
    dao.updateGiftIdea(travel.copy(isFavorited = true))

    val returned = repository.generateAiGiftSuggestions("Binh", setOf("coffee"), "Anniversary").getOrThrow()
    val all = dao.getAllGiftIdeas().first()

    val an = all.filter { it.remoteId.endsWith("_An") }.map { it.remoteId }
    assert(an == listOf("tpl_travel_Anniversary_An")) { "only the favorited An row may remain: $an" }
    val binh = all.filter { it.remoteId.endsWith("_Binh") }.map { it.remoteId }.toSet()
    assert(binh == setOf("tpl_coffee_Anniversary_Binh", "tpl_book_Anniversary_Binh")) { "Binh rows missing: $binh" }
    assert(returned.map { it.remoteId }.toSet() == binh) { "returned ids must be the Binh ids: ${returned.map { it.remoteId }}" }
    returned.forEach { r ->
      assert(all.first { it.remoteId == r.remoteId }.id == r.id) { "returned id must equal stored id for ${r.remoteId}" }
    }
    db.close()
  }

  @Test
  fun regenerate_keepsNonTemplateRows() = runBlocking {
    val db = newDb()
    val dao = db.inLoveDao()
    val repository = repo(dao)

    dao.upsertGiftIdeaByRemoteId(row("doc_1"))
    dao.upsertGiftIdeaByRemoteId(row("tplX_keep"))
    repository.generateAiGiftSuggestions("An", setOf("coffee"), "Anniversary")

    val ids = dao.getAllGiftIdeas().first().map { it.remoteId }
    assert("doc_1" in ids) { "a catalog row must survive regeneration: $ids" }
    assert("tplX_keep" in ids) { "tplX_ is not a template prefix and must survive: $ids" }
    db.close()
  }

  @Test
  fun english_rowsHaveEnglishTextAndVietnameseCategory() = runBlocking {
    val db = newDb()
    val dao = db.inLoveDao()
    val repository = repo(dao)

    val rows = repository.generateAiGiftSuggestions("An", setOf("coffee", "travel", "cinema"), "Anniversary", isEnglish = true).getOrThrow()

    assert(rows.size == 4) { "expected 4 rows: got ${rows.size}" }
    val coffee = rows.first { it.remoteId.startsWith("tpl_coffee") }
    assert(coffee.title == "Custom-Engraved Thermos & Specialty Coffee Set for An") { "coffee title was: ${coffee.title}" }
    assert(coffee.category == "Trải nghiệm & Thưởng thức") { "category stays the Vietnamese key: ${coffee.category}" }
    rows.forEach { r ->
      listOf(r.title, r.badgeText, r.tag, r.description, r.detailsSnippet, r.actionText).forEach { text ->
        assert(!vietnameseLetters.containsMatchIn(text)) { "Vietnamese text in English mode (${r.remoteId}): $text" }
      }
    }
    db.close()
  }

  @Test
  fun languageSwitch_keepsIdsAndFallsBackPerLanguage() = runBlocking {
    val db = newDb()
    val dao = db.inLoveDao()
    val repository = repo(dao)
    val interests = setOf("coffee", "travel")

    val vi = repository.generateAiGiftSuggestions("An", interests, "Anniversary").getOrThrow()
    val en = repository.generateAiGiftSuggestions("An", interests, "Anniversary", isEnglish = true).getOrThrow()
    assert(vi.map { it.remoteId }.toSet() == en.map { it.remoteId }.toSet()) { "ids must not depend on language" }
    assert(dao.getAllGiftIdeas().first().size == vi.size) { "language switch must not add rows" }

    val viAgain = repository.generateAiGiftSuggestions("An", interests, "Anniversary").getOrThrow()
    val viCoffee = viAgain.first { it.remoteId.startsWith("tpl_coffee") }
    assert(viCoffee.title == "Set Bình Giữ Nhiệt Khắc Tên & Cà Phê Đặc Sản Cho An") { "vi title was: ${viCoffee.title}" }

    val blankVi = repository.generateAiGiftSuggestions("", setOf("coffee"), "Anniversary").getOrThrow()
    val blankEn = repository.generateAiGiftSuggestions("", setOf("coffee"), "Anniversary", isEnglish = true).getOrThrow()
    assert(blankVi.all { it.remoteId.endsWith("_Người thương") }) { "vi ids: ${blankVi.map { it.remoteId }}" }
    assert(blankEn.all { it.remoteId.endsWith("_Người thương") }) { "en ids: ${blankEn.map { it.remoteId }}" }
    val blankEnCoffee = blankEn.first { it.remoteId.startsWith("tpl_coffee") }
    assert(blankEnCoffee.title.endsWith("for Your Sweetheart")) { "en blank title was: ${blankEnCoffee.title}" }
    db.close()
  }
}
