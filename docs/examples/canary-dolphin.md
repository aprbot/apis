# Распознавание речи Canary и документов Dolphin

Для обоих примеров задайте `AIMOS_HOST` равным `https://dreamtoken.ru` или `https://aimos.dreamagent.ru`. Экспортируйте ключи выбранного сервиса в `CANARY_API_KEY` и `DOLPHIN_API_KEY`. Команды запускаются из корня репозитория с Python 3.10+, requests и pytest.

## NVIDIA Canary

Пример отправляет WAV или OGG как multipart-файл в `POST /v1/audio/transcriptions` с моделью `nvidia/canary-1b-v2` и языком записи. JSON содержит расшифровку в `text`, длительность — в `usage.seconds`.

```bash
python3 python/aprbot/aimos/canary_example.py speech.wav --host "$AIMOS_HOST" --language ru
```

Для перевода записи на английский добавьте `--translate`. Пример передаст тот же файл и язык источника в `POST /v1/audio/translations`.

```bash
python3 python/aprbot/aimos/canary_example.py speech.ogg --host "$AIMOS_HOST" --language ru --translate
```

## Dolphin OCR

Пример загружает документ в `aprbot/dolphin`, отправляет его на распознавание и опрашивает статус. Для справки КС-3 укажите идентификатор типа документа `rus.reference.ks3.type1`.

```bash
OCR_KEY="$DOLPHIN_API_KEY" python3 python/aprbot/dreamocr/ocr_example.py \
  --host "$AIMOS_HOST/api/v2/models/aprbot/dolphin/dreamocr" \
  -i ks3.pdf -o output -p '{"doctype":"rus.reference.ks3.type1"}'
```

## Проверка через рабочий API

Укажите фразу из записи, её перевод и известное значение поля из документа. Проверка отправляет оба файла в выбранный сервис, вызывает модели и скачивает JSON завершённого задания Dolphin.

```bash
export CANARY_AUDIO=speech.wav CANARY_LANGUAGE=ru CANARY_EXPECTED_TEXT='электроника'
export CANARY_EXPECTED_TRANSLATION='electronics'
export DOLPHIN_DOCUMENT=ks3.pdf DOLPHIN_DOCTYPE=rus.reference.ks3.type1
export DOLPHIN_EXPECTED_TEXT='Вектор'
python3 -m pytest -q python/tests/check_published_models.py
```

Чтобы проверить тарифы, экспортируйте JWT обычного пользователя кабинета в `AIMOS_CABINET_TOKEN`. Проверка читает каталог и сверяет цены всех трёх видов расхода и доступность выпуска ключа.

```bash
python3 -m pytest -q python/tests/check_catalog_prices.py
```
