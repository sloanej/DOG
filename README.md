# Digital Orientation Guide

[View our website here.](https://searri.github.io/project-dog/)

To install our app, install `DOG.apk` using the normal process for installing APK files.

## Android App
The folder `dog-app` should be opened in Android Studio to see the project structure and files.

The "assets" folder contains data files, including the labels for the object detection models as well as the models themselves (`.tflite` files).

The app has three associated packages:
- `org.google.ar.core.examples.java`, containing `CollisionFragment.java` which handles the depth mapping capabilities of the app used in collision detection
- `org.projectdog`, containing most of the app's structure. Most functionality is in `MainActivity.java` and `MyAdapter.java`
- `org.tensorflow.lite.examples.detection`, containing `DetectionFragment.java` which handles the object detection capabilities of the app

Android will handle importing necessary libraries.

## Python Code

We used the Tensorflow 2 Object Detection API to train our sign detection model. We have included some training files from Tensorflow so you don't have to track them down yourself.

### Installation

**Heads up:** At the time of creating this project, the Tensorflow Object Detection API is difficult to install, and its documentation is cluttered between versions for Tensorflow 1 and Tensorflow 2. It is, however, constantly being updated so this may not be the case in the future. The information here might very well be out of date.

1. Install TF2, following [official documentation](https://www.tensorflow.org/install/pip)
    - I recommend using `pip` and Python's native [virtual environment support](https://docs.python.org/3/tutorial/venv.html). Windows can have unexpected problems with Anaconda
    - ***Make sure*** you run Tensorflow's "Verify the install" command before moving on. If Tensorflow didn't install correctly, _now_ is the time to find that out
2. Double-check that the [`tflite_convert` command](https://www.tensorflow.org/lite/convert#command_line_tool_) also installed correctly by running:
```bash
tflite_convert --help
``` 

3. Follow [this guide](https://tensorflow-object-detection-api-tutorial.readthedocs.io/en/latest/install.html#tensorflow-object-detection-api-installation) to install the Object Detection API.
    - This tutorial asks you to create many subdirectories. Feel free to try a different directory structure than theirs, but make sure you can keep track of what each folder does.
    - Again, make sure to run the "Test your Installation" command

If all the test commands produce the expected output, congratulations!

### Usage

1. Find a dataset. We used Mapillary for the street signs, but you'll need a dataset of images that has both labels and bounding box annotations.

2. Write a preprocessor for that dataset. I have included our Mapillary preprocessor (`mapillary_prep_obj.py`) for reference. Your preprocessor needs to be able to read the dataset and output `.record` files (this is a Tensorflow file format) that have the following values for each object:
    - filename
    - xmin
    - ymin
    - xmax
    - ymax
    - class
    - width
    - height

    You also need to output a label map file

    Hopefully our example file provides a structure for how to do this.

3. Once you have `train.record`, `val.record`, and `test.record`, download a model from the [Model Zoo](https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/tf2_detection_zoo.md). Extract the file and configure the `pipeline.config` file to match your data. At the very least, this means changing `num_classes` and `input_path`/`label_map_path` values. You'll probably need to downsize the `batch_size` as well, depending on your system's RAM. Any bugs you hit while training will probably need to be addressed in this file.

4. Run:
```bash
python model_main_tf2.py --model_dir <PATH TO NEW MODEL DIRECTORY> --pipeline_config_path <PATH TO pipeline.config>
```

5. Run:
```bash
python export_tflite_graph_tf2.py --pipeline_config_path <PATH TO pipeline.config> --trained_checkpoint_dir <PATH TO NEW MODEL DIRECTORY> --output_directory <PATH TO NEW TFLITE MODEL DIRECTORY>
```

6. Run ("MODEL NAME" should end with `.tflite`):
```bash
tflite_convert --output_file <MODEL NAME> --saved_model_dir <PATH TO NEW TFLITE MODEL DIRECTORY>/saved_model
```

7. Modify lines 10, 12, 14, 15, 41, and 42 in `tflite_meta_packer.py`. Notably, line 41 should point to the label map file and line 42 should point to "MODEL NAME" from Step 6. Then run:
```
python tflite_meta_packer.py
```

You now have a custom Tensorflow Lite model ready for use in Android.
