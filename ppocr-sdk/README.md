# PaddleOCR Android ONNX SDK

Vendored from [PaddlePaddle/PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR)
`deploy/ppocr-android/ppocr-sdk` (Apache-2.0).

Uses Microsoft `onnxruntime-android` (16 KB page-size aligned) plus OpenCV for
on-device PP-OCR inference.

## Models

Bundled under `src/main/assets/models/`:

- `det/inference.onnx` — PP-OCRv5_mobile detection
- `rec/inference.onnx` + `rec/inference.yml` — PP-OCRv5_mobile recognition

Refresh from:
https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/
