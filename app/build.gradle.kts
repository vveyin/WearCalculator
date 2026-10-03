import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.nickwoluff.wearcalculator"
    // 科学模式只用到框架自带 API，compileSdk 34 足够；
    // 用 34 是为了兼容 AGP 8.5.x + Gradle 8.7 这套构建环境。
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nickwoluff.wearcalculator"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "1.1.3fix-cy"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    // --------------------------------

    // release 签名：口令与路径放在项目根目录的 keystore.properties 里
    // （该文件已被 .gitignore 忽略，不会进版本库）。
    // 文件不存在时也能正常构建，只是产出的 release 包未签名。
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 开启代码压缩/混淆与资源裁剪
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 界面全部用框架原生控件（没有 material / constraintlayout），
    // 所以依赖保持最小：appcompat + activity + wear（CurvedTextView 顶部时间用）。
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation("androidx.wear:wear:1.3.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

// androidx.wear:wear:1.3.0 要求 kotlin-stdlib 1.8.22，而 appcompat 1.6.1 会拖来
// kotlin-stdlib-jdk7/jdk8 1.6.21，两者有重复类（kotlin.collections.jdk8 等），
// 不统一就会卡在 checkDebugDuplicateClasses。这里把整个 kotlin 标准库拉平到 1.8.22。
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22")
    }
}