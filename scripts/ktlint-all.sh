#!/usr/bin/env bash
set -e

for proj in omoide-memory-uploader omoide-memory-migration omoide-memory-jooq omoide-memory-downloader; do
  if [ -d "$proj" ]; then
    cp .editorconfig "$proj"/.editorconfig
    (cd "$proj" && ./gradlew ktlintFormat)
  fi
done
