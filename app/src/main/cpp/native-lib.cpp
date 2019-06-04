#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>

using namespace std;
using namespace cv;

float kernelCircle[27][27] =
{
	{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
	{ 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0 },
	{ 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0 },
	{ 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0 },
	{ 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0 },
	{ 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0 },
	{ 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0 },
	{ 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0 },
	{ 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0 },
	{ 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
	{ 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
	{ 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
	{ 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
	{ 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
	{ 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
	{ 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
	{ 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
	{ 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
	{ 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0 },
	{ 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0 },
	{ 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0 },
	{ 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0 },
	{ 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0 },
	{ 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0 },
	{ 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0 },
	{ 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0 },
	{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 }
};

const float ZCircle = 549;

void circleBlur(Mat &src, Mat &dst)
{
    auto size = 27;
    auto kernel = Mat(size, size, CV_32FC1, kernelCircle) / ZCircle;
    filter2D(src, dst, -1, kernel);
}

void alphaBlendWithMultiplier(cv::Mat &foreground, cv::Mat &background, cv::Mat &alpha, cv::Mat &result, float multiplier) {
    const uchar *fptr;
    const uchar *bptr;
    const float *aptr;
    uchar *rptr;
    float val;

    auto cols = result.cols;
    auto rows = result.rows;
    auto channels = result.channels();

    auto fstep0 = foreground.step[0];
    auto fstep1 = foreground.step[1];
    auto bstep0 = background.step[0];
    auto bstep1 = background.step[1];
    auto astep0 = alpha.step[0];
    auto astep1 = alpha.step[1];
    auto rstep0 = result.step[0];
    auto rstep1 = result.step[1];

    for (auto i = 0; i < rows; ++i)
    {
        fptr = foreground.data + i * fstep0;
        bptr = background.data + i * bstep0;
        rptr = result.data + i * rstep0;

        for (auto j = 0; j < cols; ++j)
        {
            aptr = reinterpret_cast<float*>(alpha.data + i * astep0 + j * astep1);
            val = min((*aptr) * multiplier, 1.0f);

            rptr[0] = val * fptr[0] + (1.0 - val) * bptr[0];
            rptr[1] = val * fptr[1] + (1.0 - val) * bptr[1];
            rptr[2] = val * fptr[2] + (1.0 - val) * bptr[2];
            if (channels == 4)
            {
                rptr[3] = val * fptr[3] + (1.0 - val) * bptr[3];
            }

            fptr += fstep1;
            bptr += bstep1;
            rptr += rstep1;
        }
    }
}

extern "C"
{
void JNICALL Java_com_lun_chin_aicamera_env_ImageUtils_bokeh(JNIEnv *env,
                                                             jobject instance,
                                                             jlong matAddr,
                                                             jlong maskAddr,
                                                             jlong resultAddr,
                                                             jint pictureWidth,
                                                             jint pictureHeight,
                                                             jint blurAmount,
                                                             jboolean grayscale) {
    const float multiplier = 13.0f;

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

    // The circleBlur function is slow. Use the built in blur for now.
    //circleBlur(img, imgBlur);
    blurAmount = blurAmount % 2 == 0 ? blurAmount + 1 : blurAmount; // Needs to be an odd number.
    blur(img, imgBlur, cv::Size(blurAmount, blurAmount));

    if (grayscale) {
        cvtColor(imgBlur, imgBlur, COLOR_BGR2GRAY);
    }

    // Do distance transform on the mask to get the amount for alpha blending.
    cv::Mat dist;
    cv::distanceTransform(maskImg, dist, cv::DIST_L2, 3);
    cv::normalize(dist, dist, 0.0, 1.0, cv::NORM_MINMAX);

    alphaBlendWithMultiplier(img, imgBlur, dist, result, multiplier);
}
}
