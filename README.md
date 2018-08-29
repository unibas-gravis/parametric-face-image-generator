parametric-face-image-generator(extension by arneli00)
==========================================================

This software is based on the parametric-face-image-generator of the gravis group. The [original Software](https://github.com/unibas-gravis/parametric-face-image-generator) enables you to generate fully parametric face images from the Basel Face Model 2017 as proposed in:

- [1] Adam Kortylewski, Andreas Schneider, Thomas Gerig, Bernhard Egger, Andreas Morel-Forster and Thomas Vetter 
["Training Deep Face Recognition Systems with Synthetic Data"](https://arxiv.org/abs/1802.05891), 
IN: arXiv preprint (2018)


- [2] Adam Kortylewski, Bernhard Egger, Andreas Schneider, Thomas Gerig, Andreas Morel-Forster and Thomas Vetter 
["Empirically Analyzing the Effect of Dataset Biases on Deep Face Recognition Systems"](https://arxiv.org/abs/1712.01619), 
IN: arXiv preprint (2017)

You can control the variation of parameters such as pose, shape, color, camera and illumination based on your demand and application.
This dataset can be used for training and comparing machine learning techniques such as CNNs on a common ground as proposed in [1] by generating fully controlled training and test data.

SETUP
-------------------
1. Add both versions of the Basel Face Model to the `data/bfm2017` folder.
2. Add some background images to the `data/backgrounds` folder.
3. Add the Basel Illumination Prior to the `data/bip` folder.

WHAT'S NEW
----------
In the file 'example_config_controlled' located in data/config_files/example_config_controlled.json, you can specify an occlusion mode (third last row in the file). You can choose between:

### eyes
This mode retrieves the location of the eyes from the provided .tlms file and renders black circles over both eyes on the face.

### random-1
A hand hides the picture on a random place in a random orientation.

### random-2
A microphone hides the picture on a random place in a random orientation.

### random
This mode renders real-world occlusions in the image. They are in a random position, randomly scaled and randomly positioned.

### box
A rectangle filled with an arbitrary color hides the image.

### box-skinColor
Boxes filled with the color at the tip of the chin hide the image.

### box-whitnoise
Creates random rectangles on the face, filled with Gaussian white noise.

### box-\[0-100\]
Iteratively creates a box column-by-column that occludes the specified amount of pace pixels. It fills the rectangle with a random color.

### loop
Creates 20 images for each angle. Each with a different amount of occluded face region. Starting from 2%, it goes up to 40% by steps of 2%.

### texture
Fills a randomly placed box with a texture image.

OUTPUT
------

The software provides csv-files, rps-files, tlms-files, ground truth masks, images with occlusions and images without occlusions for both the `bfm` and the `face12` version of the Basel Face Model. If an occlusion gets rendered over a landmark, it gets disabled.
 
Contributors
------------

- Bernhard Egger
- Adam Kortylewski
- Andreas Morel-Forster
- Andreas Schneider

Maintainers
-----------

- University of Basel, Graphics and Vision research: [@unibas-gravis](https://github.com/unibas-gravis), [homepage](http://gravis.cs.unibas.ch)


License
-------

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0), details see LICENSE

    Copyright 2017, University of Basel, Graphics and Vision Research

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

