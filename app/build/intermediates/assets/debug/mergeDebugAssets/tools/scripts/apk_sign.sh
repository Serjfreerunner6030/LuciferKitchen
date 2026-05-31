#!/system/bin/sh
# Lucifer Kitchen - Sign APK
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK_PATH="$1"

[ -z "$APK_PATH" ] && echo "ERROR: Usage: apk_sign.sh <apk_path>" && exit 1
[ ! -f "$APK_PATH" ] && echo "ERROR: APK not found: $APK_PATH" && exit 1

UBER_SIGNER="$TOOLS_DIR/bin/uber-apk-signer.jar"
KEYSTORE="$TOOLS_DIR/lucifer.keystore"
KEYSTORE_PASS="lucifer123"
KEY_ALIAS="lucifer"

echo "INFO: Signing $APK_PATH"

# Generate keystore if not exists
if [ ! -f "$KEYSTORE" ]; then
    echo "INFO: Generating signing keystore..."
    keytool -genkeypair \
        -alias "$KEY_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -keystore "$KEYSTORE" \
        -storepass "$KEYSTORE_PASS" \
        -keypass "$KEYSTORE_PASS" \
        -dname "CN=Lucifer Kitchen, OU=ROM, O=Lucifer, L=Unknown, ST=Unknown, C=US" \
        2>&1
    [ $? -ne 0 ] && echo "ERROR: Failed to generate keystore" && exit 1
    echo "INFO: Keystore generated"
fi

# Try uber-apk-signer first
if [ -f "$UBER_SIGNER" ]; then
    echo "INFO: Using uber-apk-signer..."
    java -jar "$UBER_SIGNER" -a "$APK_PATH" --ks "$KEYSTORE" --ksPass "$KEYSTORE_PASS" --ksAlias "$KEY_ALIAS" --ksKeyPass "$KEYSTORE_PASS" 2>&1
    EXIT_CODE=$?
else
    # Fallback to apksigner
    APKSIGNER="$TOOLS_DIR/bin/apksigner.jar"
    if [ -f "$APKSIGNER" ]; then
        echo "INFO: Using apksigner..."
        SIGNED_APK="${APK_PATH%.apk}-signed.apk"
        java -jar "$APKSIGNER" sign \
            --ks "$KEYSTORE" \
            --ks-pass "pass:$KEYSTORE_PASS" \
            --ks-key-alias "$KEY_ALIAS" \
            --key-pass "pass:$KEYSTORE_PASS" \
            --out "$SIGNED_APK" \
            "$APK_PATH" 2>&1
        EXIT_CODE=$?
        if [ $EXIT_CODE -eq 0 ]; then
            mv "$SIGNED_APK" "$APK_PATH"
        fi
    else
        # Last resort: jarsigner
        echo "INFO: Using jarsigner..."
        jarsigner -verbose \
            -keystore "$KEYSTORE" \
            -storepass "$KEYSTORE_PASS" \
            -keypass "$KEYSTORE_PASS" \
            -signedjar "$APK_PATH" \
            "$APK_PATH" "$KEY_ALIAS" 2>&1
        EXIT_CODE=$?
    fi
fi

if [ $EXIT_CODE -eq 0 ]; then
    echo "INFO: APK signed successfully"
else
    echo "ERROR: Signing failed (exit code: $EXIT_CODE)"
fi
exit $EXIT_CODE
