## PaddleOCR 使用说明

- 首次运行时需要运行 gradle task downloadAndExtractArchives 下载PaddleLite和OpenCV4.5.5的包: 
- 在根目录执行 `./gradlew downloadAndExtractArchives`
- 运行完毕后会下载OpenCV的安卓SDK包以及Paddle-Lite的包 其中PaddleLite的目录结构如下
- ```log
  |-- PaddleLite
  |  |-- cxx
  |  |  |-- include
  |  |  |-- libs
  |  |  |  |-- arm64-v8a
  |  |  |  |-- armeapi-v7a
  |  |-- java
  |  |  |-- libs
  |  |  |  |-- arm64-v8a
  |  |  |  |-- armeapi-v7a
  ```
- 官方的v3模型放置在 `assets/models/` 目录下，后续更新模型可以进行替换