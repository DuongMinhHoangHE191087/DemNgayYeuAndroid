package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.util.Birthday
import com.example.ui.util.ProfileUtils

/** Ô ngày sinh duy nhất: chỉ chọn từ lịch (1920..hôm nay), giá trị luôn là dd/MM/yyyy. */
@Composable
fun BirthdayField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  dialogTitle: String? = null,
  isEnglish: Boolean = false,
  testTag: String = "date_picker_field",
) {
  InLoveDatePickerField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    modifier = modifier,
    dialogTitle = dialogTitle,
    quickPresets = DatePickerPresets.birthDatePresets(isEnglish),
    helperText = Birthday.parse(value)?.let {
      listOf(ProfileUtils.formatAgeDisplay(it.age, isEnglish), ProfileUtils.formatZodiacDisplay(it.zodiac, isEnglish))
        .filter(String::isNotBlank)
        .joinToString(" • ")
    }?.ifBlank { null },
    isEnglish = isEnglish,
    testTag = testTag,
    pickerOnly = true,
    yearRange = DatePickerRules.birthYearRange(),
    minMillis = DatePickerRules.birthMinDay(),
    maxDateMillis = DatePickerRules.todayAsUtcDay(System.currentTimeMillis(), java.util.TimeZone.getDefault()),
    onClear = { onValueChange("") },
  )
}
