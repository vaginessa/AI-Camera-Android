#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>

using namespace std;
using namespace cv;

extern "C"
{
    void JNICALL Java_com_example_chin_nativeopencvtest_MainActivity_gray(JNIEnv *env,
                                                                          jobject instance,
                                                                          jlong matAddr) {
        Mat &mat = *(Mat *) matAddr;
        cvtColor(mat, mat, COLOR_BGR2GRAY);
    }
}

void alphaBlendWithMultiplier(cv::Mat &foreground, cv::Mat &background, cv::Mat &alpha, cv::Mat &result, float multiplier)
{
    int nRows = result.rows;
    int nCols = result.cols * result.channels();

    if (result.isContinuous() &&
        foreground.isContinuous() &&
        background.isContinuous() &&
        alpha.isContinuous())
    {
        nCols *= nRows;
        nRows = 1;
    }

    const uchar *fptr;
    const uchar *bptr;
    const float *aptr;
    uchar *rptr;

    for (auto i = 0; i < nRows; ++i)
    {
        fptr = foreground.ptr<uchar>(i);
        bptr = background.ptr<uchar>(i);
        aptr = alpha.ptr<float>(i);
        rptr = result.ptr<uchar>(i);

        auto val = 0.0f;

        for (auto j = 0; j < nCols; ++j, fptr++, bptr++, rptr++)
        {
            if (j % 3 == 0)
            {
                val = *aptr;
                val = min(val * multiplier, 1.0f);
                aptr++;
            }

            *rptr = val * (*fptr) + (1.0f - val) * (*bptr);
        }
    }
}

extern "C"
{
void JNICALL Java_com_example_chin_instancesegmentation_DetectorActivity_process(JNIEnv *env,
                                                                                 jobject instance,
                                                                                 jlong matAddr,
                                                                                 jlong maskAddr,
                                                                                 jlong resultAddr,
                                                                                 jint previewWidth,
                                                                                 jint previewHeight) {

    const int erodeIter = 1;
    const int dilateIter = 1;
    const int elementSize = 40;
    const int erodeElementSize = 50;
    const float multiplier = 4.0f;

    Mat &img = *(Mat *) matAddr;
    Mat &maskImg = *(Mat *) maskAddr;
    Mat &result = *(Mat *) resultAddr;

    // Resize mask to original size;
    maskImg.convertTo(maskImg, CV_8UC1);
    int sideLength = max(previewHeight, previewWidth);
    Size size(sideLength, sideLength);
    resize(maskImg, maskImg, size);
    cv::threshold(maskImg, maskImg, 0, 255, cv::THRESH_BINARY);

    // Crop mask back to the original dimensions.
    auto rect = Rect(0, 0, previewWidth, previewHeight);
    maskImg = maskImg(rect);

    // Find edges.
    auto refinedMask = cv::Mat(img.size(), img.type());
    cv::Canny(img, refinedMask, 50, 100);

    auto structuringElement = cv::getStructuringElement(
            cv::MORPH_RECT,
            cv::Size(elementSize, elementSize));

    auto erodeStructuringElement = cv::getStructuringElement(
            cv::MORPH_RECT,
            cv::Size(erodeElementSize, erodeElementSize));

    // Get sure background.
    auto sureBg = cv::Mat(maskImg.size(), maskImg.type());
    cv::dilate(maskImg, sureBg, structuringElement, cv::Point(-1, -1), dilateIter);

    // Get sure foreground.
    auto sureFg = cv::Mat(maskImg.size(), maskImg.type());
    cv::erode(maskImg, sureFg, erodeStructuringElement, cv::Point(-1, -1), erodeIter);

    refinedMask = refinedMask | sureFg;
    refinedMask = refinedMask & sureBg;

    // Get refined mask by closing.
    cv::morphologyEx(refinedMask, refinedMask, cv::MORPH_CLOSE, structuringElement);

    // Blur the background.
    cv::Mat imgBlur;
    cv::blur(img, imgBlur, cv::Size(20, 20));

    // Do distance transform on the mask to get the amount for alpha blending.
    cv::Mat dist;
    cv::distanceTransform(refinedMask, dist, cv::DIST_L2, 3);
    cv::normalize(dist, dist, 0.0, 1.0, cv::NORM_MINMAX);

    // Alpha blend the foreground onto the blurred image.
    alphaBlendWithMultiplier(img, imgBlur, dist, result, multiplier);
}
}

extern "C"
{
void JNICALL Java_com_example_chin_instancesegmentation_DetectorActivity_bokeh(JNIEnv *env,
                                                                               jobject instance,
                                                                               jlong matAddr,
                                                                               jlong maskAddr,
                                                                               jlong resultAddr,
                                                                               jint pictureWidth,
                                                                               jint pictureHeight) {
    const float multiplier = 20.0f;

    Mat &img = *(Mat *) matAddr;
    Mat &maskImg = *(Mat *) maskAddr;
    Mat &result = *(Mat *) resultAddr;

    // Resize mask to original size;
    maskImg.convertTo(maskImg, CV_8UC1);
    Size size(pictureWidth, pictureHeight);
    resize(maskImg, maskImg, size);
    cv::threshold(maskImg, maskImg, 0, 255, cv::THRESH_BINARY);

    // Blur the background.
    cv::Mat imgBlur;
    cv::blur(img, imgBlur, cv::Size(50, 50));
    cvtColor(imgBlur, imgBlur, COLOR_BGR2GRAY);

    // Do distance transform on the mask to get the amount for alpha blending.
    cv::Mat dist;
    cv::distanceTransform(maskImg, dist, cv::DIST_L2, 3);
    cv::normalize(dist, dist, 0.0, 1.0, cv::NORM_MINMAX);

    alphaBlendWithMultiplier(img, imgBlur, dist, result, multiplier);
}
}
