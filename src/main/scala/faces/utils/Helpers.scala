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
import com.sun.xml.internal.bind.v2.TODO
import faces.apps.RandomFaces.{helpers, rnd}
import faces.settings.FacesSettings
import scalismo.faces.color.RGBA
import scalismo.faces.image.PixelImage
import scalismo.faces.io.{MoMoIO, PixelImageIO, RenderParameterIO, TLMSLandmarksIO}
import scalismo.faces.landmarks.TLMSLandmark2D
import scalismo.faces.mesh.BinaryMask
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters._
import scalismo.faces.sampling.face.ModalityRenderers.{AlbedoRenderer, IlluminationVisualizationRenderer}
import scalismo.faces.sampling.face.{CorrespondenceColorImageRenderer, CorrespondenceMoMoRenderer, MoMoRenderer}
import scalismo.faces.utils.ResourceManagement
import scalismo.geometry.{Point, Point2D, Vector2D, Vector3D}
import scalismo.utils.Random

import scala.util.matching.Regex
import scala.io.Source
import scala.reflect.ClassTag
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
  if (!Path(outImgPath_bfm).exists) {
    Path(outImgPath_bfm).createDirectory(failIfExists = false)
  }
  if (!Path(outOccPath_bfm).exists) {
    Path(outOccPath_bfm).createDirectory(failIfExists = false)
  }
  if (!Path(outMaskPath_bfm).exists) {
    Path(outMaskPath_bfm).createDirectory(failIfExists = false)
  }
  if (!Path(outRpsPath_bfm).exists) {
    Path(outRpsPath_bfm).createDirectory(failIfExists = false)
  }
  if (!Path(outCSVPath_bfm).exists) {
    Path(outCSVPath_bfm).createDirectory(failIfExists = false)
  }
  if (!Path(outTLMSPath_bfm).exists) {
    Path(outTLMSPath_bfm).createDirectory(failIfExists = false)
  }

  if (!Path(outImgPath_face12).exists) {
    Path(outImgPath_face12).createDirectory(failIfExists = false)
  }
  if (!Path(outOccPath_face12).exists) {
    Path(outOccPath_face12).createDirectory(failIfExists = false)
  }
  if (!Path(outMaskPath_face12).exists) {
    Path(outMaskPath_face12).createDirectory(failIfExists = false)
  }
  if (!Path(outRpsPath_face12).exists) {
    Path(outRpsPath_face12).createDirectory(failIfExists = false)
  }
  if (!Path(outCSVPath_face12).exists) {
    Path(outCSVPath_face12).createDirectory(failIfExists = false)
  }
  if (!Path(outTLMSPath_face12).exists) {
    Path(outTLMSPath_face12).createDirectory(failIfExists = false)
  }

  if (!Path(inOcclusonsPath).exists) {
    Path(inOcclusonsPath).createDirectory(failIfExists = false)
  }
  if (!Path(inBackgroundsPath).exists) {
    Path(inBackgroundsPath).createDirectory(failIfExists = false)
  }
  if (!Path(inTexturesPath).exists) {
    Path(inTexturesPath).createDirectory(failIfExists = false)
  }

  // loading model and choosing neutral part if expressions not used
  val model_face12: MoMo = {
    val momo = MoMoIO.read(new File(modelFn)).get
    if (expressions) momo else momo.neutralModel
  }

  val model_bfm: MoMo = {
    val momo = MoMoIO.read(new File("data/bfm2017/model2017-1_bfm_nomouth.h5")).get
    if (expressions) momo else momo.neutralModel
  }

  // the renderer for the model
  val face12_renderer = CorrespondenceMoMoRenderer(model_face12, RGBA.BlackTransparent).cached(5)
  val bfm_renderer = CorrespondenceMoMoRenderer(model_bfm, RGBA.BlackTransparent).cached(5)

  val face12_renderingMethods = {
    val depthMapGenerator_face12 = new DepthMapRendererRGBA(face12_renderer)
    val colorCorrespondenceGen_face12 = new CorrespondenceColorImageRenderer(face12_renderer)
    val normalsGenerator_face12 = new NormalsRendererRGBA(face12_renderer, RGBA.BlackTransparent)
    val albedoGenerator_face12 = new AlbedoRenderer(face12_renderer, RGBA.BlackTransparent)
    val illuminationGenerator_face12 = new IlluminationVisualizationRenderer(face12_renderer, RGBA.BlackTransparent)
    //additional rendering methods
    val dm_face12 = if (cfg.renderingMethods.renderDepthMap) {
      Some(("face12_depth", depthMapGenerator_face12))
    } else None
    val cm_face12 = if (cfg.renderingMethods.renderColorCorrespondenceImage) {
      Some(("face12_correspondence", colorCorrespondenceGen_face12))
    } else None
    val nm_face12 = if (cfg.renderingMethods.renderNormals) {
      Some(("face12_normals", normalsGenerator_face12))
    } else None
    val am_face12 = if (cfg.renderingMethods.renderAlbedo) {
      Some(("face12_albedo", albedoGenerator_face12))
    } else None
    val im_face12 = if (cfg.renderingMethods.renderIllumination) {
      Some(("face12_illumination", illuminationGenerator_face12))
    } else None
    val rn_face12 = if (cfg.renderingMethods.render) {
      Some(("face12", face12_renderer))
    } else None

    IndexedSeq(rn_face12, dm_face12, cm_face12, nm_face12, am_face12, im_face12).flatten
  }
  val bfm_renderingMethods = {
    val depthMapGenerator_bfm = new DepthMapRendererRGBA(bfm_renderer)
    val colorCorrespondenceGen_bfm = new CorrespondenceColorImageRenderer(bfm_renderer)
    val normalsGenerator_bfm = new NormalsRendererRGBA(bfm_renderer, RGBA.BlackTransparent)
    val albedoGenerator_bfm = new AlbedoRenderer(bfm_renderer, RGBA.BlackTransparent)
    val illuminationGenerator_bfm = new IlluminationVisualizationRenderer(bfm_renderer, RGBA.BlackTransparent)
    //additional rendering methods
    val dm_bfm = if (cfg.renderingMethods.renderDepthMap) {
      Some(("bfm_depth", depthMapGenerator_bfm))
    } else None
    val cm_bfm = if (cfg.renderingMethods.renderColorCorrespondenceImage) {
      Some(("bfm_correspondence", colorCorrespondenceGen_bfm))
    } else None
    val nm_bfm = if (cfg.renderingMethods.renderNormals) {
      Some(("bfm_normals", normalsGenerator_bfm))
    } else None
    val am_bfm = if (cfg.renderingMethods.renderAlbedo) {
      Some(("bfm_albedo", albedoGenerator_bfm))
    } else None
    val im_bfm = if (cfg.renderingMethods.renderIllumination) {
      Some(("bfm_illumination", illuminationGenerator_bfm))
    } else None
    val rn_bfm = if (cfg.renderingMethods.render) {
      Some(("bfm", bfm_renderer))
    } else None

    IndexedSeq(rn_bfm, dm_bfm, cm_bfm, nm_bfm, am_bfm, im_bfm).flatten
  }

  def convertFace12ToBfm(path:(String,Int)):PixelImage[RGBA] ={
    val face12 = MoMoIO.read(new File("data/bfm2017/model2017-1_face12_nomouth.h5")).get
    val bfm = MoMoIO.read(new File("data/bfm2017/model2017-1_bfm_nomouth.h5")).get

    val rendererBFM = MoMoRenderer(bfm, RGBA.BlackTransparent).cached(5)
    val rendererFace12 = MoMoRenderer(face12, RGBA.BlackTransparent).cached(5)

    val params: RenderParameter = RenderParameterIO.read(new File(s"data/output/face12/rps/${path._2}/" + path._1.replace(".png",".rps"))).get


    val mesh = face12.instance(params.momo.coefficients)
    val mask = BinaryMask.createFromMeshes(bfm.referenceMesh,face12.referenceMesh)
    val bfmPoints = bfm.referenceMesh.pointSet.points.toIndexedSeq
    val pointIds = bfmPoints.zip(mask.entries).filter(_._2).map(p => bfm.referenceMesh.pointSet.findClosestPoint(p._1).id)
    val posteriorShape = bfm.neutralModel.shape.posterior(pointIds.zip(mesh.shape.pointSet.points.toIndexedSeq),1.0e-5)
    val shapeParamsBFM = bfm.neutralModel.shape.coefficients(posteriorShape.mean)
    val posteriorColor = bfm.neutralModel.color.posterior(pointIds.zip(mesh.color.pointData.map(_.toRGB)),1.0e-5)
    val colorParamsBFM = bfm.neutralModel.color.coefficients(posteriorColor.mean)
    //val renderingFace12 = MoMoRenderer(face12).renderImage(params)
    //PixelImageIO.write(renderingFace12, new File("temp/0_0_face12.png"))
    val newMomo = MoMoInstance(shapeParamsBFM.toArray.toIndexedSeq,colorParamsBFM.toArray.toIndexedSeq,IndexedSeq(0),new File("").toURI)
    val renderingBFM = MoMoRenderer(bfm).renderImage(params.copy(momo=newMomo))
    //PixelImageIO.write(renderingBFM, new File("temp/0_0_bfm.png"))
    renderingBFM
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

  lazy val loadTexture: IndexedSeq[File] = {
    // search all texture files
    new File(inTexturesPath).listFiles.filter(_.getName.endsWith(".png")).toIndexedSeq
  }

  def writeLandmarks_face12(rps: RenderParameter, file: File, mask:PixelImage[RGBA]): Try[Unit] = {
    require(!cfg.landmarkTags.isEmpty, "Landmark tags list can not be empty when writing landmarks to a file.")
    val lms = visibilityForLandmarks(face12_renderer, rps, cfg.landmarkTags.map(tag => face12_renderer.renderLandmark(tag, rps).get).toIndexedSeq)
    //TLMSLandmarksIO.write2D(lms, file)
    write2D_arneli00(lms, file, mask)
  }

  def writeLandmarks_face12_initial(rps: RenderParameter, file: File, mask:PixelImage[RGBA]): Try[Unit] = {
    require(!cfg.landmarkTags.isEmpty,"Landmark tags list can not be empty when writing landmarks to a file.")
    val lmsInklCenters = cfg.landmarkTags ++ Seq("left.eye.pupil.center", "right.eye.pupil.center")
    val lms = visibilityForLandmarks(face12_renderer, rps, lmsInklCenters.map(tag => face12_renderer.renderLandmark(tag, rps).get).toIndexedSeq)
    //TLMSLandmarksIO.write2D(lms, file)
    write2D_arneli00(lms, file, mask)
  }

  def writeLandmarks_bfm(rps: RenderParameter, file: File, mask:PixelImage[RGBA]): Try[Unit] = {
    require(!cfg.landmarkTags.isEmpty, "Landmark tags list can not be empty when writing landmarks to a file.")
    val lms = visibilityForLandmarks(bfm_renderer, rps, cfg.landmarkTags.map(tag => bfm_renderer.renderLandmark(tag, rps).get).toIndexedSeq)
    //TLMSLandmarksIO.write2D(lms, file)
    write2D_arneli00(lms, file, mask)
  }

  // center the face around a point between the ears and nose (change here if you want to center another point)
  def centerLandmark_face12(rps: RenderParameter): RenderParameter = {
    val lmLeftEar: TLMSLandmark2D = face12_renderer.renderLandmark("left.eye.pupil.center", rps).get
    val lmRightEar: TLMSLandmark2D = face12_renderer.renderLandmark("right.eye.pupil.center", rps).get
    val lmNose: TLMSLandmark2D = face12_renderer.renderLandmark("center.nose.tip", rps).get


    val middleOfFace = (lmLeftEar.point + lmRightEar.point.toVector + lmNose.point.toVector).toVector / 3
    val centerOfImage = Point2D(imageWidth / 2, imageHeight / 2)
    val shift = centerOfImage - middleOfFace

    // shifting by moving the principal point (normalized device coordinates [-1, 1], y axis upwards)
    rps.copy(camera = rps.camera.copy(principalPoint = Point2D(
      rps.camera.principalPoint.x + 2 * shift.x / rps.imageSize.width,
      rps.camera.principalPoint.y - 2 * shift.y / rps.imageSize.height))
    )
  }

  def centerLandmark_bfm(rps: RenderParameter): RenderParameter = {
    val lmLeftEar: TLMSLandmark2D = bfm_renderer.renderLandmark("left.eye.pupil.center", rps).get
    val lmRightEar: TLMSLandmark2D = bfm_renderer.renderLandmark("right.eye.pupil.center", rps).get
    val lmNose: TLMSLandmark2D = bfm_renderer.renderLandmark("center.nose.tip", rps).get


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
      if (x >= 0 && x < w && y >= 0 && y < h) { // within image bounds
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
      } else {
        false
      }
    }


    for (lm <- landmarks) yield {
      lm.copy(visible = visibilityForLandmark(lm))
    }
  }

  /*Find the 3D center point between the left and the right nosewing and transform it to 2D*/

  private def findCenterWithNoseWings(rps: RenderParameter): Option[Point2D] = {

    for {
      leftNoseWingId <- model_face12.landmarkPointId("left.nose.wing.tip")
      rightNoseWingId <- model_face12.landmarkPointId("right.nose.wing.tip")
      leftNoseWing3D <- Some(model_face12.instanceAtPoint(rps.momo.coefficients, leftNoseWingId)._1)
      rightNoseWing3D = Some(model_face12.instanceAtPoint(rps.momo.coefficients, rightNoseWingId)._1)
      centerPoint3D <- Some(leftNoseWing3D + (rightNoseWing3D.get - leftNoseWing3D) * 0.5)
      pt <- Some(rps.renderTransform(centerPoint3D))
    } yield Point(pt.x, pt.y)

  }

  // center the face around chin, eyes, nose and ears and crop a face box (change here if you want to center another point)
  def centerFaceBox(rps: RenderParameter, scaling: Double = 1): RenderParameter = {

    val lmTags = Seq(
      //"left.ear.helix.outer",
      //"right.ear.helix.outer",
      "center.nose.tip",
      "center.front.trichion",
      "center.chin.tip",
      "left.eyebrow.bend.lower",
      "right.eyebrow.bend.lower"
    )
    // The face12 doesn't know the outcommented landmarks
    val lms = lmTags.map(tag => tag -> face12_renderer.renderLandmark(tag, rps).get).toMap


    val xmax = lms.values.map(_.point.x).max
    val xmin = lms.values.map(_.point.x).min

    val ymax = lms.values.map(_.point.y).max
    val ymin = lms.values.map(_.point.y).min

    val maxSide = math.max(xmax - xmin, ymax - ymin) * scaling

    val middleOfFace = findCenterWithNoseWings(rps).get

    val centerOfImage = Point2D(imageWidth / 2, imageHeight / 2)

    val shift = middleOfFace - centerOfImage

    val f = maxSide.toDouble / rps.imageSize.width.toDouble
    val g = maxSide.toDouble / rps.imageSize.height.toDouble

    // shifting by moving the principal point (normalized device coordinates [-1, 1], y axis upwards)
    rps.copy(camera = rps.camera.copy(principalPoint = Point2D(
      (rps.camera.principalPoint.x - 2 * shift.x / rps.imageSize.width) / f,
      (rps.camera.principalPoint.y + 2 * shift.y / rps.imageSize.height) / g),
      sensorSize = Vector2D(rps.camera.sensorSize.x * f, rps.camera.sensorSize.y * g))
    )

  }

  // center the face around chin, eyes, nose and ears and crop a face box (change here if you want to center another point)
  def centerFaceBox_bfm(rps: RenderParameter, scaling: Double = 1): RenderParameter = {

    val lmTags = Seq(
      "left.ear.helix.outer",
      "right.ear.helix.outer",
      "center.nose.tip",
      "center.front.trichion",
      "center.chin.tip",
      "left.eyebrow.bend.lower",
      "right.eyebrow.bend.lower"
    )

    val lms = lmTags.map(tag => tag -> bfm_renderer.renderLandmark(tag, rps).get).toMap


    val xmax = lms.values.map(_.point.x).max
    val xmin = lms.values.map(_.point.x).min

    val ymax = lms.values.map(_.point.y).max
    val ymin = lms.values.map(_.point.y).min

    val maxSide = math.max(xmax - xmin, ymax - ymin) * scaling

    val middleOfFace = findCenterWithNoseWings(rps).get

    val centerOfImage = Point2D(imageWidth / 2, imageHeight / 2)

    val shift = middleOfFace - centerOfImage

    val f = maxSide.toDouble / rps.imageSize.width.toDouble
    val g = maxSide.toDouble / rps.imageSize.height.toDouble

    // shifting by moving the principal point (normalized device coordinates [-1, 1], y axis upwards)
    rps.copy(camera = rps.camera.copy(principalPoint = Point2D(
      (rps.camera.principalPoint.x - 2 * shift.x / rps.imageSize.width) / f,
      (rps.camera.principalPoint.y + 2 * shift.y / rps.imageSize.height) / g),
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

  def getEyeCenters(id: Int, n: Int): Array[Int] = {
    val asdf = new Array[Int](4)
    val outTLMSPathID = outTLMSPath_face12 + id + "/"
    if (!Path(outTLMSPathID).exists) {
      Path(outTLMSPathID).createDirectory(failIfExists = false)
    }
    val File_Path = outTLMSPathID + id + "_" + n + ".tlms"
    val f = scala.io.Source.fromFile(File_Path)

    for (line <- f.getLines()) {
      if (line.startsWith("right.eye.pupil.center")) {
        val pattern = new Regex("\\d+[.]\\d+")
        val right_eye_result = pattern.findAllIn(line).toIndexedSeq
        if (right_eye_result.length == 2) {
          asdf(0) = Math.floor(right_eye_result(0).toDouble).toInt
          asdf(1) = Math.floor(right_eye_result(1).toDouble).toInt
        }
      }
      if (line.startsWith("left.eye.pupil.center")) {
        val pattern = new Regex("\\d+[.]\\d+")
        val left_eye_result = pattern.findAllIn(line).toIndexedSeq
        if (left_eye_result.length == 2) {
          asdf(2) = Math.floor(left_eye_result(0).toDouble).toInt
          asdf(3) = Math.floor(left_eye_result(1).toDouble).toInt
        }
      }
    }
    f.close()


    return asdf
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
          RGBA.apply(0, 0, 0, 0) // Change the color of the occlusion on the GROTRU mask
        else
          mask(x, y)
      }
      else
        mask(x, y)
    })
    (return_Image, return_Mask)
  }

  def gaussian_white_noise(image: PixelImage[RGBA], mask: PixelImage[RGBA], whiteNoiseOnly: Boolean, positions: (Int, Int, Int, Int)*): (PixelImage[RGBA], PixelImage[RGBA]) = {
    val r = new java.util.Random()
    var from_x = r.nextInt(image.width - 50)
    var from_y = r.nextInt(image.height - 50)
    var box_width = Math.min(image.width - from_x - 10, r.nextInt(image.width / 3))
    var box_height = Math.min(image.height - from_y - 10, r.nextInt(image.height / 3))
    if (positions.length > 0) {
      from_x = positions(0)._1
      from_y = positions(0)._2
      box_width = positions(0)._3
      box_height = positions(0)._4
    }
    val color = RGBA.apply(r.nextDouble(), r.nextDouble(), r.nextDouble())
    var whiteNoise = whiteNoiseOnly

    if (!whiteNoiseOnly)
      whiteNoise = r.nextBoolean()


    val return_Image = PixelImage(image.width, image.height, (x, y) => {
      if (x >= from_x && x < from_x + box_width && y >= from_y && y < from_y + box_height) {
        if (whiteNoise)
          RGBA.apply(Math.abs(r.nextGaussian()), Math.abs(r.nextGaussian()), Math.abs(r.nextGaussian()))
        else
          color
      } else {
        image(x, y)
      }
    })
    val return_Mask = PixelImage(mask.width, mask.height, (x, y) => {
      if (x >= from_x && x < from_x + box_width && y >= from_y && y < from_y + box_height)
        RGBA.apply(1, 0, 0, 0)
      else
        mask(x, y)
    })
    (return_Image, return_Mask)
  }

  // (start_x,start_y,width,height)
  def fillBox(img: PixelImage[RGBA], mask: PixelImage[RGBA], positions: (Int, Int, Int, Int), color_input: RGBA*): (PixelImage[RGBA], PixelImage[RGBA], RGBA) = {
    val r = new java.util.Random()
    var color: RGBA = null

    if (color_input.size != 0)
      color = color_input(0)
    else
      color = RGBA.apply(r.nextDouble(), r.nextDouble(), r.nextDouble())


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

  // These are the backup variables which keep track of what 'face12' did, so 'bfm' can render the same occlusion
  // The next three variables are for the modes 'random', 'random-1' and 'random-2'
  var backup_random_magic = (false,false,false,"asdf",1)
  var backup_random_resample = 1.0
  var backup_random_pos = (1,1)
  // The next two are for occlusionMode 'box'
  var backup_box_dimensions = (1,1,1,1)
  var backup_box_color = RGBA.Black
  // The two are for occlusionMode 'texture'
  var backup_texture_dims = (1,1,1,1)
  var backup_texture_name:File = null
  // The next is for occlusionMode 'eyes'
  var backup_eyes_positions = Array[Int](1,2,3,4)

  def writeImg_bfm(img: PixelImage[RGBA], id: Int, n: Int, postfix: String, landmarkTags: IndexedSeq[String], Mask: PixelImage[RGBA], bypri: (Int, Int, Int, Int, Int)*):Unit = {
    val outImgPathID = outImgPath_bfm + id + "/"
    if (!Path(outImgPathID).exists) {
      Path(outImgPathID).createDirectory(failIfExists = false)
    }
    val outOccPathID = outOccPath_bfm + id + "/"
    if (!Path(outOccPathID).exists) {
      Path(outOccPathID).createDirectory(failIfExists = false)
    }
    val outMaskPathID = outMaskPath_bfm + id + "/"
    if (!Path(outMaskPathID).exists) {
      Path(outMaskPathID).createDirectory(failIfExists = false)
    }
    if (postfix == "bfm" || postfix == "face12") { // The Output is the image (not a helper (depth, correspondence, ...))
      var direction = ""
      if (bypri.length > 0) {
        direction = "$" + bypri(0)._2 + "_" + bypri(0)._3 + "_" + bypri(0)._4
        // $_<yaw>_<pitch>_<roll>
      }

      // New 'occlusionMode'
      if (cfg.occlusionMode.equals("eyes")) {
        val img_array = img.toArray
        val eyeCenters = backup_eyes_positions
        val (new_image, new_mask) = drawGlasses(img, Mask, eyeCenters)
        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
      }

      // New 'occlusionMode'
      else if (cfg.occlusionMode.startsWith("random-")) {
        val numPattern = "[0-9]+".r
        val match1 = numPattern.findFirstIn(cfg.occlusionMode)
        val r = scala.util.Random
        val magic = backup_random_magic
        val occlusion_File_path = inOcclusonsPath + magic._4
        var occlusion_image = flipImage(PixelImageIO.read[RGBA](new File(occlusion_File_path)).get, (magic._1, magic._2, magic._3))
        val resize = backup_random_resample
        occlusion_image = occlusion_image.resampleNearestNeighbour((resize * occlusion_image.width).toInt, (resize * occlusion_image.height).toInt)
        val (new_image, new_mask) = writeOcclusionToImages(img, Mask, occlusion_image,backup_random_pos)
        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
      }

      // New 'occlusionMode'
      else if (cfg.occlusionMode.equals("box-whiteNoise")) {
        val (new_image, new_mask) = gaussian_white_noise(img, Mask, true)
        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
      }

      // New 'occlusionMode'
      else if (cfg.occlusionMode.equals("box-skincolor")) {
        val color_skin: RGBA = get_color_at(img, "center.chin.tip", id, n)
        val rand = scala.util.Random
        val (new_image, new_mask, color) = fillBox(img, Mask, getBoxDimensions(Mask, rand.nextInt(30) + 20), color_skin + RGBA.apply(color_skin.r + rand.nextInt(10) / 100, color_skin.g + rand.nextInt(10) / 100, color_skin.b + rand.nextInt(10) / 100))
        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
      }

      // New 'occlusionMode'
      else if (cfg.occlusionMode.startsWith("box")) {
        val r = scala.util.Random
        var percentage = r.nextInt(40) + 10 // You can choose this number
        if (cfg.occlusionMode.startsWith("box-")) {
          val numPattern = "[0-9]+".r
          val match1 = numPattern.findFirstIn(cfg.occlusionMode)
          percentage = match1.get.toInt
          direction += "_OccVal_" + percentage
        }
        val test = backup_box_dimensions
        val (new_image, new_mask,color) = fillBox(img, Mask, test, backup_box_color)
        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))

      }

      // New 'occlusionMode'
      else if (cfg.occlusionMode.equals("loop")) {
        for (percentage <- 2 to 40 by 2) {
          val direction_now = direction + "_occVal_" + percentage
          val test = getBoxDimensions(Mask, percentage)
          val (new_image, new_mask, color) = fillBox(img, Mask, test)
          PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction_now + ".png"))
          PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction_now + ".png"))
        }
      }

      // New 'occlusionMode'
      else if (cfg.occlusionMode.equals("texture")){
        val dims = backup_texture_dims
        val texture_name = backup_texture_name
        var texture_image = PixelImageIO.read[RGBA](texture_name).get.resample(imageWidth, imageHeight)
        texture_image = texture_image.resampleNearestNeighbour(Mask.width, Mask.height)
        val (new_image, new_mask) = fillBox_texture(img, Mask, dims, texture_image)

        PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
        PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))

      }
      else {
        PixelImageIO.write(Mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
      }
      PixelImageIO.write(img.map { f => f.toRGB }, new File(outImgPathID + id + "_" + n + direction + ".png"))
    }
    else {
      PixelImageIO.write(img.map { f => f.toRGB }, new File(outImgPathID + id + "_" + n + postfix + ".png"))
      PixelImageIO.write(Mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + postfix + ".png"))
    }
  }



    def writeImg_face12(img: PixelImage[RGBA], id: Int, n: Int, postfix: String, landmarkTags: IndexedSeq[String], Mask: PixelImage[RGBA], bypri: (Int, Int, Int, Int, Int)*): (PixelImage[RGBA],(String,Int)) = {
      val outImgPathID = outImgPath_face12 + id + "/"
      if (!Path(outImgPathID).exists) {
        Path(outImgPathID).createDirectory(failIfExists = false)
      }
      val outOccPathID = outOccPath_face12 + id + "/"
      if (!Path(outOccPathID).exists) {
        Path(outOccPathID).createDirectory(failIfExists = false)
      }
      val outMaskPathID = outMaskPath_face12 + id + "/"
      if (!Path(outMaskPathID).exists) {
        Path(outMaskPathID).createDirectory(failIfExists = false)
      }


      if (postfix == "bfm" || postfix == "face12") { // The Output is the image (not a helper (depth, correspondence, ...))
        var direction = ""
        if (bypri.length > 0) {
          direction = "$" + bypri(0)._2 + "_" + bypri(0)._3 + "_" + bypri(0)._4
          // $_<yaw>_<pitch>_<roll>
        }
        val new_mask = null
        val filename = id + "_" + n + direction + ".png"

        // New 'occlusionMode'
        if (cfg.occlusionMode.equals("eyes")) {
          val img_array = img.toArray
          val eyeCenters = getEyeCenters(id, n)
          backup_eyes_positions = eyeCenters
          val (new_image, new_mask) = drawGlasses(img, Mask, eyeCenters)
          PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
          PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
          PixelImageIO.write(img.map { f => f.toRGB }, new File(outImgPathID + id + "_" + n + postfix + ".png"))

          return (new_mask, (id + "_" + n + direction + ".png",id))
        }

        // New 'occlusionMode'
        else if (cfg.occlusionMode.startsWith("random-")) {
          val numPattern = "[0-9]+".r
          val match1 = numPattern.findFirstIn(cfg.occlusionMode)
          val r = scala.util.Random
          val magic = getRandImage(match1.get.toInt)
          backup_random_magic = magic
          val occlusion_File_path = inOcclusonsPath + magic._4
          var occlusion_image = flipImage(PixelImageIO.read[RGBA](new File(occlusion_File_path)).get, (magic._1, magic._2, magic._3))
          val resize = Math.min(0.8, r.nextDouble() + 0.2)
          backup_random_resample = resize
          occlusion_image = occlusion_image.resampleNearestNeighbour((resize * occlusion_image.width).toInt, (resize * occlusion_image.height).toInt)
          val position = (r.nextInt(img.width - occlusion_image.width),r.nextInt(img.width - occlusion_image.width))
          backup_random_pos = position
          val (new_image, new_mask) = writeOcclusionToImages(img, Mask, occlusion_image, position)
          PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
          PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
        }

        // New 'occlusionMode'
        else if (cfg.occlusionMode.equals("box-whiteNoise")) {
          val (new_image, new_mask) = gaussian_white_noise(img, Mask, true)
          PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
          PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
        }

        // New 'occlusionMode'
        else if (cfg.occlusionMode.equals("box-skincolor")) {
          val color_skin: RGBA = get_color_at(img, "center.chin.tip", id, n)
          val rand = scala.util.Random
          val (new_image, new_mask, color) = fillBox(img, Mask, getBoxDimensions(Mask, rand.nextInt(30) + 20), color_skin + RGBA.apply(color_skin.r + rand.nextInt(10) / 100, color_skin.g + rand.nextInt(10) / 100, color_skin.b + rand.nextInt(10) / 100))
          PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
          PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
        }

        // New 'occlusionMode'
        else if (cfg.occlusionMode.startsWith("box")) {
          val r = scala.util.Random
          var percentage = r.nextInt(40) + 10 // You can choose this number
          if (cfg.occlusionMode.startsWith("box-")) {
            val numPattern = "[0-9]+".r
            val match1 = numPattern.findFirstIn(cfg.occlusionMode)
            percentage = match1.get.toInt
            direction += "_OccVal_" + percentage
          }
          val test = getBoxDimensions(Mask, percentage)
          backup_box_dimensions = test
          val (new_image, new_mask, color) = fillBox(img, Mask, test)
          backup_box_color = color
          PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
          PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
        }

        // New 'occlusionMode'
        else if (cfg.occlusionMode.equals("loop")) {
          for (percentage <- 2 to 40 by 2) {
            val direction_now = direction + "_occVal_" + percentage
            val test = getBoxDimensions(Mask, percentage)
            val (new_image, new_mask, color) = fillBox(img, Mask, test)
            PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction_now + ".png"))
            PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction_now + ".png"))
          }
        }

        // New 'occlusionMode'
        else if (cfg.occlusionMode.equals("texture")){
          val r = scala.util.Random
          var percentage = r.nextInt(40) + 10 // You can choose this number
          val dims = getBoxDimensions(Mask, percentage)
          backup_texture_dims = dims

          val rndTexture = helpers.loadTexture(r.nextInt(helpers.loadTexture.length))
          val texture_image = PixelImageIO.read[RGBA](rndTexture).get.resample(imageWidth, imageHeight)

          backup_texture_name = rndTexture
          val (new_image, new_mask) = fillBox_texture(img, Mask, dims, texture_image)

          PixelImageIO.write(new_image.map { f => f.toRGB }, new File(outOccPathID + id + "_" + n + direction + ".png"))
          PixelImageIO.write(new_mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
        }
        else {
          PixelImageIO.write(Mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + direction + ".png"))
        }
        PixelImageIO.write(img.map { f => f.toRGB }, new File(outImgPathID + id + "_" + n + direction + ".png"))
        return (new_mask, (filename,id))
      }
      else {
        PixelImageIO.write(img.map { f => f.toRGB }, new File(outImgPathID + id + "_" + n + postfix + ".png"))
        PixelImageIO.write(Mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + postfix + ".png"))
      }
      return (Mask, ("return path to .png file",1))
    }

    def writeImg_controlled(img: PixelImage[RGBA], id: Int, n: Int, postfix: String, landmarkTags: IndexedSeq[String], Mask: PixelImage[RGBA], bypri: (Int, Int, Int, Int, Int)*): Unit = {
      val outImgPathID = outImgPath_face12 + id + "/"
      if (!Path(outImgPathID).exists) {
        Path(outImgPathID).createDirectory(failIfExists = false)
      }

      PixelImageIO.write(img.map { f => f.toRGB }, new File(outImgPathID + id + "_" + n + postfix + ".png"))


      if(postfix.equals("face12")){
        val outMaskPathID = outMaskPath_face12 + id + "/"
        if (!Path(outMaskPathID).exists) {
          Path(outMaskPathID).createDirectory(failIfExists = false)
        }

        PixelImageIO.write(Mask.map { f => f.toRGB }, new File(outMaskPathID + id + "_" + n + postfix + ".png"))
      }
    }

    def get_color_at(img: PixelImage[RGBA], LMtag: String, id: Int, n: Int): RGBA = {
      val outTLMSPathID = outTLMSPath_face12 + id + "/"
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
            //asdf(0) = Math.floor(right_eye_result(0).toDouble).toInt
            //asdf(1) = Math.floor(right_eye_result(1).toDouble).toInt
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
      val r = scala.util.Random
      while (!ok) {
        start_x = r.nextInt(cfg.imageDimensions.imageWidth - 2) + 1
        start_y = r.nextInt(cfg.imageDimensions.imageHeight - 2) + 1
        height = r.nextInt(cfg.imageDimensions.imageHeight - 1 - start_y)
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

    def getRandImage(onlyHands: Int): (Boolean, Boolean, Boolean, String, Int) = {
      val r = scala.util.Random
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
        val image_index = r.nextInt(index_hand_images.size)
        return (r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), A(index_hand_images(image_index)),image_index)
      }
      else if (onlyHands == 2) {
        val image_index = r.nextInt(index_microphone_images.size)
        return (r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), A(index_microphone_images(image_index)), image_index)
      }
      else {
        val image_index = r.nextInt(A.size)
        return (r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), A(image_index), image_index)
      }
    }

    def writeRenderParametersAndLandmarks_face12(rps: RenderParameter, id: Int, n: Int, mask:PixelImage[RGBA], initial:String*): Unit = {
      val outRpsPathID = outRpsPath_face12 + id + "/"
      if (!Path(outRpsPathID).exists) {
        Path(outRpsPathID).createDirectory(failIfExists = false)
      }
      val outCSVPathID = outCSVPath_face12 + id + "/"
      if (!Path(outCSVPathID).exists) {
        Path(outCSVPathID).createDirectory(failIfExists = false)
      }
      val outTLMSPathID = outTLMSPath_face12 + id + "/"
      if (!Path(outTLMSPathID).exists) {
        Path(outTLMSPathID).createDirectory(failIfExists = false)
      }

      RenderParameterIO.write(rps, new File(outRpsPathID + id + "_" + n + ".rps"))
      writeCSV(rps, new File(outCSVPathID + id + "_" + n + ".csv"))
      if (!cfg.landmarkTags.isEmpty) {
        if(initial.length == 0)
          writeLandmarks_face12(rps, new File(outTLMSPathID + id + "_" + n + ".tlms"), mask)
        else
          writeLandmarks_face12_initial(rps, new File(outTLMSPathID + id + "_" + n + ".tlms"), mask)
      }
    }

    def writeRenderParametersAndLandmarks_bfm(rps: RenderParameter, id: Int, n: Int, mask:PixelImage[RGBA]): Unit = {
      val outRpsPathID = outRpsPath_bfm + id + "/"
      if (!Path(outRpsPathID).exists) {
        Path(outRpsPathID).createDirectory(failIfExists = false)
      }
      val outCSVPathID = outCSVPath_bfm + id + "/"
      if (!Path(outCSVPathID).exists) {
        Path(outCSVPathID).createDirectory(failIfExists = false)
      }
      val outTLMSPathID = outTLMSPath_bfm + id + "/"
      if (!Path(outTLMSPathID).exists) {
        Path(outTLMSPathID).createDirectory(failIfExists = false)
      }

      RenderParameterIO.write(rps, new File(outRpsPathID + id + "_" + n + ".rps"))
      writeCSV(rps, new File(outCSVPathID + id + "_" + n + ".csv"))
      if (!cfg.landmarkTags.isEmpty) {
        writeLandmarks_bfm(rps, new File(outTLMSPathID + id + "_" + n + ".tlms"), mask)
      }
    }


//    def write_face12(img: PixelImage[RGBA], rps: RenderParameter, id: Int, n: Int, bypri: (Int, Int, Int, Int, Int)*): Unit = {
//      writeRenderParametersAndLandmarks_face12(rps, id, n)
//      writeImg_face12(img, id, n, "", null, null)
//    }

//    def write_bfm(img: PixelImage[RGBA], rps: RenderParameter, id: Int, n: Int, bypri: (Int, Int, Int, Int, Int)*): Unit = {
//      writeRenderParametersAndLandmarks_bfm(rps, id, n)
//      writeImg_bfm(img, id, n, "", null, null)
//    }

  def write2D_arneli00(landmarks: IndexedSeq[TLMSLandmark2D], file: File, mask:PixelImage[RGBA]): Try[Unit] = Try {
    ResourceManagement.usingTry(Try(new FileOutputStream(file)))(stream => write2DToStream_arneli00(landmarks, stream, mask))
  }

  /** write TLMS 2D landmarks format to a stream (format: "id visible x y") */
  def write2DToStream_arneli00(landmarks: IndexedSeq[TLMSLandmark2D], stream: OutputStream, mask:PixelImage[RGBA]): Try[Unit] = Try {
    ResourceManagement.using(new PrintWriter(stream), (wr: PrintWriter) => wr.flush()) { writer =>
      landmarks.foreach{lm =>
        var visible = if (lm.visible) "1" else "0"
        val colorasdf = mask(lm.point.x.toInt, lm.point.y.toInt)
        if(visible == "1" && mask(lm.point.x.toInt, lm.point.y.toInt) != RGBA.White){
          visible = "0"
        }
        val line = "%s %s %.17g %.17g".formatLocal(java.util.Locale.US, lm.id, visible, lm.point.x,lm.point.y)
        writer.println(line)
      }
    }
  }
}
