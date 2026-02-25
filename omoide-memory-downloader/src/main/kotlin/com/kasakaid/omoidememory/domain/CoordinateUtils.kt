package com.kasakaid.omoidememory.domain

import java.math.RoundingMode

object CoordinateUtils {
    /**
     * 丸めた緯度経度を返す関数（デフォルトは小数点第3位）
     * 110m四方の精度となるため、位置情報のゆるいキャッシュに最適
     */
    fun roundCoordinate(
        value: Double,
        scale: Int = 3,
    ): Double = value.toBigDecimal().setScale(scale, RoundingMode.HALF_UP).toDouble()
}
