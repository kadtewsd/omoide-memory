rootProject.name = "思い出のためのレコード情報操作のためのダウンローダー"

// まずこういうプロジェクトいるよと教える
include(":omoide-memory-jooq")
// 教えられたプロジェクトに物理的なパスをいれる
project(":omoide-memory-jooq").projectDir = file("../omoide-memory-jooq")