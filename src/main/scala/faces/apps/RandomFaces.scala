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
package faces.apps

import java.io.File

import faces.settings.RandomFacesSettings
import faces.utils.{Helpers, ParametricFaceImageGeneratorOptions}
import scalismo.faces.color.RGBA
import scalismo.faces.io.PixelImageIO
import scalismo.faces.parameters._
import scalismo.geometry.Point2D
import scalismo.utils.Random

object RandomFaces extends App {

  scalismo.initialize()
  implicit val rnd: Random = Random(1986) // use random seed to reproduce your results

  //****************************************************************************
  // SETTINGS
  //****************************************************************************

  val opt = new ParametricFaceImageGeneratorOptions(args)
  opt.verify()

  val cfg = RandomFacesSettings.read(new File(opt.configurationFile()))
  import cfg.backgrounds._
  import cfg.defaultParameters._
  import cfg.illuminationParameters._
  import cfg.imageDimensions._
  import cfg.morphableModelParameters._
  import cfg.poseVariation._

  val helpers = Helpers(cfg)

  //****************************************************************************
  // RANDOM GENERATOR
  //****************************************************************************

  (0 until nIds).par.foreach( id =>{
    try {
      // generate random model instance (shape and color)
      val rndId = helpers.rndMoMoInstance
      for (n <- 0 until nSamples) {
        // add a random expression if activated
        val momoInstance = if (expressions) helpers.rndExpressions(rndId) else rndId

        // sample a random pose
        val rndYaw = yawDistribution()
        val rndPitch = pitchDistribution()
        val rndRoll = rollDistribution()
        val rndPose = Pose(
          pose.scaling,
          pose.translation,
          scala.math.toRadians(rndRoll),
          scala.math.toRadians(rndYaw),
          scala.math.toRadians(rndPitch))

        // random scaling of the face in the image
        val rndCamera = camera.copy(focalLength = camera.focalLength * scalingDistribution())

        // random illumination
        val rndIll = illuminationPrior.rnd(illumination)

        // put RenderParameters together of all its randomized parts
        val uncentered = RenderParameter(rndPose, view, rndCamera, rndIll, directionalLight, momoInstance, ImageSize(imageWidth, imageHeight), colorTransform)


        val centered = if (faceCenter == "facebox") {
          helpers.centerFaceBox(uncentered)
        }
        else if (faceCenter == "landmark") {
          helpers.centerLandmark_face12(uncentered)
        }
        else {
          uncentered
        }

        // add some controlled random translation
        val rps = centered.copy(camera = centered.camera.copy(
          principalPoint = Point2D(
            centered.camera.principalPoint.x + (2.0 * xTranslationDistribution()) / imageWidth,
            centered.camera.principalPoint.y + (2.0 * yTranslationDistribution()) / imageHeight)
        ))

        val imageData =
          for ((postfix, currentRenderer) <- helpers.face12_renderingMethods) yield {
            if (bg && (postfix == "face12" || postfix == "bfm")) { // only allow different backgrounds for standard renderings
              require(helpers.loadBgs.nonEmpty, "no Background files with type " + cfg.backgrounds.bgType + " found in " + cfg.backgrounds.bgPath)
              val rndBG = helpers.loadBgs(rnd.scalaRandom.nextInt(helpers.loadBgs.length))
              val rndBGimg = PixelImageIO.read[RGBA](rndBG).get.resample(imageWidth, imageHeight)
              val img = currentRenderer.renderImage(rps).zip(rndBGimg).map(p => if (p._1.a < 0.5) p._2 else p._1)
              val mask = currentRenderer.renderImage(centered).zip(rndBGimg).map(p => if (p._1.a < 0.5) RGBA.Black else RGBA.White)
              helpers.writeRenderParametersAndLandmarks_face12(rps, id, n, mask, "initial")
              val mask_with_occlusions_and_path = helpers.writeImg_face12(img, id, n, postfix, cfg.landmarkTags, mask)
              helpers.writeRenderParametersAndLandmarks_face12(rps, id, n, mask_with_occlusions_and_path._1)
              // here, we switch to the bfm
              val bfmFaceImage = helpers.convertFace12ToBfm(mask_with_occlusions_and_path._2)
              val bfmImage = bfmFaceImage.zip(rndBGimg).map(p => if (p._1.a < 0.5) p._2 else p._1)
              val bfmMask = bfmFaceImage.zip(rndBGimg).map(p => if (p._1.a < 0.5) RGBA.Black else RGBA.White)
              helpers.writeRenderParametersAndLandmarks_bfm(rps, id, n, bfmMask)
              helpers.writeImg_bfm(bfmImage, id, n, postfix, cfg.landmarkTags, bfmMask)
            }
            else {
              (currentRenderer.renderImage(rps), postfix, currentRenderer.renderImage(rps))
            }
          }

//        // write images and their parameters
//        println(s"Generating \t ID:$id \t Sample:$n")
//        for ((img, postifx, mask) <- imageData) {
//          if(postifx.startsWith("bfm")) {
//            helpers.writeRenderParametersAndLandmarks_bfm(rps, id, n)
//            helpers.writeImg_bfm(img, id, n, postifx, cfg.landmarkTags, mask)
//          }
//        }
//        println(s"Generating \t ID:$id \t Sample:$n for face12")

      }
    }
    catch{
      case e: Throwable =>
        println("Something went wrong with id: " + id)
        println(s"${e.getMessage}")
        println(s"${e.getStackTrace}")
        e.printStackTrace()
    }
  })

}