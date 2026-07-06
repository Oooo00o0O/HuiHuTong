/*
 * QR encoding portions are adapted from Project Nayuki's QR Code generator.
 * Copyright © 2025 Project Nayuki. Licensed under the MIT License.
 * See the repository's THIRD_PARTY_NOTICES.md for the complete notice.
 */
package cn.ac.xjtlu.huihutong

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max

object QrCodeBitmap {
    private const val VERSION = 5
    private const val SIZE = 21 + 4 * (VERSION - 1)
    private const val DATA_CODEWORDS = 108
    private const val ECC_CODEWORDS = 26
    private const val QUIET_ZONE = 4

    fun create(text: String, targetSizePx: Int): Bitmap {
        val qr = encode(text)
        val moduleCount = SIZE + QUIET_ZONE * 2
        val scale = max(1, targetSizePx / moduleCount)
        val bitmapSize = moduleCount * scale
        val bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                if (qr[y][x]) {
                    val left = (x + QUIET_ZONE) * scale
                    val top = (y + QUIET_ZONE) * scale
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            bitmap.setPixel(left + dx, top + dy, Color.BLACK)
                        }
                    }
                }
            }
        }
        return bitmap
    }

    private fun encode(text: String): Array<BooleanArray> {
        val dataCodewords = makeDataCodewords(text)
        val ecc = ReedSolomon.computeRemainder(dataCodewords, ECC_CODEWORDS)
        val codewords = dataCodewords + ecc
        val modules = Array(SIZE) { IntArray(SIZE) { -1 } }
        val function = Array(SIZE) { BooleanArray(SIZE) }

        fun setFunction(x: Int, y: Int, black: Boolean) {
            if (x !in 0 until SIZE || y !in 0 until SIZE) return
            modules[y][x] = if (black) 1 else 0
            function[y][x] = true
        }

        drawFinder(modules, function, 0, 0)
        drawFinder(modules, function, SIZE - 7, 0)
        drawFinder(modules, function, 0, SIZE - 7)
        drawTiming(modules, function)
        drawAlignment(modules, function, 30, 30)
        drawFormatBits(modules, function, mask = 0)

        val bits = codewords.flatMap { byte ->
            (7 downTo 0).map { bit -> ((byte ushr bit) and 1) != 0 }
        }

        var bitIndex = 0
        var upward = true
        var right = SIZE - 1
        while (right > 0) {
            if (right == 6) right--
            for (i in 0 until SIZE) {
                val y = if (upward) SIZE - 1 - i else i
                for (dx in 0..1) {
                    val x = right - dx
                    if (!function[y][x]) {
                        val bit = bitIndex < bits.size && bits[bitIndex++]
                        val masked = bit xor (((x + y) and 1) == 0)
                        modules[y][x] = if (masked) 1 else 0
                    }
                }
            }
            upward = !upward
            right -= 2
        }

        return Array(SIZE) { y -> BooleanArray(SIZE) { x -> modules[y][x] == 1 } }
    }

    private fun makeDataCodewords(text: String): IntArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= DATA_CODEWORDS - 2) {
            "二维码内容过长，当前内置编码器最多支持 ${DATA_CODEWORDS - 2} 字节"
        }

        val bits = ArrayList<Boolean>(DATA_CODEWORDS * 8)
        bits.append(0b0100, 4)
        bits.append(bytes.size, 8)
        for (byte in bytes) {
            bits.append(byte.toInt() and 0xFF, 8)
        }

        val capacityBits = DATA_CODEWORDS * 8
        bits.append(0, minOf(4, capacityBits - bits.size))
        while (bits.size % 8 != 0) bits.add(false)

        val data = bits.toCodewords().toMutableList()
        var pad = 0
        while (data.size < DATA_CODEWORDS) {
            data.add(if (pad++ % 2 == 0) 0xEC else 0x11)
        }
        return data.toIntArray()
    }

    private fun ArrayList<Boolean>.append(value: Int, bitCount: Int) {
        for (i in bitCount - 1 downTo 0) {
            add(((value ushr i) and 1) != 0)
        }
    }

    private fun List<Boolean>.toCodewords(): List<Int> {
        val result = ArrayList<Int>(size / 8)
        var index = 0
        while (index < size) {
            var value = 0
            for (i in 0 until 8) {
                value = (value shl 1) or if (this[index + i]) 1 else 0
            }
            result.add(value)
            index += 8
        }
        return result
    }

    private fun drawFinder(modules: Array<IntArray>, function: Array<BooleanArray>, left: Int, top: Int) {
        for (dy in -1..7) {
            for (dx in -1..7) {
                val x = left + dx
                val y = top + dy
                if (x !in 0 until SIZE || y !in 0 until SIZE) continue
                val inFinder = dx in 0..6 && dy in 0..6
                val black = inFinder && (
                    dx == 0 || dx == 6 || dy == 0 || dy == 6 ||
                        (dx in 2..4 && dy in 2..4)
                    )
                modules[y][x] = if (black) 1 else 0
                function[y][x] = true
            }
        }
    }

    private fun drawTiming(modules: Array<IntArray>, function: Array<BooleanArray>) {
        for (i in 8 until SIZE - 8) {
            val black = i % 2 == 0
            modules[6][i] = if (black) 1 else 0
            modules[i][6] = if (black) 1 else 0
            function[6][i] = true
            function[i][6] = true
        }
    }

    private fun drawAlignment(modules: Array<IntArray>, function: Array<BooleanArray>, centerX: Int, centerY: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val x = centerX + dx
                val y = centerY + dy
                val black = max(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1
                modules[y][x] = if (black) 1 else 0
                function[y][x] = true
            }
        }
    }

    private fun drawFormatBits(modules: Array<IntArray>, function: Array<BooleanArray>, mask: Int) {
        val bits = getFormatBits(mask)

        fun bit(index: Int): Boolean = ((bits ushr index) and 1) != 0
        fun set(x: Int, y: Int, black: Boolean) {
            modules[y][x] = if (black) 1 else 0
            function[y][x] = true
        }

        for (i in 0..5) set(8, i, bit(i))
        set(8, 7, bit(6))
        set(8, 8, bit(7))
        set(7, 8, bit(8))
        for (i in 9 until 15) set(14 - i, 8, bit(i))

        for (i in 0 until 8) set(SIZE - 1 - i, 8, bit(i))
        for (i in 8 until 15) set(8, SIZE - 15 + i, bit(i))
        set(8, SIZE - 8, true)
    }

    private fun getFormatBits(mask: Int): Int {
        val errorCorrectionLevelLow = 0b01
        val data = (errorCorrectionLevelLow shl 3) or mask
        var remainder = data
        repeat(10) {
            remainder = (remainder shl 1) xor (((remainder ushr 9) and 1) * 0x537)
        }
        return ((data shl 10) or remainder) xor 0x5412
    }

    private object ReedSolomon {
        private val exp = IntArray(512)
        private val log = IntArray(256)

        init {
            var x = 1
            for (i in 0 until 255) {
                exp[i] = x
                log[x] = i
                x = x shl 1
                if ((x and 0x100) != 0) x = x xor 0x11D
            }
            for (i in 255 until exp.size) {
                exp[i] = exp[i - 255]
            }
        }

        fun computeRemainder(data: IntArray, degree: Int): IntArray {
            val divisor = computeDivisor(degree)
            val result = IntArray(degree)
            for (byte in data) {
                val factor = byte xor result[0]
                for (i in 0 until degree - 1) result[i] = result[i + 1]
                result[degree - 1] = 0
                for (i in 0 until degree) {
                    result[i] = result[i] xor multiply(divisor[i], factor)
                }
            }
            return result
        }

        private fun computeDivisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in 0 until degree) {
                    result[j] = multiply(result[j], root)
                    if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
                }
                root = multiply(root, 2)
            }
            return result
        }

        private fun multiply(x: Int, y: Int): Int {
            if (x == 0 || y == 0) return 0
            return exp[log[x] + log[y]]
        }
    }
}
