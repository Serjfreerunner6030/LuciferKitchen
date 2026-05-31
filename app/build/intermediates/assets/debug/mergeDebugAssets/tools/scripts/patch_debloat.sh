#!/system/bin/sh
# patch_debloat.sh — Remove MIUI/HyperOS bloatware
# Args: $1=project_dir

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT="$1"

[ -z "$PROJECT" ] && echo "ERROR: No project dir" && exit 1
[ ! -d "$PROJECT" ] && echo "ERROR: Project dir not found" && exit 1

echo "INFO: Debloating MIUI/HyperOS ROM..."

BLOAT_LIST="
Analytics
AntHalService
BaiduIME
Browser
Calculator
Calendar
CatchLog
CleanMaster
CloudService
Email
Facebook
GameCenter
Games
Gmail
GoogleMaps
GooglePhotos
GooglePlay
Health
HybridAccessory
Joyose
KSafety
MAnalytics
MSA
MiAd
MiBrowserGlobal
MiCloudSync
MiCreditStub
MiDrop
MiFitnessStub
MiGalleryLockscreen
MiLinkService
MiPay
MiPlayClient
MiRadio
MiRecycle
MiService
MiShare
MiShop
MiTrustService
MiVideo
MiVideoGlobal
MiWallpaper
MiuiBugReport
MiuiCompass
MiuiDaemon
MiuiGallery
MiuiScanner
MiuiScreenRecorder
MiuiSuperMarket
MiuiUpdater
MiuiVideo
MiuiVoice
MiuiWeather
MiuiYellowPage
Music
Notes
PaymentService
PersonalAssistant
Podcast
QQBrowser
SMSExtra
SoHotService
SogouInput
SoterService
Stk
ThirdAppAssistant
TouchAssistant
UserGuide
VoiceAssist
VoiceTrigger
WMService
Weather
XMCloudEngine
XMSFKeeper
XiaomiAccount
XiaomiServiceFramework
Yandex
YellowPage
com.tencent.soter.soterserver
"

REMOVED=0
for part in system product system_ext data-app; do
    PART_DIR="$PROJECT/$part"
    [ ! -d "$PART_DIR" ] && continue
    for app in $BLOAT_LIST; do
        [ -z "$app" ] && continue
        for subdir in app priv-app; do
            APP_DIR="$PART_DIR/$subdir/$app"
            if [ -d "$APP_DIR" ]; then
                echo "INFO: Removing $part/$subdir/$app"
                rm -rf "$APP_DIR"
                REMOVED=$((REMOVED + 1))
            fi
        done
    done
done

echo "INFO: Debloat complete. Removed $REMOVED apps."
exit 0
