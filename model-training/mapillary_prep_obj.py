# Mapillary preprocessor for object detection

import os
import glob
import pandas as pd
import io
import json
import tensorflow.compat.v1 as tf
from PIL import Image
from object_detection.utils import dataset_util, label_map_util
from collections import namedtuple


def make_df():

    with open("ignore.txt", "r") as igfile:
        t = igfile.readlines()
        ignore_these_classes = set([x[:-1] for x in t])

    # Input folders
    indirs = [
        os.getenv("HOME") + "/Downloads/mtsd_fully_annotated",
        os.getenv("HOME") + "/Downloads/mtsd_partially_annotated",
    ]
    mapillary_parent_dir = os.getenv("HOME") + "/Downloads/mtsd_fully_annotated"

    # Loop through annotations
    train_list = []
    val_list = []
    test_list = []
    used_labels = set()
    ignored_labels = set()

    for mapillary_parent_dir in indirs:
        # Read files containing image keys
        try:
            with open(mapillary_parent_dir + "/splits/train.txt", "r") as train_in:
                train_keys = set(train_in.read().splitlines())

            with open(mapillary_parent_dir + "/splits/test.txt", "r") as test_in:
                test_keys = set(test_in.read().splitlines())

            with open(mapillary_parent_dir + "/splits/val.txt", "r") as val_in:
                val_keys = set(val_in.read().splitlines())
        except FileNotFoundError:
            with open(mapillary_parent_dir + "/splits/all.txt", "r") as train_in:
                train_keys = set(train_in.read().splitlines())

        for f in os.listdir(mapillary_parent_dir + "/annotations"):
            if f.endswith(".json"):
                with open(mapillary_parent_dir + "/annotations/" + f, "r") as infile:
                    img_coords_dict = json.load(infile)

                for item in img_coords_dict["objects"]:
                    if item["label"] in ignore_these_classes:
                        ignored_labels.add(item["label"])
                        continue

                    curr_obj = {
                        "filename": mapillary_parent_dir + "/images/" + f[:-4] + "jpg"
                    }
                    curr_obj["xmin"] = item["bbox"]["xmin"]
                    curr_obj["ymin"] = item["bbox"]["ymin"]
                    curr_obj["xmax"] = item["bbox"]["xmax"]
                    curr_obj["ymax"] = item["bbox"]["ymax"]
                    curr_obj["class"] = item["label"]
                    curr_obj["width"] = curr_obj["xmax"] - curr_obj["xmin"]
                    curr_obj["height"] = curr_obj["ymax"] - curr_obj["ymin"]

                    if f[:-5] in train_keys:
                        train_list.append(curr_obj)
                    elif f[:-5] in val_keys:
                        val_list.append(curr_obj)
                    elif f[:-5] in test_keys:
                        test_list.append(curr_obj)
                    else:
                        print("File lacks set label:", f)
                        continue

                    used_labels.add(item["label"])

            else:
                print("Unknown file discovered:", f)

    train_df = pd.DataFrame(train_list)
    val_df = pd.DataFrame(val_list)
    test_df = pd.DataFrame(test_list)
    print(ignore_these_classes - ignored_labels)
    create_label_map(list(used_labels))

    return train_df, val_df, test_df


def create_label_map(categories):
    output_string = ""
    end = "\n"
    s = " "
    class_map = {}
    for ID, name in enumerate(categories):
        out = ""
        out += "item" + s + "{" + end
        out += s * 2 + "id:" + " " + (str(ID + 1)) + end
        out += s * 2 + "name:" + " " + "'" + name + "'" + end
        out += "}" + end * 2
        class_map[name] = ID + 1
        output_string += out

    with open("label_map.pbtxt", "w") as f:
        f.write(output_string)

    with open("sign_labels.txt", "w") as outfile:
        for label in categories:
            outfile.write(label)
            outfile.write("\n")


def split(df, group):
    data = namedtuple("data", ["filename", "object"])
    gb = df.groupby(group)
    return [
        data(filename, gb.get_group(x))
        for filename, x in zip(gb.groups.keys(), gb.groups)
    ]


def create_tf_example(group, path, label_map_dict):
    # with tf.gfile.GFile(os.path.join(path, "{}".format(group.filename)), "rb") as fid:
    with tf.gfile.GFile(group.filename, "rb") as fid:
        encoded_jpg = fid.read()
    encoded_jpg_io = io.BytesIO(encoded_jpg)
    image = Image.open(encoded_jpg_io)
    width, height = image.size

    filename = group.filename.encode("utf8")
    image_format = b"jpg"
    xmins = []
    xmaxs = []
    ymins = []
    ymaxs = []
    classes_text = []
    classes = []

    for index, row in group.object.iterrows():
        xmins.append(row["xmin"] / width)
        xmaxs.append(row["xmax"] / width)
        ymins.append(row["ymin"] / height)
        ymaxs.append(row["ymax"] / height)
        classes_text.append(row["class"].encode("utf8"))
        classes.append(label_map_dict[row["class"]])

    tf_example = tf.train.Example(
        features=tf.train.Features(
            feature={
                "image/height": dataset_util.int64_feature(height),
                "image/width": dataset_util.int64_feature(width),
                "image/filename": dataset_util.bytes_feature(filename),
                "image/source_id": dataset_util.bytes_feature(filename),
                "image/encoded": dataset_util.bytes_feature(encoded_jpg),
                "image/format": dataset_util.bytes_feature(image_format),
                "image/object/bbox/xmin": dataset_util.float_list_feature(xmins),
                "image/object/bbox/xmax": dataset_util.float_list_feature(xmaxs),
                "image/object/bbox/ymin": dataset_util.float_list_feature(ymins),
                "image/object/bbox/ymax": dataset_util.float_list_feature(ymaxs),
                "image/object/class/text": dataset_util.bytes_list_feature(
                    classes_text
                ),
                "image/object/class/label": dataset_util.int64_list_feature(classes),
            }
        )
    )
    return tf_example


def main(_):
    train_df, val_df, test_df = make_df()
    outputs = {"train.record": train_df, "val.record": val_df, "test.record": test_df}
    label_map = label_map_util.load_labelmap("label_map.pbtxt")
    label_map_dict = label_map_util.get_label_map_dict(label_map)
    for subset in outputs:
        if len(outputs[subset]) <= 0:
            continue
        writer = tf.python_io.TFRecordWriter(subset)
        grouped = split(outputs[subset], "filename")
        for group in grouped:
            tf_example = create_tf_example(
                group,
                os.getenv("HOME") + "/Downloads/mtsd_fully_annotated/images",
                label_map_dict,
            )
            writer.write(tf_example.SerializeToString())
        writer.close()


if __name__ == "__main__":
    tf.app.run()
