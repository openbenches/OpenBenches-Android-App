# PaddleOCR Android ONNX SDK (`:ppocr-sdk`)

Vendored from [PaddlePaddle/PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR)
`deploy/ppocr-android/ppocr-sdk` (Apache-2.0).

Used by the OpenBenches app for **on-device inscription OCR** when adding a bench:
OpenCV preprocesses the photo, then this module runs PP-OCR inference.

## Stack

- Microsoft `onnxruntime-android` (16 KB page-size aligned, Play-compatible)
- OpenCV (linked from the app module for preprocessing)
- PP-OCRv5 mobile detection + recognition models

## Models

Bundled under `src/main/assets/models/`:

| Path | Role |
|------|------|
| `det/inference.onnx` | PP-OCRv5_mobile text detection |
| `rec/inference.onnx` + `rec/inference.yml` | PP-OCRv5_mobile recognition |

Refresh models from the official PaddleX / PaddleOCR release channels, e.g.:

https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/

## App integration

The `:app` module depends on this project:

```kotlin
implementation(project(":ppocr-sdk"))
```

Kotlin wrappers live in `app` under `org.openbenches.util` (`InscriptionOcr`, `InscriptionOcrPreprocessor`).
OCR is warmed when **Add Bench** opens (not on every cold start).

## Licence

Apache License 2.0 — see upstream PaddleOCR.
