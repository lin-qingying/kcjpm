package org.cangnova.kcjpm.dependency

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.cangnova.kcjpm.test.BaseTest
import java.io.OutputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.readText

class DependencyHttpClientTest : BaseTest() {
    init {
        test("DefaultDependencyHttpClient 应该解压带 PAX path 的 tar.gz") {
            val tempDir = createTempDir("http-client")
            val archive = tempDir.resolve("package.tar.gz")
            val targetDir = tempDir.resolve("target")

            writeTarGzWithPaxPath(
                archive = archive,
                path = "package-root/config/cjpm.toml",
                content = "[package]\nname = \"demo\"\n"
            )

            DefaultDependencyHttpClient().extractArchive(archive, targetDir).getOrThrow()

            targetDir.resolve("package-root/config/cjpm.toml").readText() shouldBe "[package]\nname = \"demo\"\n"
        }
    }

    private fun writeTarGzWithPaxPath(
        archive: java.nio.file.Path,
        path: String,
        content: String
    ) {
        GZIPOutputStream(java.nio.file.Files.newOutputStream(archive)).use { output ->
            val paxContent = paxRecord("path", path).toByteArray(Charsets.UTF_8)
            output.writeTarHeader("PaxHeaders/package", paxContent.size.toLong(), 'x')
            output.writePadded(paxContent)

            val fileContent = content.toByteArray(Charsets.UTF_8)
            output.writeTarHeader("short-name", fileContent.size.toLong(), '0')
            output.writePadded(fileContent)

            output.write(ByteArray(1024))
        }
    }

    private fun paxRecord(key: String, value: String): String {
        val payload = "$key=$value\n"
        var length = payload.toByteArray(Charsets.UTF_8).size + 2
        while (true) {
            val record = "$length $payload"
            val actualLength = record.toByteArray(Charsets.UTF_8).size
            if (actualLength == length) {
                return record
            }
            length = actualLength
        }
    }

    private fun OutputStream.writeTarHeader(name: String, size: Long, type: Char) {
        val header = ByteArray(512)
        header.writeAscii(0, 100, name)
        header.writeAscii(100, 8, "0000777")
        header.writeAscii(108, 8, "0000000")
        header.writeAscii(116, 8, "0000000")
        header.writeAscii(124, 12, size.toString(8).padStart(11, '0'))
        header.writeAscii(136, 12, "00000000000")
        for (index in 148 until 156) {
            header[index] = ' '.code.toByte()
        }
        header[156] = type.code.toByte()
        header.writeAscii(257, 6, "ustar")
        header.writeAscii(263, 2, "00")

        val checksum = header.sumOf { it.toUByte().toInt() }
        header.writeAscii(148, 8, checksum.toString(8).padStart(6, '0') + "\u0000 ")

        write(header)
    }

    private fun OutputStream.writePadded(bytes: ByteArray) {
        write(bytes)
        val padding = (512 - (bytes.size % 512)) % 512
        if (padding > 0) {
            write(ByteArray(padding))
        }
    }

    private fun ByteArray.writeAscii(offset: Int, length: Int, value: String) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        val bytesToCopy = minOf(bytes.size, length)
        bytes.copyInto(this, offset, 0, bytesToCopy)
    }
}
