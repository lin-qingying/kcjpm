package org.cangnova.kcjpm.sdk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.cangnova.kcjpm.test.BaseTest
import java.nio.file.Paths

class SdkManagerTest : BaseTest() {
    init {
        test("应该从 CANGJIE_HOME 环境变量创建 SDK") {
            val cangjieHome = System.getenv("CANGJIE_HOME")
            
            if (cangjieHome == null) {
                println("跳过测试: CANGJIE_HOME 环境变量未设置")
                return@test
            }
            
            val result = SdkManager.default()
            
            result.isSuccess shouldBe true
            val sdk = result.getOrThrow()
            sdk.sdkHome shouldBe Paths.get(cangjieHome)
            sdk.compilerPath.toFile().exists() shouldBe true
        }
        
        test("应该验证 SDK 有效性") {
            val cangjieHome = System.getenv("CANGJIE_HOME")
            
            if (cangjieHome == null) {
                println("跳过测试: CANGJIE_HOME 环境变量未设置")
                return@test
            }
            
            val sdk = SdkManager.default().getOrThrow()
            
            val validationResult = sdk.validate()
            validationResult.isSuccess shouldBe true
        }
        
        test("应该拒绝无效的 SDK 路径") {
            val invalidPath = createTempDir().resolve("nonexistent")
            
            val result = SdkManager.fromPath(invalidPath)
            
            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldContain "未找到编译器"
        }
        
        test("应该能够获取编译器命令") {
            val cangjieHome = System.getenv("CANGJIE_HOME")
            
            if (cangjieHome == null) {
                println("跳过测试: CANGJIE_HOME 环境变量未设置")
                return@test
            }
            
            val sdk = SdkManager.default().getOrThrow()
            
            val command = sdk.getCompilerCommand()
            command shouldContain "cjc"
        }
        
        test("应该能够检测 SDK 版本") {
            val cangjieHome = System.getenv("CANGJIE_HOME")
            
            if (cangjieHome == null) {
                println("跳过测试: CANGJIE_HOME 环境变量未设置")
                return@test
            }
            
            val sdk = SdkManager.default().getOrThrow()
            
            val version = sdk.detectVersion()
            println("检测到的版本: $version")
            version shouldNotBe null
        }
        
        test("应该支持从系统 PATH 查找 SDK") {
            val result = SdkManager.fromSystemPath()
            
            if (result.isSuccess) {
                val sdk = result.getOrThrow()
                println("从 PATH 找到 SDK: ${sdk.sdkHome}")
                sdk.compilerPath.toFile().exists() shouldBe true
            } else {
                println("跳过测试: 系统 PATH 中未找到 cjc")
            }
        }
        
        test("应该支持 SDK 配置") {
            val cangjieHome = System.getenv("CANGJIE_HOME")
            
            if (cangjieHome == null) {
                println("跳过测试: CANGJIE_HOME 环境变量未设置")
                return@test
            }
            
            val config = SdkConfig(sdkHomePath = cangjieHome)
            val result = config.createSdk()
            
            result.isSuccess shouldBe true
            val sdk = result.getOrThrow()
            sdk.sdkHome shouldBe Paths.get(cangjieHome)
        }
        
        test("默认配置应该使用 CANGJIE_HOME") {
            val cangjieHome = System.getenv("CANGJIE_HOME")
            
            if (cangjieHome == null) {
                println("跳过测试: CANGJIE_HOME 环境变量未设置")
                return@test
            }
            
            val config = SdkConfig()
            val result = config.createSdk()
            
            result.isSuccess shouldBe true
        }
        
        test("应该能创建带版本信息的 SDK") {
            val cangjieHome = System.getenv("CANGJIE_HOME")
            
            if (cangjieHome == null) {
                println("跳过测试: CANGJIE_HOME 环境变量未设置")
                return@test
            }
            
            val sdk = SdkManager.default().getOrThrow()
            
            sdk.version shouldNotBe null
            println("SDK 版本: ${sdk.version}")
        }
    }
}