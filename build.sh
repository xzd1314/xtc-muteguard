#!/bin/bash
# MuteGuard 构建脚本（无 gradle，直接 aapt2 + javac + d8 + apksigner）
set -e

BT="${ANDROID_BUILD_TOOLS:-D:/openclaw/工具/buildtools/android-14}"
AAPT2="$BT/aapt2.exe"
ANDROID_JAR="${ANDROID_JAR_PATH:-D:/openclaw/工具/buildtools/android-8.1.0/android.jar}"
JDK="${JAVA_HOME:-D:/openclaw/jdk/jdk-17.0.20.1+1}"
JAVAC="$JDK/bin/javac.exe"
JAVA="$JDK/bin/java.exe"
KEYTOOL="$JDK/bin/keytool.exe"
D8_JAR="$BT/lib/d8.jar"
APKSIGNER_JAR="$BT/lib/apksigner.jar"
ZIPALIGN="$BT/zipalign.exe"
PY="${PYTHON:-python}"

cd "$(dirname "$0")"

KS="keystore.jks"
KS_PASS="muteguard2026"
KS_ALIAS="muteguard"

echo "==> [1/7] 清理"
rm -rf build
mkdir -p build/gen build/classes build/dex

echo "==> [2/7] aapt2 compile"
"$AAPT2" compile --dir res -o build/res.zip

echo "==> [3/7] aapt2 link"
"$AAPT2" link \
  -o build/base.apk \
  -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  -R build/res.zip \
  -A assets \
  --java build/gen \
  --min-sdk-version 19 \
  --target-sdk-version 27 \
  --auto-add-overlay

echo "==> [4/7] javac"
# 4.1 先编译 Xposed API 桩到独立目录（关键：桩绝不能进 dex，
#     否则模块类实现的接口会解析到桩而非框架，isAssignableFrom 失败导致模块被跳过）
mkdir -p build/stub-classes
find stub -name "*.java" > build/stub-sources.txt
"$JAVAC" -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d build/stub-classes @build/stub-sources.txt

# 4.2 编译业务代码，桩只作为 classpath
find src build/gen -name "*.java" > build/sources.txt
"$JAVAC" -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$ANDROID_JAR;build/stub-classes" \
  -d build/classes @build/sources.txt

echo "==> [5/7] d8"
find build/classes -name "*.class" > build/classes.txt
"$JAVA" -cp "$D8_JAR" com.android.tools.r8.D8 \
  --release --lib "$ANDROID_JAR" --lib build/stub-classes --min-api 19 \
  --output build/dex @build/classes.txt

echo "==> [6/7] 合并 dex 到 apk"
"$PY" pack.py build/base.apk build/dex/classes.dex build/unsigned.apk

echo "==> [7/7] zipalign + 签名"
if [ ! -f "$KS" ]; then
  "$KEYTOOL" -genkeypair -keystore "$KS" -alias "$KS_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=MuteGuard, OU=xzd, O=xzd, L=Beijing, ST=Beijing, C=CN" >/dev/null 2>&1
fi

"$ZIPALIGN" -f 4 build/unsigned.apk build/aligned.apk
"$JAVA" -jar "$APKSIGNER_JAR" sign \
  --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out MuteGuard.apk build/aligned.apk

ls -lh MuteGuard.apk
echo "==> done"
