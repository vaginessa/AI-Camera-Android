#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>

extern "C" JNIEXPORT jstring

JNICALL
Java_com_example_chin_nativeopencvtest_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

using namespace std;
using namespace cv;

extern "C"
{
    void JNICALL Java_com_example_chin_nativeopencvtest_MainActivity_salt(JNIEnv *env, jobject instance,
                                                                           jlong matAddrGray,
                                                                           jint nbrElem) {
        Mat &mGr = *(Mat *) matAddrGray;
        for (int k = 0; k < nbrElem; k++) {
            int i = rand() % mGr.cols;
            int j = rand() % mGr.rows;
            mGr.at<uchar>(j, i) = 255;
        }
    }
}

extern "C"
{
    void JNICALL Java_com_example_chin_nativeopencvtest_MainActivity_gray(JNIEnv *env,
                                                                          jobject instance,
                                                                          jlong matAddr) {
        Mat &mat = *(Mat *) matAddr;
        cvtColor(mat, mat, COLOR_BGR2GRAY);
    }
}

void alphaBlend(cv::Mat& foreground, cv::Mat& background, cv::Mat& alpha, cv::Mat& outImage)
{
    const cv::Mat* arrays[] = { &foreground, &background, &alpha, &outImage, NULL };
    cv::Mat planes[4];
    cv::NAryMatIterator it(arrays, planes);

    for (int i = 0; i < it.nplanes; ++i, ++it)
    {
        for (int j = 0; j < it.size; ++j)
        {
            auto fgPixel = it.planes[0].at<cv::Vec3b>(j);
            auto bgPixel = it.planes[1].at<cv::Vec3b>(j);
            auto alpha = it.planes[2].at<float>(j);
            for (int k = 0; k < 3; ++k)
            {
                auto value = alpha * fgPixel[k] + (1.0 - alpha) * bgPixel[k];
                it.planes[3].at<cv::Vec3b>(j)[k] = value;
            }
        }
    }
}

void alphaBlend2(cv::Mat& foreground, cv::Mat& background, cv::Mat& alpha, cv::Mat& outImage)
{
	for (int i = 0; i < outImage.rows; ++i)
	{
		for (int j = 0; j < outImage.cols; ++j)
		{
			auto fgPixel = foreground.at<cv::Vec3b>(i, j);
			auto bgPixel = background.at<cv::Vec3b>(i, j);
			auto alphaVal = 1.0; //alpha.at<float>(i, j);

			for (int k = 0; k < outImage.channels(); ++k)
			{
				auto value = alphaVal * fgPixel[k] + (1.0 - alphaVal) * bgPixel[k];
				outImage.at<cv::Vec3b>(i, j)[k] = value;
			}
		}
	}
}

void testLoop(Mat &img, Mat &result)
{
    for (int i = 0; i < img.rows; ++i)
    {
        for (int j = 0; j < img.cols; ++j)
        {
            auto p = img.data + i * img.step[0] + j * img.step[1];
            auto avg = (p[0] + p[1] + p[2]) / 3;
            auto res = result.data + i * result.step[0] + j * result.step[1];

            res[0] = avg;
            res[1] = avg;
            res[2] = avg;
        }
    }

    /*
    for (int i = 0; i < img.rows; ++i)
    {
        auto row = img.ptr<Vec3b>(i);
        auto resultRow = result.ptr<Vec3b>(i);
        for (int j = 0; j < img.cols; ++j)
        {
            auto avg = (row[j][0] + row[j][1] + row[j][2]) / 3;
            resultRow[j][0] = avg;
            resultRow[j][1] = avg;
            resultRow[j][2] = avg;
        }
    }
     */
}

void alphaBlend3(cv::Mat &foreground, cv::Mat &background, cv::Mat &alpha, cv::Mat &result)
{
	for (int i = 0; i < result.rows; ++i)
	{
		for (int j = 0; j < result.cols; ++j)
		{
			auto fgPixel = foreground.data + i * foreground.step[0] + j * foreground.step[1];
			auto bgPixel = background.data + i * background.step[0] + j * background.step[1];
			auto alphaPtr = reinterpret_cast<float*>(alpha.data + i * alpha.step[0] + j * alpha.step[1]);
			auto resultPixel = result.data + i * result.step[0] + j * result.step[1];

			auto val = (float)*alphaPtr;

			//std::cout << val << " ";


			for (int k = 0; k < result.channels(); ++k)
			{
				resultPixel[k] = val * fgPixel[k] + (1.0 - val) * bgPixel[k];
			}

		}
	}
}

extern "C"
{
void JNICALL Java_com_example_chin_nativeopencvtest_MainActivity_process2(JNIEnv *env,
                                                                           jobject instance,
                                                                           jlong matAddr,
                                                                           jlong maskAddr,
                                                                           jlong resultAddr) {

    Mat &img = *(Mat *) matAddr;
    Mat &result = *(Mat *) resultAddr;
    testLoop(img, result);
}
}

extern "C"
{
void JNICALL Java_com_example_chin_instancesegmentation_DetectorActivity_process(JNIEnv *env,
                                                                         jobject instance,
                                                                         jlong matAddr,
                                                                         jlong maskAddr,
                                                                         jlong resultAddr) {
    Mat &img = *(Mat *) matAddr;
    Mat &maskImg = *(Mat *) maskAddr;
    Mat &result = *(Mat *) resultAddr;

    // The mask needs to be grayscale.
    cv::cvtColor(maskImg, maskImg, cv::COLOR_BGR2GRAY);
    cv::threshold(maskImg, maskImg, 100, 255, cv::THRESH_BINARY);

    // Do edge detection.
    auto canny = cv::Mat(img.size(), img.type());
    cv::Canny(img, canny, 150, 200);

    // Dilate to get sure background.
    auto structuringElement = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(50, 50));
    auto sureBg = cv::Mat(maskImg.size(), maskImg.type());
    cv::dilate(maskImg, sureBg, structuringElement, cv::Point(-1, -1), 2);

    // Remove lines in sure background but keep lines in sure foreground.
    canny = canny | maskImg; // Replace maskImg with sure foreground.
    canny = canny & sureBg;

    // Get refined mask by closing.
    cv::morphologyEx(canny, canny, cv::MORPH_CLOSE, structuringElement);

    // Apply blur to original image.
    cv::Mat imgCopy;
    cv::GaussianBlur(img, imgCopy, cv::Size(5, 3), 50.0);

    // Do distance transform on the mask to get the amount for alpha blending.
    cv::Mat dist;
    cv::distanceTransform(canny, dist, cv::DIST_L2, 3);
    cv::normalize(dist, dist, 0.0, 1.0, cv::NORM_MINMAX);

    // Alpha blend the foreground onto the blurred image.

    // These two are for testing.
    auto bg = cv::Mat(img.size(), img.type(), cv::Scalar::all(0));
    //dist = cv::Mat(img.size(), CV_32FC1, cv::Scalar::all(0.5));

    alphaBlend3(img, bg, dist, result);
}
}
