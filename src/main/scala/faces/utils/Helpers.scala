/*
 * Copyright University of Basel, Graphics and Vision Research Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package faces.utils

import java.io.{File, FileOutputStream, OutputStream, PrintWriter}
import java.net.URI

import breeze.linalg.DenseVector
import faces.settings.FacesSettings
import scalismo.faces.color.RGBA
import scalismo.faces.image.PixelImage
import scalismo.faces.io.{MoMoIO, PixelImageIO, RenderParameterIO, TLMSLandmarksIO}
import scalismo.faces.landmarks.TLMSLandmark2D
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters._
import scalismo.faces.sampling.face.ModalityRenderers.{AlbedoRenderer, IlluminationVisualizationRenderer}
import scalismo.faces.sampling.face.{CorrespondenceColorImageRenderer, CorrespondenceMoMoRenderer, MoMoRenderer}
import scalismo.faces.utils.ResourceManagement
import scalismo.geometry.{Point, Point2D, Vector2D, Vector3D}
import scalismo.utils.Random

import scala.util.matching.Regex
import scala.reflect.io.Path
import scala.util.Try

case class Helpers(cfg: FacesSettings)(implicit rnd: Random) {

  import cfg._
  import backgrounds._
  import imageDimensions._
  import morphableModelParameters._
  import outputLocation._

  //****************************************************************************
  // HELPERS
  //****************************************************************************


    // generates outPath if not existing
    if (!Path(outImgPath).exists) {
      Path(outImgPath).createDirectory(failIfExists = false)
    }
    if (!Path(outRpsPath).exists) {
      Path(outRpsPath).createDirectory(failIfExists = false)
    }
    if (!Path(outCSVPath).exists) {
      Path(outCSVPath).createDirectory(failIfExists = false)
    }
    if (!Path(outTLMSPath).exists) {
      Path(outTLMSPath).createDirectory(failIfExists = false)
    }


  // loading model and choosing neutral part if expressions not used
  val model: MoMo = {
    val momo = MoMoIO.read(new File(modelFn)).get
    if (expressions) momo else momo.neutralModel
  }

  // the renderer for the model
  val renderer  = CorrespondenceMoMoRenderer(model, RGBA.BlackTransparent).cached(5)

  val renderingMethods = {
    val depthMapGenerator = new DepthMapRendererRGBA(renderer)
    val colorCorrespondenceGen = new CorrespondenceColorImageRenderer(renderer)
    val normalsGenerator = new NormalsRendererRGBA(renderer, RGBA.BlackTransparent)
    val albedoGenerator = new AlbedoRenderer(renderer, RGBA.BlackTransparent)
    val illuminationGenerator = new IlluminationVisualizationRenderer(renderer, RGBA.BlackTransparent)
    //additional rendering methods
    val dm = if(cfg.renderingMethods.renderDepthMap) {
      Some(("_depth", depthMapGenerator))
    }else None
    val cm = if(cfg.renderingMethods.renderColorCorrespondenceImage) {
      Some(("_correspondence", colorCorrespondenceGen))
    }else None
    val nm = if(cfg.renderingMethods.renderNormals) {
      Some(("_normals", normalsGenerator))
    }else None
    val am = if(cfg.renderingMethods.renderAlbedo) {
      Some(("_albedo", albedoGenerator))
    }else None
    val im = if(cfg.renderingMethods.renderIllumination) {
      Some(("_illumination", illuminationGenerator))
    }else None
    val rn = if(cfg.renderingMethods.render) {
      Some(("", renderer))
    }else None
    IndexedSeq(rn, dm, cm, nm, am, im).flatten
  }

  // generates a random instance of a Morphable Model following Gaussian distributions
  def rndMoMoInstance: MoMoInstance = {
    if (expressions)
      MoMoInstance(
        if (nShape == 0) IndexedSeq(0.0) else IndexedSeq.fill(nShape)(rnd.scalaRandom.nextGaussian()),
        if (nColor == 0) IndexedSeq(0.0) else IndexedSeq.fill(nColor)(rnd.scalaRandom.nextGaussian()),
        if (nExpression == 0) IndexedSeq(0.0) else IndexedSeq.fill(nExpression)(rnd.scalaRandom.nextGaussian()),
        new URI(""))
    else
      MoMoInstance(
        if (nShape == 0) IndexedSeq(0.0) else IndexedSeq.fill(nShape)(rnd.scalaRandom.nextGaussian()),
        if (nColor == 0) IndexedSeq(0.0) else IndexedSeq.fill(nColor)(rnd.scalaRandom.nextGaussian()),
        if (nExpression == 0) IndexedSeq(0.0) else IndexedSeq.fill(nExpression)(0.0),
        new URI(""))
  }


  // adds a random expression to a neutral morphable model instance
  def rndExpressions(id: MoMoInstance): MoMoInstance = {
    id.copy(expression = IndexedSeq.fill(nExpression)(rnd.scalaRandom.nextGaussian()))
  }

  lazy val loadBgs: IndexedSeq[File] = {
    // search all background files
    new File(bgPath).listFiles.filter(_.getName.endsWith(bgType)).toIndexedSeq
  }

  def writeLandmarks(rps: RenderParameter, file: File, mask:PixelImage[RGBA]): Try[Unit] = {
    require(!cfg.landmarkTags.isEmpty,"Landmark tags list can not be empty when writing landmarks to a file.")
    val lms = visibilityForLandmarks(renderer, rps, cfg.landmarkTags.map(tag => renderer.renderLandmark(tag, rps).get).toIndexedSeq)
    writeTLMSLandmarks(lms, file, mask)
  }

  // center the face around a point between the ears and nose (change here if you want to center another point)
  def centerLandmark(rps: RenderParameter): RenderParameter = {
    val lmLeftEar: TLMSLandmark2D = renderer.renderLandmark("left.eye.pupil.center", rps).get
    val lmRightEar: TLMSLandmark2D = renderer.renderLandmark("right.eye.pupil.center", rps).get
    val lmNose: TLMSLandmark2D = renderer.renderLandmark("center.nose.tip", rps).get


    val middleOfFace = (lmLeftEar.point + lmRightEar.point.toVector + lmNose.point.toVector).toVector / 3
    val centerOfImage = Point2D(imageWidth / 2, imageHeight / 2)
    val shift = centerOfImage - middleOfFace

    // shifting by moving the principal point (normalized device coordinates [-1, 1], y axis upwards)
    rps.copy(camera = rps.camera.copy(principalPoint = Point2D(
      rps.camera.principalPoint.x + 2 * shift.x / rps.imageSize.width,
      rps.camera.principalPoint.y - 2 * shift.y / rps.imageSize.height))
    )
  }

  /** checks visibility for landmarks by rastering the image with the CorrespondenceMoMoRenderer.
    * Make sure, that you are using a cached CorrespondenceMoMoRenderer */
  def visibilityForLandmarks(renderer: CorrespondenceMoMoRenderer, param: RenderParameter, landmarks: IndexedSeq[TLMSLandmark2D]): IndexedSeq[TLMSLandmark2D] = {
    val correspondenceImage = renderer.renderCorrespondenceImage(param)
    val w = param.imageSize.width
    val h = param.imageSize.height

    def visibilityForLandmark(lm: TLMSLandmark2D): Boolean = {
      val eps = 2.0
      val pt2d = lm.point
      val x = math.round(pt2d.x).toInt
      val y = math.round(pt2d.y).toInt
      if(x >=0 && x < w && y >=0 && y < h) { // within image bounds
        val maybeFrag = correspondenceImage(x, y)
        if (maybeFrag.isDefined) {
          val frag = maybeFrag.get
          val modelView = param.modelViewTransform
          val pos3dOnMesh = modelView(frag.mesh.position(frag.triangleId, frag.worldBCC))

          val ptId = renderer.model.landmarkPointId(lm.id).get
          val lm3d = modelView(renderer.model.instanceAtPoint(param.momo.coefficients, ptId)._1)

          if (lm3d.z + eps >= pos3dOnMesh.z) {
            true
          } else {
            false
          }
        } else {
          false
        }
      }else {
        false
      }
    }


    for(lm <- landmarks) yield {
      lm.copy(visible = visibilityForLandmark(lm))
    }
  }

  /*Find the 3D center point between the left and the right nosewing and transform it to 2D*/

  private def findCenterWithNoseWings(rps: RenderParameter) : Option[Point2D] = {

    for {
      leftNoseWingId <- model.landmarkPointId("left.nose.wing.tip")
      rightNoseWingId <- model.landmarkPointId("right.nose.wing.tip")
      leftNoseWing3D <- Some(model.instanceAtPoint(rps.momo.coefficients,leftNoseWingId)._1)
      rightNoseWing3D <- Some(model.instanceAtPoint(rps.momo.coefficients,rightNoseWingId)._1)
      centerPoint3D <- Some(leftNoseWing3D + (rightNoseWing3D - leftNoseWing3D) * 0.5)
      pt <- Some(rps.renderTransform(centerPoint3D))
    } yield Point(pt.x,pt.y)

  }

  // center the face around chin, eyes, nose and ears and crop a face box (change here if you want to center another point)
  def centerFaceBox(rps: RenderParameter, scaling : Double = 1): RenderParameter = {

    val lmTags = Seq(
      "left.ear.helix.outer",
      "right.ear.helix.outer",
      "center.nose.tip",
      "center.front.trichion",
      "center.chin.tip",
      "left.eyebrow.bend.lower",
      "right.eyebrow.bend.lower"
     )

    val lms = lmTags.map(tag => tag -> renderer.renderLandmark(tag, rps).get ).toMap


    val xmax = lms.values.map( _.point.x ).max
    val xmin = lms.values.map( _.point.x ).min

    val ymax = lms.values.map( _.point.y ).max
    val ymin = lms.values.map( _.point.y ).min

    val maxSide = math.max(xmax-xmin, ymax-ymin) * scaling

    val middleOfFace = findCenterWithNoseWings(rps).get

    val centerOfImage = Point2D(imageWidth / 2, imageHeight / 2)

    val shift = middleOfFace - centerOfImage

    val f = maxSide.toDouble/rps.imageSize.width.toDouble
    val g = maxSide.toDouble/rps.imageSize.height.toDouble

    // shifting by moving the principal point (normalized device coordinates [-1, 1], y axis upwards)
    rps.copy(camera = rps.camera.copy(principalPoint = Point2D(
      (rps.camera.principalPoint.x - 2 * shift.x / rps.imageSize.width)/f,
      (rps.camera.principalPoint.y + 2 * shift.y / rps.imageSize.height)/g),
      sensorSize = Vector2D(rps.camera.sensorSize.x * f, rps.camera.sensorSize.y * g))
    )

  }

  // method to write csv file of parameters
  def writeCSV(rps: RenderParameter, file: File): Unit = {

    val vPose: DenseVector[Double] = DenseVector(
      rps.pose.yaw,
      rps.pose.pitch,
      rps.pose.roll,
      rps.pose.translation.x,
      rps.pose.translation.y,
      rps.pose.translation.z
    )
    val vCamera: DenseVector[Double] = DenseVector(
      rps.camera.principalPoint.x,
      rps.camera.principalPoint.y,
      rps.camera.focalLength,
      rps.camera.sensorSize.x,
      rps.camera.sensorSize.y)
    val vMoMo: DenseVector[Double] = DenseVector((rps.momo.shape ++ rps.momo.color ++ rps.momo.expression).toArray)
    val vLight: DenseVector[Double] = rps.environmentMap.toBreezeVector

    val vParam = DenseVector.vertcat(vPose, vCamera, vMoMo, vLight)

    breeze.linalg.csvwrite(file, vParam.toDenseMatrix)
  }

  // method to read csv file and generate a RenderParameter from it to render an image again
  def readCSV(file: File): RenderParameter = {
    val vec = breeze.linalg.csvread(file).toDenseVector
    val init = RenderParameter.default.fitToImageSize(imageWidth, imageHeight)
    val withPose = init.withPose(pose = init.pose.copy(yaw = vec(0), pitch = vec(1), roll = vec(2), translation = Vector3D(vec(3), vec(4), vec(5))))
    val withCam = withPose.withCamera(camera = init.camera.copy(principalPoint = Point2D(vec(6), vec(7)), focalLength = vec(8), sensorSize = Vector2D(vec(9), vec(10))))
    val withMoMo = withCam.withMoMo(momo = MoMoInstance(vec(11 until 11 + nShape).toArray.toIndexedSeq,
      vec(11 + nShape until 11 + nShape + nColor).toArray.toIndexedSeq,
      vec(11 + nShape + nColor until 11 + nShape + nColor + nExpression).toArray.toIndexedSeq,
      new URI("")))
    val withLight = withMoMo.withEnvironmentMap(SphericalHarmonicsLight.fromBreezeVector(vec(11 + nShape + nColor + nExpression until 11 + nShape + nColor + nExpression + 27)))
    withLight
  }

  lazy val loadTextures: IndexedSeq[File] = {
    // search all texture files
    new File(inTexturesPath).listFiles.filter(_.getName.endsWith(".png")).toIndexedSeq
  }

  def writeImg(img: PixelImage[RGBA], id: Int, n: Int, postfix: String, mask: PixelImage[RGBA]): PixelImage[RGBA] = {



    val outImgPathID = outImgPath + id + "/"
    if (!Path(outImgPathID).exists) {
      Path(outImgPathID).createDirectory(failIfExists = false)
    }
    PixelImageIO.write(img.map { f => f.toRGB }, new File(outImgPathID + id + "_" + n + postfix + ".png"))
    var occlusion_mask = mask

    if(cfg.occlusion.renderOcclusion==true && postfix.equals("")) {
      val outOccPathID = outOccPath + id + "/"
      if (!Path(outOccPathID).exists) {
        Path(outOccPathID).createDirectory(failIfExists = false)
      }

      val outMaskPathID = outMaskPath + id + "/"
      if (!Path(outMaskPathID).exists) {
        Path(outMaskPathID).createDirectory(failIfExists = false)
      }

      // New 'occlusionMode'
      if (cfg.occlusion.occlusionMode.equals("eyes")) {
        val img_array = img.toArray
        val eyeCenters = getEyeCenters(id, n)
        val (new_image, new_mask) = drawGlasses(img, mask, eyeCenters)
        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + postfix + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + postfix + ".png"))
        PixelImageIO.write(img.map { f => f.toRGB }, new File(outImgPathID + id + "_" + n + postfix + ".png"))
        occlusion_mask = new_mask
      }

      // New 'occlusionMode'
      else if (cfg.occlusion.occlusionMode.startsWith("random-")) {
        val numPattern = "[0-9]+".r
        val match1 = numPattern.findFirstIn(cfg.occlusion.occlusionMode)
        val magic = getRandImage(match1.get.toInt)
        val occlusion_File_path = inOccPath + magic._4
        var occlusion_image = flipImage(PixelImageIO.read[RGBA](new File(occlusion_File_path)).get, (magic._1, magic._2, magic._3))
        val resize = Math.min(0.8, rnd.scalaRandom.nextDouble() + 0.2)
        occlusion_image = occlusion_image.resampleNearestNeighbour((resize * occlusion_image.width).toInt, (resize * occlusion_image.height).toInt)
        val position = (rnd.scalaRandom.nextInt(img.width - occlusion_image.width),rnd.scalaRandom.nextInt(img.width - occlusion_image.width))
        val (new_image, new_mask) = writeOcclusionToImages(img, mask, occlusion_image, position)
        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + postfix + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + postfix + ".png"))
        occlusion_mask = new_mask
      }

      // New 'occlusionMode'
      else if (cfg.occlusion.occlusionMode.equals("box-whiteNoise")) {
        val (new_image, new_mask) = gaussian_white_noise(img, mask, true)
        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + postfix + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + postfix + ".png"))
        occlusion_mask = new_mask
      }

      // New 'occlusionMode'
      else if (cfg.occlusion.occlusionMode.equals("box-skincolor")) {
        val color_skin: RGBA = get_color_at(img, "center.chin.tip", id, n)
        val (new_image, new_mask, color) = fillBox(img, mask, getBoxDimensions(mask, rnd.scalaRandom.nextInt(30) + 20), color_skin + RGBA.apply(color_skin.r + rnd.scalaRandom.nextInt(10) / 100, color_skin.g + rnd.scalaRandom.nextInt(10) / 100, color_skin.b + rnd.scalaRandom.nextInt(10) / 100))
        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + postfix + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + postfix + ".png"))
        occlusion_mask = new_mask
      }

      // New 'occlusionMode'
      else if (cfg.occlusion.occlusionMode.startsWith("box")) {
        var percentage = rnd.scalaRandom.nextInt(40) + 10 // You can choose this number
        if (cfg.occlusion.occlusionMode.startsWith("box-")) {
          val numPattern = "[0-9]+".r
          val match1 = numPattern.findFirstIn(cfg.occlusion.occlusionMode)
          percentage = match1.get.toInt
        }
        val dims = getBoxDimensions(mask, percentage)
        val (new_image, new_mask, color) = fillBox(img, mask, dims)
        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + postfix + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + postfix + ".png"))
        occlusion_mask = new_mask
      }


      // New 'occlusionMode'
      else if (cfg.occlusion.occlusionMode.equals("texture")){
        val percentage = rnd.scalaRandom.nextInt(40) + 10 // You can choose this number
        val dims = getBoxDimensions(mask, percentage)

        val rndTexture = loadTextures(rnd.scalaRandom.nextInt(loadTextures.length))
        val texture_image = PixelImageIO.read[RGBA](rndTexture).get.resample(imageWidth, imageHeight)

        val (new_image, new_mask) = fillBox_texture(img, mask, dims, texture_image)

        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + postfix + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + postfix + ".png"))
        occlusion_mask = new_mask
      }
    }
    return occlusion_mask
  }

  // (start_x,start_y,width,height)
  def fillBox(img: PixelImage[RGBA], mask: PixelImage[RGBA], positions: (Int, Int, Int, Int), color_input: RGBA*): (PixelImage[RGBA], PixelImage[RGBA], RGBA) = {
    var color: RGBA = null

    if (color_input.size != 0)
      color = color_input(0)
    else
      color = RGBA.apply(rnd.scalaRandom.nextDouble(), rnd.scalaRandom.nextDouble(), rnd.scalaRandom.nextDouble())


    val return_Image = PixelImage(img.width, img.height, (x, y) => {
      if (x >= positions._1 && x < positions._1 + positions._3 && y >= positions._2 && y < positions._2 + positions._4) {
        color
      } else {
        img(x, y)
      }
    })
    val return_Mask = PixelImage(mask.width, mask.height, (x, y) => {
      if (x >= positions._1 && x < positions._1 + positions._3 && y >= positions._2 && y < positions._2 + positions._4)
        RGBA.apply(0, 0, 0, 0) // Change the color of the occlusion on the GROTRU mask
      else
        mask(x, y)
    })
    (return_Image, return_Mask, color)
  }

  def fillBox_texture(img: PixelImage[RGBA], mask: PixelImage[RGBA], positions: (Int, Int, Int, Int), texture: PixelImage[RGBA]) = {
    val return_Image = PixelImage(img.width, img.height, (x, y) => {
      if (x >= positions._1 && x < positions._1 + positions._3 && y >= positions._2 && y < positions._2 + positions._4) {
        texture(x,y)
      } else {
        img(x, y)
      }
    })
    val return_Mask = PixelImage(mask.width, mask.height, (x, y) => {
      if (x >= positions._1 && x < positions._1 + positions._3 && y >= positions._2 && y < positions._2 + positions._4)
        RGBA.apply(0, 0, 0, 0)  // Change the color of the occlusion on the GROTRU mask
      else
        mask(x, y)
    })
    (return_Image, return_Mask)
  }

  def gaussian_white_noise(image: PixelImage[RGBA], mask: PixelImage[RGBA], whiteNoiseOnly: Boolean, positions: (Int, Int, Int, Int)*): (PixelImage[RGBA], PixelImage[RGBA]) = {
    var from_x = rnd.scalaRandom.nextInt(image.width - 50)
    var from_y = rnd.scalaRandom.nextInt(image.height - 50)
    var box_width = Math.min(image.width - from_x - 10, rnd.scalaRandom.nextInt(image.width / 3))
    var box_height = Math.min(image.height - from_y - 10, rnd.scalaRandom.nextInt(image.height / 3))
    if (positions.length > 0) {
      from_x = positions(0)._1
      from_y = positions(0)._2
      box_width = positions(0)._3
      box_height = positions(0)._4
    }
    val color = RGBA.apply(rnd.scalaRandom.nextDouble(), rnd.scalaRandom.nextDouble(), rnd.scalaRandom.nextDouble())
    var whiteNoise = whiteNoiseOnly

    if (!whiteNoiseOnly)
      whiteNoise = rnd.scalaRandom.nextBoolean()


    val return_Image = PixelImage(image.width, image.height, (x, y) => {
      if (x >= from_x && x < from_x + box_width && y >= from_y && y < from_y + box_height) {
        if (whiteNoise)
          RGBA.apply(Math.abs(rnd.scalaRandom.nextGaussian()), Math.abs(rnd.scalaRandom.nextGaussian()), Math.abs(rnd.scalaRandom.nextGaussian()))
        else
          color
      } else {
        image(x, y)
      }
    })
    val return_Mask = PixelImage(mask.width, mask.height, (x, y) => {
      if (x >= from_x && x < from_x + box_width && y >= from_y && y < from_y + box_height)
        RGBA.Black
      else
        mask(x, y)
    })
    (return_Image, return_Mask)
  }

  def get_color_at(img: PixelImage[RGBA], LMtag: String, id: Int, n: Int): RGBA = {
    val outTLMSPathID = outTLMSPath + id + "/"
    if (!Path(outTLMSPathID).exists) {
      Path(outTLMSPathID).createDirectory(failIfExists = false)
    }
    val File_Path = outTLMSPathID + id + "_" + n + ".tlms"
    val f = scala.io.Source.fromFile(File_Path)
    var color = RGBA.Black
    for (line <- f.getLines()) {
      if (line.startsWith(LMtag)) {
        val pattern = new Regex("\\d+[.]\\d+")
        val right_eye_result = pattern.findAllIn(line).toIndexedSeq
        if (right_eye_result.length == 2) {
          color = img.apply(Math.floor(right_eye_result(0).toDouble).toInt, Math.floor(right_eye_result(1).toDouble).toInt)
        }
      }
    }
    color
  }

  def getBoxDimensions(mask: PixelImage[RGBA], percentage: Int): (Int, Int, Int, Int) = {
    var ok = false
    var start_x = 0
    var start_y = 0
    var width = 0
    var height = 0
    val face_pixels = countFacePixels(mask)
    val target_pixels = face_pixels * (percentage.toDouble / 100)
    var current_pixels = 0
    while (!ok) {
      start_x = rnd.scalaRandom.nextInt(cfg.imageDimensions.imageWidth - 2) + 1
      start_y = rnd.scalaRandom.nextInt(cfg.imageDimensions.imageHeight - 2) + 1
      height = rnd.scalaRandom.nextInt(cfg.imageDimensions.imageHeight - 1 - start_y)
      current_pixels = 0
      var current_x = start_x
      while (current_pixels < target_pixels && current_x < mask.width - 1) {
        current_pixels += countFacePixelsInCol(mask, current_x, height)
        current_x += 1
      }
      width = current_x - start_x
      if (current_pixels >= target_pixels)
        ok = true
    }
    (start_x, start_y, width, height)
  }

  def countFacePixelsInCol(mask: PixelImage[RGBA], col: Int, height: Int): Int = {
    var counter = 0
    for (i <- 0 to height) {
      if (mask.apply(col, i) == RGBA.White) {
        counter += 1
      }
    }
    counter
  }

  def countFacePixels(img: PixelImage[RGBA]): Long = {
    var counter = 0
    for (i <- 0 to img.width - 1) {
      for (j <- 0 to img.height - 1) {
        if (img(i, j) == RGBA.White)
          counter += 1
      }
    }
    counter
  }


  def writeOcclusionToImages(image: PixelImage[RGBA], mask: PixelImage[RGBA], occlusion: PixelImage[RGBA], start: (Int, Int)): (PixelImage[RGBA], PixelImage[RGBA]) = {
    val return_Image = PixelImage(image.width, image.height, (x, y) => {
      if (x >= start._1 && x < start._1 + occlusion.width && y >= start._2 && y < start._2 + occlusion.height) {
        val occlusion_rgba = occlusion(x - start._1, y - start._2)
        if (occlusion_rgba.a > 0.5)
          occlusion(x - start._1, y - start._2)
        else
          image(x, y)
      }
      else
        image(x, y)
    })
    val return_Mask = PixelImage(mask.width, mask.height, (x, y) => {
      if (x >= start._1 && x < start._1 + occlusion.width && y >= start._2 && y < start._2 + occlusion.height) {
        val occlusion_rgba = occlusion(x - start._1, y - start._2)
        if (occlusion_rgba.a > 0.5)
          RGBA.Black
        else
          mask(x, y)
      }
      else
        mask(x, y)
    })
    (return_Image, return_Mask)
  }

  def flipImage(image: PixelImage[RGBA], flip: (Boolean, Boolean, Boolean)): PixelImage[RGBA] = {
    if (flip._3)
      PixelImage(image.height, image.width, (x, y) => {
        var x_new = x
        var y_new = y
        if (flip._1)
          x_new = image.height - 1 - x
        if (flip._2)
          y_new = image.width - 1 - y
        image(y_new, x_new)
      })
    else
      PixelImage(image.width, image.height, (x, y) => {
        var x_new = x
        var y_new = y
        if (flip._1)
          x_new = image.width - 1 - x
        if (flip._2)
          y_new = image.height - 1 - y
        image(x_new, y_new)
      })
  }

  def getRandImage(onlyHands: Int): (Boolean, Boolean, Boolean, String, Int) = {
    val A = Array(
      "hand_dark.png",
      "hand_dark_2.png",
      "hand_dark_3.png",
      "hand_white.png",
      "hand_white_1.png",
      "hand_white_2.png",
      "hand_white_3.png",
      "hand_white_4.png",
      "hand_white_5.png",
      "hand_white_6.png",
      "micro_1.png",
      "micro_2.png",
      "micro_3.png")
    val index_hand_images = 0 to 9
    val index_microphone_images = 10 to A.size - 1
    if (onlyHands == 1) {
      val image_index = rnd.scalaRandom.nextInt(index_hand_images.size)
      return (rnd.scalaRandom.nextBoolean(), rnd.scalaRandom.nextBoolean(), rnd.scalaRandom.nextBoolean(), A(index_hand_images(image_index)),image_index)
    }
    else if (onlyHands == 2) {
      val image_index = rnd.scalaRandom.nextInt(index_microphone_images.size)
      return (rnd.scalaRandom.nextBoolean(), rnd.scalaRandom.nextBoolean(), rnd.scalaRandom.nextBoolean(), A(index_microphone_images(image_index)), image_index)
    }
    else {
      val image_index = rnd.scalaRandom.nextInt(A.size)
      return (rnd.scalaRandom.nextBoolean(), rnd.scalaRandom.nextBoolean(), rnd.scalaRandom.nextBoolean(), A(image_index), image_index)
    }
  }

  def getEyeCenters(id: Int, n: Int): Array[Int] = {
    val positions = new Array[Int](4)
    val outTLMSPathID = outTLMSPath + id + "/"
    if (!Path(outTLMSPathID).exists) {
      Path(outTLMSPathID).createDirectory(failIfExists = false)
    }
    val File_Path = outTLMSPathID + id + "_" + n + ".tlms"
    val file = scala.io.Source.fromFile(File_Path)

    for (line <- file.getLines()) {
      if (line.startsWith("right.eye.pupil.center")) {
        val pattern = new Regex("\\d+[.]\\d+")
        val right_eye_result = pattern.findAllIn(line).toIndexedSeq
        if (right_eye_result.length == 2) {
          positions(0) = Math.floor(right_eye_result(0).toDouble).toInt
          positions(1) = Math.floor(right_eye_result(1).toDouble).toInt
        }
      }
      if (line.startsWith("left.eye.pupil.center")) {
        val pattern = new Regex("\\d+[.]\\d+")
        val left_eye_result = pattern.findAllIn(line).toIndexedSeq
        if (left_eye_result.length == 2) {
          positions(2) = Math.floor(left_eye_result(0).toDouble).toInt
          positions(3) = Math.floor(left_eye_result(1).toDouble).toInt
        }
      }
    }
    file.close()


    return positions
  }

  def drawGlasses(img: PixelImage[RGBA], mask: PixelImage[RGBA], eyeCenters: Array[Int]) = {
    val return_Image = PixelImage(img.width, img.height, (x, y) => {
      if (Math.pow((x-eyeCenters(0)),2)+Math.pow((y-eyeCenters(1)),2) < Math.pow(20,2) || Math.pow((x-eyeCenters(2)),2)+Math.pow((y-eyeCenters(3)),2) < Math.pow(20,2)) {
        RGBA.Black
      } else {
        img(x, y)
      }
    })
    val return_Mask = PixelImage(mask.width, mask.height, (x, y) => {
      if (Math.pow((x-eyeCenters(0)),2)+Math.pow((y-eyeCenters(1)),2) < Math.pow(20,2) || Math.pow((x-eyeCenters(2)),2)+Math.pow((y-eyeCenters(3)),2) < Math.pow(20,2)) {
        RGBA.Black // Change the color of the occlusion on the GROTRU mask
      } else {
        mask(x, y)
      }
    })
    (return_Image, return_Mask)
  }

  def writeRenderParametersAndLandmarks(rps: RenderParameter, id: Int, n: Int, mask:PixelImage[RGBA]): Unit = {
    val outRpsPathID= outRpsPath + id + "/"
    if (!Path(outRpsPathID).exists) {
      Path(outRpsPathID).createDirectory(failIfExists = false)
    }
    val outCSVPathID= outCSVPath + id + "/"
    if (!Path(outCSVPathID).exists) {
      Path(outCSVPathID).createDirectory(failIfExists = false)
    }
    val outTLMSPathID= outTLMSPath + id + "/"
    if (!Path(outTLMSPathID).exists) {
      Path(outTLMSPathID).createDirectory(failIfExists = false)
    }

    RenderParameterIO.write(rps, new File(outRpsPathID + id + "_" + n + ".rps"))
    writeCSV(rps, new File(outCSVPathID + id + "_" + n + ".csv") )
    if ( !cfg.landmarkTags.isEmpty && cfg.occlusion.renderOcclusion ) {
      writeLandmarks(rps, new File(outTLMSPathID + id + "_" + n + ".tlms"), mask)
    }
  }

  def writeTLMSLandmarks(landmarks: IndexedSeq[TLMSLandmark2D], file: File, mask:PixelImage[RGBA]): Try[Unit] = Try {
    ResourceManagement.usingTry(Try(new FileOutputStream(file)))(stream => write2DToStream_with_visibilityCheck(landmarks, stream, mask))
  }

  /** write TLMS 2D landmarks format to a stream (format: "id visible x y") */
  def write2DToStream_with_visibilityCheck(landmarks: IndexedSeq[TLMSLandmark2D], stream: OutputStream, mask:PixelImage[RGBA]): Try[Unit] = Try {
    ResourceManagement.using(new PrintWriter(stream), (wr: PrintWriter) => wr.flush()) { writer =>
      landmarks.foreach { lm =>
        var visible = if (lm.visible) "1" else "0"
        if (visible == "1" && mask(lm.point.x.toInt, lm.point.y.toInt) != RGBA.White) {
          visible = "0"
        }
        val line = "%s %s %.17g %.17g".formatLocal(java.util.Locale.US, lm.id, visible, lm.point.x, lm.point.y)
        writer.println(line)
      }
    }
  }
}
