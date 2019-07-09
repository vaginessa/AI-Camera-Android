#AiCamera

This is a camera app for Android that automatically recognises the pixel location of people, cats or dogs in the camera view and blurs the background simulating depth of field. It can also gray out the background leaving only the recognised object in colour.

You can download the app on F-Droid [here](https://f-droid.org/en/packages/com.lun.chin.aicamera/)

![get it on f-droid](https://bitbucket.org/chlun/aicamera/raw/master/images/get-it-on-small.png)

or 

download the apk from Google Drive [here](https://drive.google.com/open?id=1s7sMea67O64RhznGaFsp-yFYjXS3GURv).

The app uses a convolutional neural network to perform the detection. A TensorFlow implementation of the neural network can be found [here](https://bitbucket.org/chlun/shufflesegmentation/src/master/). It was trained on a dataset of images consisting of people, cats and dogs and the trained model then exported as a protobuf file which contains the graph definition and the trained weights. The loading and inference of this exported model is done using [Android TensorFlow support](https://github.com/tensorflow/tensorflow/tree/master/tensorflow/contrib/android).

The various image manipulations are done using the [OpenCV for Android SDK](https://opencv.org/android/).
