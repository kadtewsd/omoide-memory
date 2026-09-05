package com.kasakaid.omoidememory.extension

import com.kasakaid.omoidememory.ui.maintenance.requestprocess.UploadPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SealedClassesTest {
    @Test
    fun `Companion から親のクラスを辿って sealed を取得できる`() {
        val result = UploadPoint.sealedClassesFromCompanion<UploadPoint.Companion, UploadPoint>()
        assertThat(result).describedAs("問題なく sealed クラスが取得できること").isOfAnyClassIn(ArrayList<UploadPoint>()::class.java)
        assertThat(result.size).describedAs("親クラスを辿って sealed を取得できること").isGreaterThan(0)
    }

    @Test
    fun `親クラスを辿って sealed を取得できる`() {
        val result = UploadPoint::class.sealedClasses()
        assertThat(result).describedAs("問題なく sealed クラスが取得できること").isOfAnyClassIn(ArrayList<UploadPoint>()::class.java)
        assertThat(result.size).describedAs("親クラスを辿って sealed を取得できること").isGreaterThan(0)
    }
}
