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

import java.io.File
import java.net.URI

import breeze.linalg.DenseVector
import faces.renderer.ColorMapRenderer
import faces.settings.FacesSettings
import scalismo.color.RGBA
import scalismo.faces.image.PixelImage
import scalismo.faces.io.{MoMoIO, PixelImageIO, RenderParameterIO, TLMSLandmarksIO}
import scalismo.faces.landmarks.TLMSLandmark2D
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters._
import scalismo.faces.sampling.face.ModalityRenderers.{AlbedoRenderer, IlluminationVisualizationRenderer}
import scalismo.faces.sampling.face.{CorrespondenceColorImageRenderer, CorrespondenceMoMoRenderer}
import scalismo.geometry.{EuclideanVector2D, EuclideanVector3D, Point, Point2D}
import scalismo.utils.Random

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
    } else None
    val cm = if(cfg.renderingMethods.renderColorCorrespondenceImage) {
      Some(("_correspondence", colorCorrespondenceGen))
    } else None
    val nm = if(cfg.renderingMethods.renderNormals) {
      Some(("_normals", normalsGenerator))
    } else None
    val am = if(cfg.renderingMethods.renderAlbedo) {
      Some(("_albedo", albedoGenerator))
    } else None
    val im = if(cfg.renderingMethods.renderIllumination) {
      Some(("_illumination", illuminationGenerator))
    } else None
    val rn = if(cfg.renderingMethods.render) {
      Some(("", renderer))
    } else None
    val rm = if(cfg.renderingMethods.renderRegionMaps) {
      cfg.regionMaps.map{ regm =>
        val regionMap = TextureMappedPropertyIO.read[RGBA](new File(regm.mapping), new File(regm.map))
        Some((s"_${regm.name}", new ColorMapRenderer(regionMap,renderer, RGBA.BlackTransparent)))
      }
    } else Seq(None)
    (IndexedSeq(rn, dm, cm, nm, am, im) ++ rm).flatten
  }

  // generates a random instance of a Morphable Model
  def rndMoMoInstance(implicit rnd: Random): MoMoInstance = {
    if (expressions)
      MoMoInstance(
        if (nShape == 0) IndexedSeq(0.0) else IndexedSeq.fill(nShape)(shapeDistribution()),
        if (nColor == 0) IndexedSeq(0.0) else IndexedSeq.fill(nColor)(colorDistribution()),
        if (nExpression == 0) IndexedSeq(0.0) else IndexedSeq.fill(nExpression)(expressionDistribution()),
        new URI(""))
    else
      MoMoInstance(
        if (nShape == 0) IndexedSeq(0.0) else IndexedSeq.fill(nShape)(shapeDistribution()),
        if (nColor == 0) IndexedSeq(0.0) else IndexedSeq.fill(nColor)(colorDistribution()),
        if (nExpression == 0) IndexedSeq(0.0) else IndexedSeq.fill(nExpression)(0.0),
        new URI(""))
  }


  // adds a random expression to a neutral morphable model instance following Gaussian Distribution
  def rndExpressions(id: MoMoInstance)(implicit rnd: Random): MoMoInstance = {
    id.copy(expression = IndexedSeq.fill(nExpression)(expressionDistribution()))
  }

  lazy val loadBgs: IndexedSeq[File] = {
    // search all background files
    new File(bgPath).listFiles.filter(_.getName.endsWith(bgType)).toIndexedSeq
  }

  def writeLandmarks(rps: RenderParameter, file: File): Try[Unit] = {
    require(!cfg.landmarkTags.isEmpty,"Landmark tags list can not be empty when writing landmarks to a file.")
    val lms = visibilityForLandmarks(renderer, rps, cfg.landmarkTags.map(tag => renderer.renderLandmark(tag, rps).get).toIndexedSeq)
    TLMSLandmarksIO.write2D(lms, file)
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
      sensorSize = EuclideanVector2D(rps.camera.sensorSize.x * f, rps.camera.sensorSize.y * g))
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
    val withPose = init.withPose(pose = init.pose.copy(yaw = vec(0), pitch = vec(1), roll = vec(2), translation = EuclideanVector3D(vec(3), vec(4), vec(5))))
    val withCam = withPose.withCamera(camera = init.camera.copy(principalPoint = Point2D(vec(6), vec(7)), focalLength = vec(8), sensorSize = EuclideanVector2D(vec(9), vec(10))))
    val withMoMo = withCam.withMoMo(momo = MoMoInstance(vec(11 until 11 + nShape).toArray.toIndexedSeq,
      vec(11 + nShape until 11 + nShape + nColor).toArray.toIndexedSeq,
      vec(11 + nShape + nColor until 11 + nShape + nColor + nExpression).toArray.toIndexedSeq,
      new URI("")))
    val withLight = withMoMo.withEnvironmentMap(SphericalHarmonicsLight.fromBreezeVector(vec(11 + nShape + nColor + nExpression until 11 + nShape + nColor + nExpression + 27)))
    withLight
  }

  def writeImg(img: PixelImage[RGBA], id: Int, n: Int, postfix: String): Unit = {
    val outImgPathID = outImgPath + id + "/"
    if (!Path(outImgPathID).exists) {
      Path(outImgPathID).createDirectory(failIfExists = false)
    }
    PixelImageIO.write(img.map { f => f.toRGB }, new File(outImgPathID + id + "_" + n + postfix + ".png"))
  }

  def writeRenderParametersAndLandmarks(rps: RenderParameter, id: Int, n: Int): Unit = {
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
    if ( !cfg.landmarkTags.isEmpty ) {
      writeLandmarks(rps, new File(outTLMSPathID + id + "_" + n + ".tlms"))
    }
  }

  def write(img: PixelImage[RGBA], rps: RenderParameter, id: Int, n: Int): Unit = {
    writeRenderParametersAndLandmarks(rps, id, n)
    writeImg(img, id, n, "")
  }


}
