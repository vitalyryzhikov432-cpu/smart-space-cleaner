#!/usr/bin/env bash
# Раскладывает на подключённом эмуляторе набор файлов для ручной проверки Smart Space Cleaner.
# Запуск из корня репозитория: bash scripts/push-test-data.sh
set -euo pipefail
export MSYS_NO_PATHCONV=1   # иначе Git Bash превращает /sdcard/... в пути Windows

R=/sdcard
# Локальную папку передаём adb как путь Windows: перевод путей выше отключён.
TMP=$(cygpath -m "$(mktemp -d)")
trap 'rm -rf "$TMP"' EXIT

head -c 300000 /dev/urandom > "$TMP/photo.jpg"
head -c 200000 /dev/urandom > "$TMP/work.bin"
head -c 50000 /dev/urandom > "$TMP/shot.png"
head -c 80000 /dev/urandom > "$TMP/tg.jpg"
head -c 120000000 /dev/urandom > "$TMP/big.mp4"
head -c 4000 /dev/urandom > "$TMP/unknown.apk"

# adb shell склеивает аргументы в одну строку, поэтому пути с пробелами — во внутренних кавычках
adb shell mkdir -p "$R/DCIM/Camera" "$R/redmi6" "$R/Download" "$R/DCIM/Screenshots" \
  "$R/Documents/project-a" "$R/Documents/project-b" "'$R/Telegram/Telegram Images'" "$R/Movies"

# Уровень 1: дубли (оригинал в Camera) и дубли в «рабочих» папках
adb push "$TMP/photo.jpg" "$R/DCIM/Camera/IMG_0001.jpg"
adb push "$TMP/photo.jpg" "$R/redmi6/IMG_0001.jpg"
adb push "$TMP/photo.jpg" "$R/Download/IMG_0001(1).jpg"
adb push "$TMP/work.bin" "$R/Documents/project-a/data.bin"
adb push "$TMP/work.bin" "$R/Documents/project-b/data.bin"

# Уровень 1: установщик уже установленного приложения (копия собственного APK)
APK=$(adb shell pm path com.smartspacecleaner | sed 's/package://' | tr -d '\r')
adb shell cp "$APK" "$R/Download/smart-space-cleaner.apk"

# Уровень 2: неизвестный установщик, старый скриншот, старое медиа Telegram, крупное видео
adb push "$TMP/unknown.apk" "$R/Download/unknown.apk"
adb push "$TMP/shot.png" "$R/DCIM/Screenshots/Screenshot_old.png"
adb shell touch -m -d 2025-01-01T12:00:00 "$R/DCIM/Screenshots/Screenshot_old.png"
adb push "$TMP/tg.jpg" "$R/Telegram/Telegram Images/old.jpg"
adb shell touch -m -d 2025-06-01T12:00:00 "'$R/Telegram/Telegram Images/old.jpg'"
adb push "$TMP/big.mp4" "$R/Movies/big.mp4"

echo "Готово. Ожидаемые группы описаны в docs/superpowers/plans/2026-10-23-smart-space-cleaner-core.md, Task 18."
