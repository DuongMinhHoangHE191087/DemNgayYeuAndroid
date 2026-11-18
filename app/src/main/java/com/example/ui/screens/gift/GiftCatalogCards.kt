@file:Suppress("FunctionName")
package com.example.ui.screens.gift

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NaturePeople
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GiftIdeaEntity
import com.example.domain.content.GiftCatalog
import com.example.ui.components.AppDialog
import com.example.ui.components.animateDialogEntrance
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryFixed
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.Tertiary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

@Composable
fun WishlistNotebookDialogContent(
  giftIdeas: List<GiftIdeaEntity>,
  isEnglish: Boolean,
  onFavoriteToggle: (GiftIdeaEntity) -> Unit,
  onItemClick: (GiftIdeaEntity) -> Unit,
  onDismiss: () -> Unit
) {
  val favorites = remember(giftIdeas) { giftIdeas.filter { it.isFavorited } }

  AppDialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(InLoveRadii.Sheet),
      color = Color.White,
      shadowElevation = 16.dp,
      modifier = Modifier
        .widthIn(max = 380.dp)
        .fillMaxWidth(0.92f)
        .animateDialogEntrance()
        .fillMaxHeight(0.85f)
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.BookmarkBorder,
              contentDescription = null,
              tint = Secondary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = if (isEnglish) "Wishlist" else "Yêu thích",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkPlum
              )
              Text(
                text = "${favorites.size}",
                fontSize = 11.sp,
                color = TextSubtlePlum
              )
            }
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(MinTouchBox)) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = if (isEnglish) "Close" else "Đóng",
              tint = TextSubtlePlum
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (favorites.isEmpty()) {
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Filled.BookmarkBorder,
              contentDescription = null,
              tint = TextSubtlePlum.copy(alpha = 0.5f),
              modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = if (isEnglish) "No favorites yet" else "Chưa có mục yêu thích",
              fontWeight = FontWeight.SemiBold,
              color = TextSubtlePlum
            )
          }
        } else {
          LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(favorites, key = { it.id }) { idea ->
              GiftIdeaCardContent(
                idea = idea,
                isEnglish = isEnglish,
                onFavoriteToggle = { onFavoriteToggle(idea) },
                onActionClick = { onItemClick(idea) }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun GiftIdeaCardContent(
  idea: GiftIdeaEntity,
  isEnglish: Boolean = false,
  onFavoriteToggle: () -> Unit,
  onActionClick: () -> Unit
) {
  val gift = GiftCatalog.localized(idea, isEnglish)
  val accent = if (idea.isAiGenerated) Tertiary else Secondary

  Card(
    shape = RoundedCornerShape(InLoveRadii.Card),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("gift_idea_${idea.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(170.dp)
          .clip(RoundedCornerShape(18.dp))
      ) {
        AsyncImage(
          model = idea.imageUrl,
          contentDescription = gift.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )

        Surface(
          shape = RoundedCornerShape(InLoveRadii.Chip),
          color = Color.White.copy(alpha = 0.9f),
          modifier = Modifier
            .padding(10.dp)
            .align(Alignment.TopStart)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = giftCategoryIcon(gift.categoryKey),
              contentDescription = null,
              tint = accent,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = gift.badge,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = accent
            )
          }
        }

        if (gift.isSeed) {
          GiftIllustrationCaption(
            isEnglish = isEnglish,
            modifier = Modifier
              .padding(10.dp)
              .align(Alignment.BottomStart)
          )
        }

        IconButton(
          onClick = onFavoriteToggle,
          modifier = Modifier
            .padding(2.dp)
            .align(Alignment.TopEnd)
            .size(MinTouchBox)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.88f))
        ) {
          Icon(
            imageVector = if (idea.isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isEnglish) "Favorite" else "Yêu thích",
            tint = Primary,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Text(
          text = gift.title,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = TextDarkPlum,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          shape = RoundedCornerShape(InLoveRadii.Badge),
          color = if (idea.isAiGenerated) SecondaryFixed.copy(alpha = 0.7f) else PrimaryFixed.copy(alpha = 0.6f)
        ) {
          Text(
            text = gift.tag,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (idea.isAiGenerated) Secondary else Primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = gift.description,
        fontSize = 12.sp,
        color = TextSubtlePlum,
        lineHeight = 17.sp
      )

      GiftCatalog.priceLabel(idea.priceRange, isEnglish)?.let { price ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = price,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = Primary
        )
      }

      if (gift.detailsSnippet.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = SurfaceContainerLow,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = gift.detailsSnippet,
            fontSize = 11.sp,
            color = TextSubtlePlum,
            lineHeight = 16.sp,
            modifier = Modifier.padding(10.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Surface(
        shape = RoundedCornerShape(InLoveRadii.Pill),
        color = SurfaceContainer,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = MinTouchBox)
          .clip(RoundedCornerShape(InLoveRadii.Pill))
          .clickable(onClick = onActionClick)
      ) {
        Row(
          modifier = Modifier.padding(vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = giftCategoryIcon(gift.categoryKey),
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = gift.actionText,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
          )
        }
      }
    }
  }
}

@Composable
fun GiftIllustrationCaption(isEnglish: Boolean, modifier: Modifier = Modifier) {
  Surface(
    shape = RoundedCornerShape(InLoveRadii.Chip),
    color = Color.Black.copy(alpha = 0.4f),
    modifier = modifier
  ) {
    Text(
      text = if (isEnglish) "Illustration" else "Ảnh minh họa",
      fontSize = 10.sp,
      color = Color.White,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
  }
}

fun giftChipIcon(key: String): ImageVector = when (key) {
  GiftCatalog.ALL_KEY -> Icons.Filled.GridView
  GiftCatalog.AI_KEY -> Icons.Filled.AutoFixHigh
  else -> giftCategoryIcon(key)
}

fun giftCategoryIcon(categoryKey: String): ImageVector = when (categoryKey) {
  GiftCatalog.ROMANTIC -> Icons.Filled.Favorite
  GiftCatalog.JEWELRY -> Icons.Filled.AutoAwesome
  GiftCatalog.HANDMADE -> Icons.Filled.Brush
  GiftCatalog.DATES -> Icons.Filled.Restaurant
  GiftCatalog.SECRET -> Icons.Filled.CardGiftcard
  else -> Icons.Filled.NaturePeople
}
