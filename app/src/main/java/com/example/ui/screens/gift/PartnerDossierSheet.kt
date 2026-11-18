@file:Suppress("FunctionName")
package com.example.ui.screens.gift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.dossier.LoveLanguage
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerFoodAndCare
import com.example.shared.dossier.PartnerInterestCatalog
import com.example.shared.dossier.PartnerSizes
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.designsystem.InLoveChoiceChip
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveSheetModal
import com.example.ui.designsystem.InLoveTextField
import com.example.ui.designsystem.InLoveTintedCard
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartnerDossierSheet(
  dossier: PartnerDossier,
  isVip: Boolean,
  isEnglish: Boolean,
  onDismiss: () -> Unit,
  onSaveDossier: (PartnerDossier) -> Unit,
  onAddSecretNote: (noteText: String, occasionHint: String) -> Unit,
  onToggleSecretNotePurchased: (noteId: String, purchased: Boolean) -> Unit,
  onRemoveSecretNote: (noteId: String) -> Unit,
  onOpenPaywall: () -> Unit,
) {
  var nickname by remember(dossier) { mutableStateOf(dossier.nickname) }
  var selectedLanguages by remember(dossier) { mutableStateOf(dossier.loveLanguages) }
  var selectedInterests by remember(dossier) { mutableStateOf(dossier.interestCategories) }
  var freeformLikes by remember(dossier) { mutableStateOf(dossier.freeformLikes) }
  var shirtSize by remember(dossier) { mutableStateOf(dossier.sizes.shirtSize) }
  var pantsSize by remember(dossier) { mutableStateOf(dossier.sizes.pantsOrDressSize) }
  var shoeSize by remember(dossier) { mutableStateOf(dossier.sizes.shoeSize) }
  var ringSize by remember(dossier) { mutableStateOf(dossier.sizes.ringSize) }
  var colorsCsv by remember(dossier) { mutableStateOf(dossier.sizes.favoriteColors.joinToString(", ")) }
  var scentsCsv by remember(dossier) { mutableStateOf(dossier.sizes.favoriteScents.joinToString(", ")) }
  var foodsCsv by remember(dossier) { mutableStateOf(dossier.foodAndCare.favoriteFoods.joinToString(", ")) }
  var goToDrinks by remember(dossier) { mutableStateOf(dossier.foodAndCare.goToDrinks) }
  var allergiesCsv by remember(dossier) { mutableStateOf(dossier.foodAndCare.allergies.joinToString(", ")) }
  var taboosCsv by remember(dossier) { mutableStateOf(dossier.foodAndCare.dislikesOrTaboos.joinToString(", ")) }
  var budgetText by remember(dossier) {
    mutableStateOf(if (dossier.budgetMaxVnd > 0L) dossier.budgetMaxVnd.toString() else "")
  }
  var region by remember(dossier) { mutableStateOf(dossier.occasionRegion.ifBlank { "VN" }) }

  fun splitCsv(raw: String): List<String> =
    raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

  InLoveSheetModal(
    title = if (isEnglish) "Sweetheart Dossier" else "Hồ Sơ Người Thương",
    subtitle = if (isEnglish) "Everything that makes them smile" else "Tất cả điều nhỏ bé khiến người ấy hạnh phúc",
    icon = Icons.Filled.Favorite,
    onDismiss = onDismiss,
    closeContentDescription = if (isEnglish) "Close" else "Đóng",
    closeTestTag = "btn_close_partner_dossier",
    footer = {
      PrimaryGradientButton(
        text = if (isEnglish) "Save Dossier" else "Lưu Hồ Sơ Người Thương",
        icon = Icons.Filled.Check,
        onClick = {
          onSaveDossier(
            dossier.copy(
              nickname = nickname.trim(),
              loveLanguages = selectedLanguages,
              interestCategories = selectedInterests,
              freeformLikes = freeformLikes.trim(),
              sizes = PartnerSizes(
                shirtSize = shirtSize.trim(),
                pantsOrDressSize = pantsSize.trim(),
                shoeSize = shoeSize.trim(),
                ringSize = ringSize.trim(),
                favoriteColors = splitCsv(colorsCsv),
                favoriteScents = splitCsv(scentsCsv)
              ),
              foodAndCare = PartnerFoodAndCare(
                favoriteFoods = splitCsv(foodsCsv),
                goToDrinks = goToDrinks.trim(),
                allergies = splitCsv(allergiesCsv),
                dislikesOrTaboos = splitCsv(taboosCsv)
              ),
              budgetMaxVnd = budgetText.toLongOrNull() ?: 0L,
              occasionRegion = region
            )
          )
          onDismiss()
        },
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = MinTouchBox),
        testTag = "btn_save_partner_dossier"
      )
    }
  ) {
    InLoveTextField(
      value = nickname,
      onValueChange = { nickname = it.take(40) },
      label = if (isEnglish) "Affectionate nickname" else "Biệt danh thân mật",
      placeholder = if (isEnglish) "e.g. Bé Mèo, Em yêu" else "VD: Bé Mèo, Anh nhà",
      modifier = Modifier.fillMaxWidth(),
      testTag = "dossier_nickname_field"
    )

    Text(
      text = if (isEnglish) "Love Languages" else "Ngôn ngữ tình yêu",
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      color = TextDarkPlum
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      LoveLanguage.entries.forEach { lang ->
        val selected = lang in selectedLanguages
        InLoveChoiceChip(
          label = if (isEnglish) lang.titleEn else lang.titleVi,
          selected = selected,
          onClick = {
            selectedLanguages = if (selected) selectedLanguages - lang else selectedLanguages + lang
          },
          testTag = "dossier_lang_${lang.name}"
        )
      }
    }

    Text(
      text = if (isEnglish) "Interest Categories" else "Nhóm sở thích",
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      color = TextDarkPlum
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      PartnerInterestCatalog.CATEGORIES.forEach { cat ->
        val selected = cat.key in selectedInterests
        InLoveChoiceChip(
          label = "${cat.emoji} ${if (isEnglish) cat.labelEn else cat.labelVi}",
          selected = selected,
          onClick = {
            selectedInterests = if (selected) selectedInterests - cat.key else selectedInterests + cat.key
          },
          testTag = "dossier_interest_${cat.key}"
        )
      }
    }

    InLoveTextField(
      value = freeformLikes,
      onValueChange = { freeformLikes = it.take(200) },
      label = if (isEnglish) "More likes & hobbies" else "Sở thích chi tiết khác",
      placeholder = if (isEnglish) "matcha, vinyl, cozy cafes" else "matcha, nến thơm, quán yên tĩnh",
      singleLine = false,
      maxLines = 2,
      modifier = Modifier.fillMaxWidth()
    )

    InLoveTintedCard {
      Text(
        text = if (isEnglish) "Sizes, Colors & Scents" else "Size, Màu Sắc & Mùi Hương",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextDarkPlum
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InLoveTextField(
          value = shirtSize,
          onValueChange = { shirtSize = it.take(12) },
          label = if (isEnglish) "Shirt" else "Size áo",
          modifier = Modifier.weight(1f)
        )
        InLoveTextField(
          value = pantsSize,
          onValueChange = { pantsSize = it.take(12) },
          label = if (isEnglish) "Pants/Dress" else "Quần/váy",
          modifier = Modifier.weight(1f)
        )
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InLoveTextField(
          value = shoeSize,
          onValueChange = { shoeSize = it.take(12) },
          label = if (isEnglish) "Shoe" else "Giày",
          modifier = Modifier.weight(1f)
        )
        InLoveTextField(
          value = ringSize,
          onValueChange = { ringSize = it.take(12) },
          label = if (isEnglish) "Ring" else "Nhẫn",
          modifier = Modifier.weight(1f)
        )
      }
      InLoveTextField(
        value = colorsCsv,
        onValueChange = { colorsCsv = it.take(120) },
        label = if (isEnglish) "Favorite colors (comma-separated)" else "Màu yêu thích (cách nhau dấu phẩy)",
        modifier = Modifier.fillMaxWidth()
      )
      InLoveTextField(
        value = scentsCsv,
        onValueChange = { scentsCsv = it.take(120) },
        label = if (isEnglish) "Signature scents" else "Mùi hương ưa thích",
        modifier = Modifier.fillMaxWidth()
      )
    }

    InLoveTintedCard(
      containerColor = InLoveColors.WarningBg.copy(alpha = 0.55f),
      borderColor = InLoveColors.WarningBorder
    ) {
      Text(
        text = if (isEnglish) "Food, Allergies & Taboos" else "Ẩm Thực, Dị Ứng & Điều Kiêng",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextDarkPlum
      )
      Text(
        text = if (isEnglish) "AI gift advice excludes allergies and taboos automatically." else "Trợ lý quà tặng sẽ tự động tránh mọi món gây dị ứng hoặc không thích.",
        fontSize = 11.sp,
        color = TextSubtlePlum
      )
      InLoveTextField(
        value = foodsCsv,
        onValueChange = { foodsCsv = it.take(140) },
        label = if (isEnglish) "Favorite foods" else "Món ăn ruột",
        modifier = Modifier.fillMaxWidth()
      )
      InLoveTextField(
        value = goToDrinks,
        onValueChange = { goToDrinks = it.take(80) },
        label = if (isEnglish) "Go-to drink" else "Đồ uống quen thuộc",
        modifier = Modifier.fillMaxWidth()
      )
      InLoveTextField(
        value = allergiesCsv,
        onValueChange = { allergiesCsv = it.take(140) },
        label = if (isEnglish) "Allergies (e.g. seafood, strong perfume)" else "Dị ứng (VD: hải sản, hương liệu nồng)",
        modifier = Modifier.fillMaxWidth(),
        testTag = "dossier_allergies_field"
      )
      InLoveTextField(
        value = taboosCsv,
        onValueChange = { taboosCsv = it.take(140) },
        label = if (isEnglish) "Dislikes / taboos" else "Không thích / điều kiêng",
        modifier = Modifier.fillMaxWidth(),
        testTag = "dossier_taboos_field"
      )
    }

    InLoveTintedCard {
      Text(
        text = if (isEnglish) "Budget & Occasion Region" else "Ngân Sách & Vùng Dịp Lễ",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextDarkPlum
      )
      InLoveTextField(
        value = budgetText,
        onValueChange = { budgetText = it.filter(Char::isDigit).take(12) },
        label = if (isEnglish) "Target gift budget (VND)" else "Ngân sách dự kiến (VND)",
        placeholder = "500000",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InLoveChoiceChip(
          label = if (isEnglish) "Vietnam" else "Việt Nam",
          selected = region == "VN",
          icon = Icons.Filled.Flag,
          onClick = { region = "VN" }
        )
        InLoveChoiceChip(
          label = if (isEnglish) "International" else "Quốc tế",
          selected = region == "INTL",
          icon = Icons.Filled.Public,
          onClick = { region = "INTL" }
        )
      }
    }

    PartnerDossierSecretNotesBlock(
      notes = dossier.secretSurpriseNotes,
      isVip = isVip,
      isEnglish = isEnglish,
      onAddNote = onAddSecretNote,
      onTogglePurchased = onToggleSecretNotePurchased,
      onRemoveNote = onRemoveSecretNote,
      onOpenPaywall = onOpenPaywall
    )
  }
}
