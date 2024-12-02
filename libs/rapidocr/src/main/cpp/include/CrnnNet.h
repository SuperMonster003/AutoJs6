#ifndef __OCR_CRNNNET_H__
#define __OCR_CRNNNET_H__

#include "OcrStruct.h"
#include "onnxruntime/core/session/onnxruntime_cxx_api.h"
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

class CrnnNet {
public:

    CrnnNet();

    ~CrnnNet();

    void setNumThread(int numOfThread);

    void initModel(AAssetManager *mgr, const std::string &name, const std::string &keysName);

    std::vector<TextLine> getTextLines(std::vector<cv::Mat> &partImg);

private:
    Ort::Session *session;
    Ort::Env ortEnv = Ort::Env(ORT_LOGGING_LEVEL_ERROR, "CrnnNet");
    Ort::SessionOptions sessionOptions = Ort::SessionOptions();
    int numThread = 0;

    std::vector<Ort::AllocatedStringPtr> inputNamesPtr;
    std::vector<Ort::AllocatedStringPtr> outputNamesPtr;

    const float meanValues[3] = {127.5, 127.5, 127.5};
    const float normValues[3] = {1.0 / 127.5, 1.0 / 127.5, 1.0 / 127.5};
    const int dstHeight = 48;

    std::vector<std::string> keys;

    TextLine scoreToTextLine(const std::vector<float> &outputData, int h, int w);

    TextLine getTextLine(cv::Mat &src);
};


#endif //__OCR_CRNNNET_H__
