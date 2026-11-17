package com.example.data.cloud

// Region của Cloud Functions. client-contract.test.js đọc hằng này và so với config.js.
const val CLOUD_FUNCTIONS_REGION = "asia-southeast1"

// Gồm cả khởi động lạnh của function.
const val CALLABLE_TIMEOUT_MS = 20_000L
