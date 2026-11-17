package com.example.billing

/** Sealed class đại diện cho các sự kiện thanh toán được emit lên UI */
sealed class PurchaseEvent {
  data object Success : PurchaseEvent()
  data object Pending : PurchaseEvent()
  data class Error(val message: String) : PurchaseEvent()
}
