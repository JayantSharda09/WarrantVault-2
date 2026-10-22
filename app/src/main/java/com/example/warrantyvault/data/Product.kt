package com.example.warrantyvault.data

data class Product(
    val id: String = "",
    val productName: String = "",
    val brand: String = "",
    val price: Double = 0.0,
    val purchaseDate: Long = 0L,
    val warrantyMonths: Int = 0,
    val expiryDate: Long = 0L,
    val invoicePath: String? = null,
    val userId: String = ""
)
