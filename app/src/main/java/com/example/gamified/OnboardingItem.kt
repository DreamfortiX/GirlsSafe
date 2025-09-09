package com.example.gamified

data class OnboardingItem(
    val imageRes: Int,
    val title: String,
    val description: String,
    val description1: String = "",
    val description2: String = "",
    val description3: String = ""
)